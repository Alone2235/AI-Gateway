package com.eta.aigateway.gateway

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.StreamEvent
import com.eta.aigateway.engine.AutoRouter
import com.eta.aigateway.engine.GatewayEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/** 处理 /v1/chat/completions */
internal class ChatHandler(
    private val gson: Gson,
    private val stateProvider: () -> GatewayServer.GatewayState
) {
    fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val st = stateProvider()
        val bodyText = readBody(session)

        val body = try { gson.fromJson(bodyText, JsonObject::class.java) }
        catch (_: Exception) { null } ?: return err(400, "invalid json body")

        val modelId = body.get("model")?.asString ?: return err(400, "missing model")
        val stream = body.get("stream")?.asBoolean ?: false
        val temp = body.get("temperature")?.asDouble ?: 0.7

        val msgs = mutableListOf<Pair<String, String>>()
        body.getAsJsonArray("messages")?.forEach { el ->
            val o = el.asJsonObject
            val role = o.get("role")?.asString ?: "user"
            val content = when {
                o.get("content")?.isJsonPrimitive == true -> o.get("content").asString
                o.get("content")?.isJsonArray == true -> {
                    // 多模态：只取文本部分
                    buildString {
                        o.getAsJsonArray("content").forEach { part ->
                            val po = part.asJsonObject
                            if (po.get("type")?.asString == "text") {
                                append(po.get("text")?.asString ?: "")
                            }
                        }
                    }
                }
                else -> ""
            }
            if (content.isNotBlank()) msgs.add(role to content)
        }

        if (msgs.isEmpty()) return err(400, "messages is empty")

        val enabled = st.groups.filter { it.enabled && it.enabledSources.isNotEmpty() }
        if (enabled.isEmpty()) {
            return err(400, "没有启用的模型，请在 App 内勾选模型")
        }
        val model: ModelGroup = when (modelId) {
            "auto", "" -> enabled.firstOrNull { !AutoRouter.isExhausted(it) } ?: enabled.first()
            else -> enabled.firstOrNull { it.modelId == modelId }
                ?: return err(404, "模型 $modelId 未启用或不存在")
        }

        val engine = GatewayEngine(st.providers, st.groups)

        return if (stream) streamResponse(engine, model, msgs, temp)
        else blockingResponse(engine, model, msgs, temp)
    }

    private fun blockingResponse(
        engine: GatewayEngine,
        model: ModelGroup,
        msgs: List<Pair<String, String>>,
        temp: Double
    ): NanoHTTPD.Response {
        val sb = StringBuilder()
        var errMsg: String? = null
        var doneModel = model.modelId
        runBlocking {
            engine.chat(model, msgs, temp, source = "api").collect { ev ->
                when (ev) {
                    is StreamEvent.Chunk -> sb.append(ev.text)
                    is StreamEvent.Done -> doneModel = ev.model
                    is StreamEvent.Failed -> errMsg = ev.reason
                    else -> {}
                }
            }
        }
        errMsg?.let { return err(502, it) }
        val resp = mapOf(
            "id" to "chatcmpl-${System.currentTimeMillis()}",
            "object" to "chat.completion",
            "created" to System.currentTimeMillis() / 1000,
            "model" to doneModel,
            "choices" to listOf(
                mapOf(
                    "index" to 0,
                    "message" to mapOf("role" to "assistant", "content" to sb.toString()),
                    "finish_reason" to "stop"
                )
            ),
            "usage" to mapOf(
                "prompt_tokens" to 0,
                "completion_tokens" to sb.length / 3,
                "total_tokens" to sb.length / 3
            )
        )
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            gson.toJson(resp)
        )
    }

    private fun streamResponse(
        engine: GatewayEngine,
        model: ModelGroup,
        msgs: List<Pair<String, String>>,
        temp: Double
    ): NanoHTTPD.Response {
        val id = "chatcmpl-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000

        val stream = object : java.io.InputStream() {
            private var buf: ByteArray = ByteArray(0)
            private var pos = 0
            private var finished = false
            private val collected = ArrayDeque<String>()
            private var collecting = true

            private fun fill(): Boolean {
                if (collecting) {
                    runBlocking {
                        engine.chat(model, msgs, temp, source = "api").collect { ev ->
                            when (ev) {
                                is StreamEvent.Chunk -> {
                                    val chunk = mapOf(
                                        "id" to id, "object" to "chat.completion.chunk",
                                        "created" to created, "model" to model.modelId,
                                        "choices" to listOf(mapOf(
                                            "index" to 0,
                                            "delta" to mapOf("content" to ev.text),
                                            "finish_reason" to null
                                        ))
                                    )
                                    collected.addLast("data: ${gson.toJson(chunk)}\n\n")
                                }
                                is StreamEvent.Done -> {
                                    val done = mapOf(
                                        "id" to id, "object" to "chat.completion.chunk",
                                        "created" to created, "model" to ev.model,
                                        "choices" to listOf(mapOf(
                                            "index" to 0, "delta" to emptyMap<String, Any>(),
                                            "finish_reason" to "stop"
                                        ))
                                    )
                                    collected.addLast("data: ${gson.toJson(done)}\n\n")
                                }
                                is StreamEvent.Failed -> {
                                    val e = mapOf("error" to mapOf("message" to ev.reason))
                                    collected.addLast("data: ${gson.toJson(e)}\n\n")
                                }
                                else -> {}
                            }
                        }
                    }
                    collected.addLast("data: [DONE]\n\n")
                    collecting = false
                }
                if (collected.isEmpty()) { finished = true; return false }
                buf = collected.removeFirst().toByteArray()
                pos = 0
                return true
            }

            override fun read(): Int {
                if (pos >= buf.size) { if (!fill()) return -1 }
                return buf[pos++].toInt() and 0xFF
            }
        }

        val resp = NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream",
            stream
        )
        resp.addHeader("Cache-Control", "no-cache")
        resp.addHeader("Connection", "keep-alive")
        resp.addHeader("X-Accel-Buffering", "no")
        return resp
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String {
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"] ?: ""
        } catch (_: Exception) { "" }
    }

    private fun err(code: Int, msg: String): NanoHTTPD.Response {
        val status = NanoHTTPD.Response.Status.lookup(code) ?: NanoHTTPD.Response.Status.BAD_REQUEST
        val body = mapOf("error" to mapOf("message" to msg, "type" to "invalid_request_error"))
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", gson.toJson(body))
    }
}
