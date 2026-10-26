package com.eta.aigateway.data.model

/** 来源健康状态 */
enum class HealthStatus(val label: String, val emoji: String) {
    UNKNOWN("未知", "⚪"),
    HEALTHY("正常", "🟢"),
    SLOW("变慢", "🟡"),
    COOLING("冷却中", "🔴")
}

/** 某个模型的一个具体来源（某 provider 下的该模型） */
data class ModelSource(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val protocol: String = "openai",
    val enabled: Boolean = true,
    val health: HealthStatus = HealthStatus.UNKNOWN,
    val lastLatencyMs: Long = 0,
    val cooldownUntil: Long = 0
) {
    val key: String get() = "$providerId|$modelId"
}

/** 同一个模型在多家 provider 的聚合 */
data class ModelGroup(
    val modelId: String,            // 真实模型 id，如 claude-3-5-sonnet-20241022
    val displayName: String,
    val sources: List<ModelSource>,
    val enabled: Boolean = false,
    val tags: List<String> = emptyList(),
    val custom: Boolean = false
) {
    val enabledSources: List<ModelSource> get() = sources.filter { it.enabled }
    val bestHealth: HealthStatus
        get() = when {
            sources.any { it.health == HealthStatus.HEALTHY } -> HealthStatus.HEALTHY
            sources.any { it.health == HealthStatus.SLOW } -> HealthStatus.SLOW
            sources.any { it.health == HealthStatus.UNKNOWN } -> HealthStatus.UNKNOWN
            else -> HealthStatus.COOLING
        }
}
