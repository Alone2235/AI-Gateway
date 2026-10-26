package com.eta.aigateway.data.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.eta.aigateway.data.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object AnthropicProtocol : ChatProtocol {

    private const val VERSION = "2023-06-01"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun endpoint(baseUrl: String): String {
        val b = baseUrl.trimEnd('/')
        return if (b.endsWith("/messages")) b else "$b/messages"
    }

    override fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        history: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int
    ): Flow<StreamEvent> = callbackFlow {
        val body = buildBody(model, systemPrompt, history, temperature, maxTokens)
        val req = Request.Builder()
            .url(endpoint(baseUrl))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", VERSION)
            .addHeader("content-type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        val call = client.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(c: Call, e: IOException) {
                trySend(StreamEvent.Failed(e.message ?: "network error", 0))
                close()
            }

            override fun onResponse(c: Call, resp: Response) {
                resp.use { r ->
                    r.consume { ev -> trySend(ev) }
                    if (r.isSuccessful) trySend(StreamEvent.Done(model, "anthropic"))
                }
                close()
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun buildBody(
        model: String,
        systemPrompt: String?,
        history: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int
    ): JsonObject {
        return JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            addProperty("temperature", temperature)
            addProperty("max_tokens", if (maxTokens > 0) maxTokens else 4096)
            systemPrompt?.takeIf { it.isNotBlank() }?.let { addProperty("system", it) }

            val arr = JsonArray()
            var lastRole: String? = null
            val pending = StringBuilder()

            fun flush() {
                if (pending.isEmpty()) return
                val o = JsonObject()
                o.addProperty("role", lastRole ?: "user")
                o.addProperty("content", pending.toString())
                arr.add(o)
                pending.clear()
            }

            history.forEach { (role, content) ->
                val r = if (role == "assistant") "assistant" else "user"
                if (lastRole != null && lastRole != r) flush()
                if (pending.isNotEmpty()) pending.append("\n")
                pending.append(content)
                lastRole = r
            }
            flush()
            add("messages", arr)
        }
    }

    private fun okhttp3.Response.consume(emit: (StreamEvent) -> Unit) {
        if (!isSuccessful) {
            val err = try { body?.string()?.take(500) } catch (_: Exception) { null }
            emit(StreamEvent.Failed(reasonText(code, err), code))
            return
        }
        val b = body
        if (b == null) {
            emit(StreamEvent.Failed("empty body", code))
            return
        }
        try {
            val src = b.source()
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                try {
                    val json = gson.fromJson(payload, JsonObject::class.java)
                    when (json.get("type")?.asString) {
                        "content_block_delta" -> {
                            val d = json.getAsJsonObject("delta")
                            if (d.get("type")?.asString == "text_delta") {
                                val t = d.get("text")?.asString ?: ""
                                if (t.isNotEmpty()) emit(StreamEvent.Chunk(t))
                            }
                        }
                        "error" -> {
                            val e = json.getAsJsonObject("error")
                            emit(StreamEvent.Failed(
                                e?.get("message")?.asString ?: "stream error", 0
                            ))
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            emit(StreamEvent.Failed(e.message ?: "read error", 0))
        }
    }

    override suspend fun listModels(baseUrl: String, apiKey: String): List<String> {
        val b = baseUrl.trimEnd('/').removeSuffix("/messages").trimEnd('/')
        val req = Request.Builder()
            .url("$b/models?limit=100")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", VERSION)
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                val raw = r.body?.string() ?: return emptyList()
                if (!r.isSuccessful) return emptyList()
                val json = gson.fromJson(raw, JsonObject::class.java)
                val arr = json.getAsJsonArray("data") ?: return emptyList()
                arr.mapNotNull { it.asJsonObject.get("id")?.asString }.sorted()
            }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun ping(baseUrl: String, apiKey: String): Pair<Boolean, String> {
        val b = baseUrl.trimEnd('/').removeSuffix("/messages").trimEnd('/')
        val req = Request.Builder()
            .url("$b/models?limit=1")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", VERSION)
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) true to "连接成功"
                else false to reasonText(r.code, null)
            }
        } catch (e: Exception) {
            false to (e.message ?: "连接失败")
        }
    }

    private fun reasonText(code: Int, body: String?): String {
        val base = when (code) {
            400 -> "请求格式错误 (400)"
            401 -> "API Key 无效 (401)"
            403 -> "无权限 (403)"
            404 -> "模型不存在 (404)"
            413 -> "请求过长 (413)"
            429 -> "限流 (429)"
            500 -> "服务端错误 (500)"
            529 -> "过载 (529)"
            else -> "HTTP $code"
        }
        return if (body.isNullOrBlank()) base else "$base: ${body.take(160)}"
    }
}
