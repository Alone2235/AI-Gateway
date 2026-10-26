package com.eta.aigateway.data.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.eta.aigateway.data.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object OpenAIClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun buildBody(model: String, messages: List<Pair<String, String>>, stream: Boolean, temperature: Double): String {
        val obj = JsonObject()
        obj.addProperty("model", model)
        obj.addProperty("stream", stream)
        obj.addProperty("temperature", temperature)
        val arr = com.google.gson.JsonArray()
        messages.forEach { (role, content) ->
            val m = JsonObject()
            m.addProperty("role", role)
            m.addProperty("content", content)
            arr.add(m)
        }
        obj.add("messages", arr)
        return gson.toJson(obj)
    }

    private fun buildRequest(baseUrl: String, apiKey: String, body: String): Request {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/Alone2235/AI-Gateway")
            .addHeader("X-Title", "AI Gateway")
            .post(body.toRequestBody(JSON))
            .build()
    }

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double = 0.7
    ): Flow<StreamEvent> = callbackFlow {
        val request = buildRequest(baseUrl, apiKey, buildBody(model, messages, true, temperature))
        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(c: Call, e: IOException) {
                trySend(StreamEvent.Failed(e.message ?: "network error", 0))
                close()
            }

            override fun onResponse(c: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = try { resp.body?.string()?.take(500) } catch (_: Exception) { null }
                        trySend(StreamEvent.Failed(reasonText(resp.code, errBody), resp.code))
                        close()
                        return
                    }
                    val body = resp.body
                    if (body == null) {
                        trySend(StreamEvent.Failed("empty body", resp.code))
                        close()
                        return
                    }
                    try {
                        val source = body.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank() || !line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            try {
                                val json = gson.fromJson(payload, JsonObject::class.java)
                                if (json.has("error")) {
                                    val msg = json.getAsJsonObject("error").get("message")?.asString ?: "stream error"
                                    trySend(StreamEvent.Failed(msg, 0))
                                    break
                                }
                                val choices = json.getAsJsonArray("choices") ?: continue
                                if (choices.size() == 0) continue
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: continue
                                val content = delta.get("content")
                                if (content != null && !content.isJsonNull) {
                                    val text = content.asString
                                    if (text.isNotEmpty()) trySend(StreamEvent.Chunk(text))
                                }
                            } catch (_: Exception) { }
                        }
                        trySend(StreamEvent.Done(model, baseUrl))
                    } catch (e: Exception) {
                        trySend(StreamEvent.Failed(e.message ?: "read error", 0))
                    }
                    close()
                }
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun reasonText(code: Int, body: String?): String {
        val base = when (code) {
            401 -> "API Key 无效 (401)"
            402 -> "余额不足 (402)"
            403 -> "无权限 (403)"
            404 -> "模型不存在 (404)"
            413 -> "请求过长 (413)"
            429 -> "限流 (429)"
            500 -> "服务端错误 (500)"
            502 -> "网关错误 (502)"
            503 -> "服务不可用 (503)"
            504 -> "超时 (504)"
            else -> "HTTP $code"
        }
        return if (body.isNullOrBlank()) base else "$base: ${body.take(160)}"
    }

    suspend fun listModels(baseUrl: String, apiKey: String): List<String> {
        val url = baseUrl.trimEnd('/') + "/models"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: return emptyList()
                if (!resp.isSuccessful) return emptyList()
                val json = gson.fromJson(raw, JsonObject::class.java)
                val data = json.getAsJsonArray("data") ?: return emptyList()
                data.mapNotNull { it.asJsonObject.get("id")?.asString }.sorted()
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun ping(baseUrl: String, apiKey: String): Pair<Boolean, String> {
        val url = baseUrl.trimEnd('/') + "/models"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) true to "连接成功"
                else false to reasonText(resp.code, null)
            }
        } catch (e: Exception) {
            false to (e.message ?: "连接失败")
        }
    }
}
