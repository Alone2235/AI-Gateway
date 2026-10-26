package com.eta.aigateway.engine

import com.eta.aigateway.data.model.*
import com.eta.aigateway.data.provider.ChatProtocol
import com.eta.aigateway.data.provider.ProtocolType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

class GatewayEngine(
    private val providers: List<ProviderConfig>,
    private val groups: List<ModelGroup>,
    private val policy: RetryPolicy = RetryPolicy()
) {

    private data class Attempt(
        val group: ModelGroup,
        val source: ModelSource,
        val baseUrl: String,
        val apiKey: String,
        val keyIndex: Int
    )

    private data class TryResult(val success: Boolean, val error: String, val code: Int)

    fun chat(
        target: ModelGroup,
        history: List<Pair<String, String>>,
        temperature: Double = 0.7,
        source: String = "app",
        maxTokens: Int = 0
    ): Flow<StreamEvent> = flow {
        val startedAt = System.currentTimeMillis()
        val attempts = buildAttempts(target)

        if (attempts.isEmpty()) {
            emit(StreamEvent.Failed("没有可用的模型来源，请检查配置与启用状态", 0))
            return@flow
        }

        var lastError = "未知错误"
        var lastCode = 0

        for ((index, att) in attempts.withIndex()) {
            if (System.currentTimeMillis() - startedAt > policy.globalBudgetMs) {
                emit(StreamEvent.Failed("整体请求超时（${policy.globalBudgetMs / 1000}s）", 408))
                return@flow
            }
            if (CooldownTracker.isCooling(att.source.key)) continue
            if (index > 0) {
                emit(StreamEvent.Switched(target.modelId, att.source.modelId,
                    "切换到 ${att.source.providerName}"))
            }
            val result = tryOne(att, history, temperature, maxTokens, source, startedAt)
            if (result.success) return@flow
            lastError = result.error
            lastCode = result.code
        }

        emit(StreamEvent.Failed("所有来源均失败：$lastError", lastCode))
    }

    private suspend fun FlowCollector<StreamEvent>.tryOne(
        att: Attempt,
        history: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int,
        source: String,
        startedAt: Long
    ): TryResult {
        val proto = ChatProtocol.of(ProtocolType.from(att.source.protocol))
        var attempt = 0
        var lastErr = ""
        var lastCode = 0
        val sb = StringBuilder()
        val modelStarted = System.currentTimeMillis()

        while (attempt < policy.maxAttempts) {
            attempt++
            if (System.currentTimeMillis() - modelStarted > policy.perModelBudgetMs) break

            var gotContent = false
            var firstChunkAt = 0L
            lastErr = ""

            try {
                proto.stream(
                    baseUrl = att.baseUrl,
                    apiKey = att.apiKey,
                    model = att.source.modelId,
                    systemPrompt = null,
                    history = history,
                    temperature = temperature,
                    maxTokens = maxTokens
                ).collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> {
                            if (!gotContent) {
                                gotContent = true
                                firstChunkAt = System.currentTimeMillis()
                            }
                            sb.append(ev.text)
                            emit(ev)
                        }
                        is StreamEvent.Failed -> {
                            lastCode = ev.code
                            lastErr = ev.reason
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                lastCode = 0
                lastErr = e.message ?: "异常"
            }

            if (gotContent) {
                val latency = if (firstChunkAt > 0) firstChunkAt - modelStarted
                else System.currentTimeMillis() - modelStarted
                HealthTracker.record(att.source.key, true, latency)
                CooldownTracker.markSuccess(att.source.key)
                recordUsage(att, source, true, 200, "", latency, history, sb.length, attempt - 1)
                emit(StreamEvent.Done(att.source.modelId, att.source.providerName))
                return TryResult(true, "", 200)
            }

            val latency = System.currentTimeMillis() - modelStarted
            HealthTracker.record(att.source.key, false, latency)
            recordUsage(att, source, false, lastCode, lastErr, latency, history, 0, attempt - 1)

            if (lastCode == 401 || lastCode == 402 || lastCode == 403 || lastCode == 404) {
                CooldownTracker.markHardFailure(att.source.key, lastCode, lastErr)
                break
            }
            if (lastCode != 0 && !policy.shouldRetry(lastCode)) break
            if (attempt < policy.maxAttempts) delay(policy.backoffFor(attempt))
        }

        CooldownTracker.markSoftFailure(att.source.key, lastErr.ifBlank { "重试耗尽" })
        return TryResult(false, lastErr.ifBlank { "重试 ${policy.maxAttempts} 次仍失败" }, lastCode)
    }

    /** 展开某个模型组的全部来源，按路由顺序 */
    private fun buildAttempts(target: ModelGroup): List<Attempt> {
        val ordered = AutoRouter.order(target)
        val list = mutableListOf<Attempt>()
        ordered.forEach { src ->
            val p = providers.firstOrNull { it.id == src.providerId } ?: return@forEach
            if (!p.enabled || p.baseUrl.isBlank() || p.apiKeys.isEmpty()) return@forEach
            p.apiKeys.forEachIndexed { idx, key ->
                list.add(Attempt(target, src, p.baseUrl, key, idx))
            }
        }
        return list
    }

    private fun recordUsage(
        att: Attempt,
        source: String,
        success: Boolean,
        code: Int,
        err: String,
        latency: Long,
        history: List<Pair<String, String>>,
        outChars: Int,
        depth: Int
    ) {
        UsageRecorder.record(
            modelId = att.source.modelId,
            modelRequested = att.group.modelId,
            providerId = att.source.providerId,
            providerName = att.source.providerName,
            source = source,
            success = success,
            httpCode = code,
            errorMsg = err,
            latencyMs = latency,
            promptTokens = estimateTokens(history),
            completionTokens = if (outChars > 0) (outChars / 3).coerceAtLeast(1) else 0,
            fallbackDepth = depth
        )
    }

    private fun estimateTokens(history: List<Pair<String, String>>): Int {
        val chars = history.sumOf { it.second.length }
        return (chars / 3).coerceAtLeast(1)
    }
}
