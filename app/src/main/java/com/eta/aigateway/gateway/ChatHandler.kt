package com.eta.aigateway.gateway

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.StreamEvent
import com.eta.aigateway.data.model.VirtualTier
import com.eta.aigateway.engine.AutoRouter
import com.eta.aigateway.engine.GatewayEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

internal class ChatHandler(
    private val gson: Gson,
    private val stateProvider: () -> GatewayServer.GatewayState
) {
    private companion object {
        const val DONE_MARK = "\u0000__DONE__"
    }

    fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val st = stateProvider()
        val bodyText = readBody(session)
        val body = try { gson.fromJson(bodyText, JsonObject::class.java) }
        catch (_: Exception) { null } ?: return err(400, "invalid json body")

        val modelId = body.get("model")?.asString ?: "auto"
        val stream = body.get("stream")?.asBoolean ?: false
        val temp = body.get("temperature")?.asDouble ?: 0.7

        val msgs = parseMessages(body)
        if (msgs.isEmpty()) return err(400, "messages is empty")

        val enabled = st.groups.filter { it.enabled && it.enabledSources.isNotEmpty() }
        if (enabled.isEmpty()) return err(400, "没有启用的模型，请在 App 内勾选模型")

        val tier = VirtualTier.from(modelId)
        val isAuto = tier != null || modelId == "auto" || modelId.isBlank()
        val model: ModelGroup = when {
            isAuto -> enabled.first()
            else -> enabled.firstOrNull { it.modelId == modelId }
                ?: return err(404, "模型 $modelId 未启用")
        }

        val engine = GatewayEngine(st.providers, st.groups)
        return if (stream) streamResponse(engine, model, msgs, temp, isAuto, tier)
        else blockingResponse(engine, model, msgs, temp, isAuto, tier)
    }

    private fun parseMessages(body: JsonObject): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        body.getAsJsonArray("messages")?.forEach { el ->
            val o = el.asJsonObject
            val role = o.get("role")?.asString ?: "user"
            val content = when {
                o.get("content")?.isJsonPrimitive == true -> o.get("content").asString
                o.get("content")?.isJsonArray == true -> buildString {
                    o.getAsJsonArray("content").forEach { part ->
                        val po = part.asJsonObject
                        if (po.get("type")?.asString == "text") append(po.get("text")?.asString ?: "")
                    }
                }
                else -> ""
            }
            if (content.isNotBlank()) out.add(role to content)
        }
        return out
    }

    private fun blockingResponse(
        engine: GatewayEngine,
        model: ModelGroup,
        msgs: List<Pair<String, String>>,
        temp: Double,
        isAuto: Boolean,
        tier: VirtualTier?
    ): NanoHTTPD.Response {
        val sb = StringBuilder()
        var errMsg: String? = null
        var doneModel = model.modelId
        runBlocking {
            val flow = if (isAuto) engine.autoChat(msgs, temp, "api", tier = tier)
            else engine.chat(model, msgs, temp, source = "api")
            flow.collect { ev ->
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
            "choices" to listOf(mapOf(
                "index" to 0,
                "message" to mapOf("role" to "assistant", "content" to sb.toString()),
                "finish_reason" to "stop"
            )),
            "usage" to mapOf(
                "prompt_tokens" to 0,
                "completion_tokens" to sb.length / 3,
                "total_tokens" to sb.length / 3
            )
        )
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", gson.toJson(resp)
        )
    }

    /**
     * 真流式：生产者边生成边入队，读取端阻塞取。
     * 之前 runBlocking 一次性跑完整个流，客户端要等全部生成才收到数据。
     */
    private fun streamResponse(
        engine: GatewayEngine,
        model: ModelGroup,
        msgs: List<Pair<String, String>>,
        temp: Double,
        isAuto: Boolean,
        tier: VirtualTier?
    ): NanoHTTPD.Response {
        val id = "chatcmpl-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000
        val queue = LinkedBlockingQueue<String>(512)
        val jobRef = AtomicReference<Job?>(null)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobRef.set(scope.launch {
            fun push(s: String) { try { queue.put(s) } catch (_: Exception) {} }
            try {
                val flow = if (isAuto) engine.autoChat(msgs, temp, "api", tier = tier)
                else engine.chat(model, msgs, temp, source = "api")
                flow.collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> push("data: " + gson.toJson(mapOf(
                            "id" to id, "object" to "chat.completion.chunk",
                            "created" to created, "model" to model.modelId,
                            "choices" to listOf(mapOf(
                                "index" to 0,
                                "delta" to mapOf("content" to ev.text),
                                "finish_reason" to null
                            ))
                        )) + "\n\n")
                        is StreamEvent.Done -> push("data: " + gson.toJson(mapOf(
                            "id" to id, "object" to "chat.completion.chunk",
                            "created" to created, "model" to ev.model,
                            "choices" to listOf(mapOf(
                                "index" to 0, "delta" to emptyMap<String, Any>(),
                                "finish_reason" to "stop"
                            ))
                        )) + "\n\n")
                        is StreamEvent.Failed -> push("data: " +
                            gson.toJson(mapOf("error" to mapOf("message" to ev.reason))) + "\n\n")
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                push("data: " + gson.toJson(mapOf("error" to mapOf("message" to (e.message ?: "err")))) + "\n\n")
            } finally {
                push("data: [DONE]\n\n")
                push(DONE_MARK)
            }
        })

        val stream = object : java.io.InputStream() {
            private var buf: ByteArray = ByteArray(0)
            private var pos = 0
            private var eof = false

            override fun read(): Int {
                if (pos >= buf.size) {
                    if (eof) return -1
                    val line = try { queue.take() } catch (_: Exception) { DONE_MARK }
                    if (line == DONE_MARK) { eof = true; return -1 }
                    buf = line.toByteArray(Charsets.UTF_8)
                    pos = 0
                }
                return buf[pos++].toInt() and 0xFF
            }

            override fun close() {
                jobRef.get()?.cancel()
                super.close()
            }
        }

        val resp = NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK, "text/event-stream", stream
        )
        resp.addHeader("Cache-Control", "no-cache")
        resp.addHeader("Connection", "keep-alive")
        resp.addHeader("X-Accel-Buffering", "no")
        return resp
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String = try {
        val map = HashMap<String, String>()
        session.parseBody(map)
        map["postData"] ?: ""
    } catch (_: Exception) { "" }

    private fun err(code: Int, msg: String): NanoHTTPD.Response {
        val status = NanoHTTPD.Response.Status.lookup(code) ?: NanoHTTPD.Response.Status.BAD_REQUEST
        val body = mapOf("error" to mapOf("message" to msg, "type" to "invalid_request_error"))
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", gson.toJson(body))
    }
}
