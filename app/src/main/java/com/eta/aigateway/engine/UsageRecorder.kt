package com.eta.aigateway.engine

import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.UsageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/** 用量记录。异步写入，不阻塞请求链路。 */
object UsageRecorder {

    private var db: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(database: AppDatabase) { db = database }

    fun record(
        modelId: String,
        modelRequested: String,
        providerId: String,
        providerName: String,
        source: String,
        success: Boolean,
        httpCode: Int,
        errorMsg: String,
        latencyMs: Long,
        promptTokens: Int,
        completionTokens: Int,
        fallbackDepth: Int
    ) {
        val d = db ?: return
        val rec = UsageRecord(
            id = UUID.randomUUID().toString(),
            modelId = modelId,
            modelRequested = modelRequested,
            providerId = providerId,
            providerName = providerName,
            source = source,
            success = success,
            httpCode = httpCode,
            errorMsg = errorMsg.take(200),
            latencyMs = latencyMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            fallbackDepth = fallbackDepth
        )
        scope.launch {
            try { d.usageDao().insert(rec) } catch (_: Exception) {}
        }
    }
}
