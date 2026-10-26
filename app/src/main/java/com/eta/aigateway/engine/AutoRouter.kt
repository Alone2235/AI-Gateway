package com.eta.aigateway.engine

import com.eta.aigateway.data.model.HealthStatus
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.ModelSource

/**
 * 路由决策：只回答"按什么顺序尝试"。
 * 真正的请求执行在 GatewayEngine。
 */
object AutoRouter {

    /** 把候选来源按优先级排好序 */
    fun order(group: ModelGroup): List<ModelSource> {
        return group.enabledSources
            .filter { !CooldownTracker.isCooling(it.key) }
            .sortedWith(
                compareBy<ModelSource> { healthRank(it) }
                    .thenBy { HealthTracker.avgLatency(it.key).let { l -> if (l == 0L) 5000 else l } }
            )
    }

    /** 供仪表盘展示：包含冷却中的 */
    fun allWithHealth(group: ModelGroup): List<ModelSource> {
        return group.sources.map { s ->
            s.copy(
                health = HealthTracker.status(s.key, CooldownTracker.cooldownUntil(s.key)),
                lastLatencyMs = HealthTracker.avgLatency(s.key),
                cooldownUntil = CooldownTracker.cooldownUntil(s.key)
            )
        }
    }

    private fun healthRank(s: ModelSource): Int = when (
        HealthTracker.status(s.key, CooldownTracker.cooldownUntil(s.key))
    ) {
        HealthStatus.HEALTHY -> 0
        HealthStatus.UNKNOWN -> 1
        HealthStatus.SLOW -> 2
        HealthStatus.COOLING -> 3
    }

    /**
     * 按标签池筛选模型组。
     * tags 为空则不过滤（返回全部启用的组）。
     */
    fun groupsFor(groups: List<ModelGroup>, tags: List<String>): List<ModelGroup> {
        val enabled = groups.filter { it.enabled && it.enabledSources.isNotEmpty() }
        if (tags.isEmpty()) return enabled
        val matched = enabled.filter { g -> g.tags.any { it in tags } }
        return matched.ifEmpty { enabled }  // 没有匹配就退回全部，避免无模型可用
    }

    /** 某组是否整体不可用（全部来源冷却中） */
    fun isExhausted(group: ModelGroup): Boolean =
        group.enabledSources.isNotEmpty() && group.enabledSources.all {
            CooldownTracker.isCooling(it.key)
        }
}
