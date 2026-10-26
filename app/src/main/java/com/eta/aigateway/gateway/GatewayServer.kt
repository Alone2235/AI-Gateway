package com.eta.aigateway.gateway

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.eta.aigateway.engine.GatewayEngine
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.ModelPreference
import com.eta.aigateway.data.model.ProviderConfig
import com.eta.aigateway.data.model.StreamEvent
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 内嵌 OpenAI 兼容网关。
 * 外部客户端（Cherry Studio / ChatBox / LobeChat / curl）填：
 *   Base URL: http://<手机IP>:8080/v1
 *   API Key : <网关访问密钥>
 * 即可使用本机聚合的全部 provider。
 */
class GatewayServer(
    port: Int,
    private val stateProvider: () -> GatewayState
) : NanoHTTPD(port) {

    data class GatewayState(
        val accessKeys: List<String>,
        val providers: List<ProviderConfig>,
        val groups: List<ModelGroup>,
        val prefs: Map<String, ModelPreference>,
        val enabled: Boolean,
        val allowLan: Boolean
    )

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val st = stateProvider()
        if (!st.enabled) {
            return json(503, mapOf("error" to mapOf("message" to "网关未启用")))
        }
        val uri = session.uri ?: "/"

        // 鉴权
        if (!checkAuth(session, st)) {
            return json(401, mapOf(
                "error" to mapOf(
                    "message" to "Invalid API key. 请在 App 设置中查看网关访问密钥。",
                    "type" to "invalid_request_error"
                )
            ))
        }

        return try {
            when {
                uri.endsWith("/models") && session.method == Method.GET -> handleModels(st)
                uri.endsWith("/chat/completions") || uri.endsWith("/completions") ->
                    ChatHandler(gson, stateProvider).handle(session)
                uri.endsWith("/health") -> json(200, mapOf("status" to "ok", "app" to "AI-Gateway"))
                else -> json(404, mapOf("error" to mapOf("message" to "Not found: $uri")))
            }
        } catch (e: Exception) {
            Log.e("GatewayServer", "serve error", e)
            json(500, mapOf("error" to mapOf("message" to (e.message ?: "internal error"))))
        }
    }

    private fun checkAuth(session: IHTTPSession, st: GatewayState): Boolean {
        if (st.accessKeys.isEmpty()) return true
        val h = session.headers["authorization"]
            ?: session.headers["Authorization"]
            ?: session.headers["x-api-key"]
            ?: return false
        val token = h.removePrefix("Bearer ").removePrefix("bearer ").trim()
        return st.accessKeys.any { it == token }
    }

    private fun handleModels(st: GatewayState): Response {
        val enabled = st.groups.filter { it.enabled && it.enabledSources.isNotEmpty() }
        val data = mutableListOf<Map<String, Any>>()
        // auto 虚拟模型：由网关自动选择
        data.add(mapOf("id" to "auto", "object" to "model", "owned_by" to "gateway"))
        enabled.forEach { g ->
            data.add(mapOf(
                "id" to g.modelId,
                "object" to "model",
                "owned_by" to g.sources.joinToString(",") { it.providerName }
            ))
        }
        return json(200, mapOf("object" to "list", "data" to data))
    }

    private fun json(code: Int, body: Any): Response {
        return newFixedLengthResponse(
            Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR,
            "application/json",
            gson.toJson(body)
        )
    }
}
