package com.eta.aigateway.data.provider

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.eta.aigateway.data.model.BalanceSnapshot
import com.eta.aigateway.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 余额查询。各家接口不统一，这里逐个适配。
 * 不支持的服务明确返回 supported=false，不做假数据。
 */
object BalanceClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun query(p: ProviderConfig): BalanceSnapshot = withContext(Dispatchers.IO) {
        val key = p.apiKeys.firstOrNull()
        if (key.isNullOrBlank()) {
            return@withContext BalanceSnapshot(
                providerId = p.id, providerName = p.name,
                supported = true, error = "未配置 API Key"
            )
        }
        try {
            when (p.id) {
                "openrouter" -> queryOpenRouter(p, key)
                "deepseek" -> queryDeepSeek(p, key)
                "moonshot" -> queryMoonshot(p, key)
                "siliconflow" -> querySiliconFlow(p, key)
                else -> BalanceSnapshot(
                    providerId = p.id, providerName = p.name,
                    supported = false, error = "该服务未提供余额查询接口"
                )
            }
        } catch (e: Exception) {
            BalanceSnapshot(
                providerId = p.id, providerName = p.name,
                supported = true, error = e.message ?: "查询失败"
            )
        }
    }

    private fun get(url: String, key: String, extraHeader: Pair<String, String>? = null): String? {
        val b = Request.Builder().url(url).addHeader("Authorization", "Bearer $key").get()
        extraHeader?.let { b.addHeader(it.first, it.second) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()
        }
    }

    /** OpenRouter: GET /api/v1/credits */
    private fun queryOpenRouter(p: ProviderConfig, key: String): BalanceSnapshot {
        val base = p.baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        val raw = get("$base/api/v1/credits", key)
            ?: return BalanceSnapshot(p.id, p.name, true, error = "接口返回失败")
        val root = JsonParser.parseString(raw).asJsonObject
        val data = root.getAsJsonObject("data") ?: root
        val total = data.get("total_credits")?.asDouble ?: 0.0
        val used = data.get("total_usage")?.asDouble ?: 0.0
        return BalanceSnapshot(
            providerId = p.id, providerName = p.name, supported = true,
            totalCredits = total, usedCredits = used,
            remaining = (total - used).coerceAtLeast(0.0),
            currency = "USD", rawInfo = raw.take(400)
        )
    }

    /** DeepSeek: GET /user/balance */
    private fun queryDeepSeek(p: ProviderConfig, key: String): BalanceSnapshot {
        val base = p.baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        val raw = get("$base/user/balance", key)
            ?: return BalanceSnapshot(p.id, p.name, true, error = "接口返回失败")
        val root = JsonParser.parseString(raw).asJsonObject
        val infos = root.getAsJsonArray("balance_infos")
        if (infos == null || infos.size() == 0) {
            return BalanceSnapshot(p.id, p.name, true, error = "无余额信息")
        }
        val first = infos[0].asJsonObject
        val cur = first.get("currency")?.asString ?: "CNY"
        val total = first.get("total_balance")?.asString?.toDoubleOrNull() ?: 0.0
        val granted = first.get("granted_balance")?.asString?.toDoubleOrNull() ?: 0.0
        val topped = first.get("topped_up_balance")?.asString?.toDoubleOrNull() ?: 0.0
        return BalanceSnapshot(
            providerId = p.id, providerName = p.name, supported = true,
            totalCredits = total, usedCredits = (total - granted - topped).coerceAtLeast(0.0),
            remaining = total, currency = cur,
            rawInfo = "赠送 $granted / 充值 $topped".take(400)
        )
    }

    /** Moonshot: GET /v1/users/me/balance */
    private fun queryMoonshot(p: ProviderConfig, key: String): BalanceSnapshot {
        val base = p.baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        val raw = get("$base/v1/users/me/balance", key)
            ?: return BalanceSnapshot(p.id, p.name, true, error = "接口返回失败")
        val root = JsonParser.parseString(raw).asJsonObject
        val d = root.getAsJsonObject("data") ?: root
        val avail = d.get("available_balance")?.asDouble
            ?: d.get("available_balance")?.asString?.toDoubleOrNull() ?: 0.0
        val voucher = d.get("voucher_balance")?.asDouble ?: 0.0
        val cash = d.get("cash_balance")?.asDouble ?: 0.0
        return BalanceSnapshot(
            providerId = p.id, providerName = p.name, supported = true,
            totalCredits = avail, remaining = avail, currency = "CNY",
            rawInfo = "代金券 $voucher / 现金 $cash".take(400)
        )
    }

    /** 硅基流动: GET /v1/user/info */
    private fun querySiliconFlow(p: ProviderConfig, key: String): BalanceSnapshot {
        val base = p.baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')
        val raw = get("$base/v1/user/info", key)
            ?: return BalanceSnapshot(p.id, p.name, true, error = "接口返回失败")
        val root = JsonParser.parseString(raw).asJsonObject
        val d = root.getAsJsonObject("data") ?: root
        val bal = d.get("totalBalance")?.asDouble
            ?: d.get("totalBalance")?.asString?.toDoubleOrNull() ?: 0.0
        val charge = d.get("chargeBalance")?.asDouble ?: 0.0
        return BalanceSnapshot(
            providerId = p.id, providerName = p.name, supported = true,
            totalCredits = bal, remaining = bal, currency = "CNY",
            rawInfo = "充值余额 $charge".take(400)
        )
    }
}
