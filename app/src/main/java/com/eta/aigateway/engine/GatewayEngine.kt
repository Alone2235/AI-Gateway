package com.eta.aigateway.engine

import com.eta.aigateway.data.model.*
import com.eta.aigateway.data.provider.ChatProtocol
import com.eta.aigateway.data.provider.ProtocolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GatewayEngine(
    private val providers: List<ProviderConfig>,
    private val preferences: Map<String, ModelPreference> = emptyMap(),
    private val availableModels: List<ModelEntry> = emptyList()
) {

    private data class Candidate(
        val modelId: String,
        val providerId: String,
        val providerName: String,
        val baseUrl: String,
        val apiKey: String,
        val keyLabel: String,
        val protocol: String = "openai"
    )

    fun chat(
        selectedModel: ModelEntry,
        history: List<Pair<String, String>>,
        temperature: Double = 0.7,
        source: String = "app"
    ): Flow<StreamEvent> = flow {
        val pref = preferences[selectedModel.id] ?: ModelPreference(selectedModel.id)
        val policy = pref.policy
        val candidates = buildCandidates(selectedModel, pref, policy)

        if (candidates.isEmpty()) {
            emit(StreamEvent.Failed("没有可用的 provider / key，请先在设置里配置", 0))
            return@flow
        }

        var lastError = "未知错误"
        var lastCode = 0
        var attempted = 0

        for (c in candidates) {
            attempted++
            if (CooldownTracker.isCooling("${c.providerId}:${c.keyLabel}")) continue

            if (attempted > 1) {
                emit(StreamEvent.Switched(
                    selectedModel.id, c.modelId,
                    "上一个接口不可用，已切到 ${c.providerName} / ${c.modelId}"
                ))
            }

            var gotAnyChunk = false
            var failureCode = 0
            var failureMsg = ""
            val startAt = System.currentTimeMillis()
            var outChars = 0

            try {
                val proto = ChatProtocol.of(ProtocolType.from(c.protocol))
                proto.stream(
                    baseUrl = c.baseUrl,
                    apiKey = c.apiKey,
                    model = c.modelId,
                    systemPrompt = null,
                    history = history,
                    temperature = temperature,
                    maxTokens = 0
                ).collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> {
                            gotAnyChunk = true
                            outChars += ev.text.length
                            emit(ev)
                        }
                        is StreamEvent.Done -> emit(StreamEvent.Done(c.modelId, c.providerName))
                        is StreamEvent.Failed -> { failureCode = ev.code; failureMsg = ev.reason }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                failureCode = 0
                failureMsg = e.message ?: "异常"
            }

            if (gotAnyChunk) {
                UsageRecorder.record(
                    modelId = c.modelId,
                    modelRequested = selectedModel.id,
                    providerId = c.providerId,
                    providerName = c.providerName,
                    source = source,
                    success = true,
                    httpCode = 200,
                    errorMsg = "",
                    latencyMs = System.currentTimeMillis() - startAt,
                    promptTokens = estimateTokens(history),
                    completionTokens = (outChars / 3).coerceAtLeast(1),
                    fallbackDepth = attempted - 1
                )
                return@flow
            }

            lastError = failureMsg
            lastCode = failureCode
            UsageRecorder.record(
                modelId = c.modelId,
                modelRequested = selectedModel.id,
                providerId = c.providerId,
                providerName = c.providerName,
                source = source,
                success = false,
                httpCode = failureCode,
                errorMsg = failureMsg,
                latencyMs = System.currentTimeMillis() - startAt,
                promptTokens = estimateTokens(history),
                completionTokens = 0,
                fallbackDepth = attempted - 1
            )
            CooldownTracker.markFailed(
                "${c.providerId}:${c.keyLabel}",
                CooldownTracker.cooldownFor(failureCode),
                failureMsg
            )

            if (policy == FallbackPolicy.STRICT) {
                emit(StreamEvent.Failed(failureMsg, failureCode))
                return@flow
            }
        }

        emit(StreamEvent.Failed("所有接口都失败了：$lastError", lastCode))
    }

    private fun buildCandidates(
        selected: ModelEntry,
        pref: ModelPreference,
        policy: FallbackPolicy
    ): List<Candidate> {
        val list = mutableListOf<Candidate>()

        fun addProviderModels(providerId: String, modelIds: List<String>) {
            val p = providers.firstOrNull { it.id == providerId && it.enabled } ?: return
            if (p.apiKeys.isEmpty() || p.baseUrl.isBlank()) return
            p.apiKeys.forEachIndexed { idx, key ->
                modelIds.forEach { mid ->
                    list.add(Candidate(
                        modelId = mid,
                        providerId = p.id,
                        providerName = p.name,
                        baseUrl = p.baseUrl,
                        apiKey = key,
                        keyLabel = "#${idx + 1}",
                        protocol = p.protocol
                    ))
                }
            }
        }

        addProviderModels(selected.providerId, listOf(selected.id))
        if (policy == FallbackPolicy.STRICT) return list

        if (policy == FallbackPolicy.NEAR_MODEL || policy == FallbackPolicy.ANY) {
            val siblings = availableModels
                .filter { it.providerId == selected.providerId && it.id != selected.id }
                .sortedBy { nearScore(selected.id, it.id) }
                .take(3)
                .map { it.id }
            if (siblings.isNotEmpty()) addProviderModels(selected.providerId, siblings)
        }

        if (pref.fallbackProviders.isNotEmpty()) {
            pref.fallbackProviders.forEach { pid ->
                val models = availableModels.filter { it.providerId == pid }.take(3).map { it.id }
                val fallbackModels = models.ifEmpty { listOf(selected.id) }
                addProviderModels(pid, fallbackModels)
            }
        }

        if (policy == FallbackPolicy.ANY) {
            providers.filter { it.enabled && it.id != selected.providerId && it.apiKeys.isNotEmpty() }
                .forEach { p ->
                    val models = availableModels.filter { it.providerId == p.id }.take(2).map { it.id }
                    if (models.isNotEmpty()) addProviderModels(p.id, models)
                }
        }

        return list
    }

    /** 粗估 token：中文约 1.5 字/token，英文约 4 字符/token，这里取折中 3 */
    private fun estimateTokens(history: List<Pair<String, String>>): Int {
        val chars = history.sumOf { it.second.length }
        return (chars / 3).coerceAtLeast(1)
    }

    private fun nearScore(a: String, b: String): Int {
        val family = { s: String -> s.substringBefore('/').substringBefore('-').lowercase() }
        var score = 0
        if (family(a) == family(b)) score -= 10
        val da = a.lowercase(); val db = b.lowercase()
        if (da.contains("mini") == db.contains("mini")) score -= 3
        if (da.contains("pro") == db.contains("pro")) score -= 2
        if (da.contains("flash") == db.contains("flash")) score -= 1
        return score
    }
}
