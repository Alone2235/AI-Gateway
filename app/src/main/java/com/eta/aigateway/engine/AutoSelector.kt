package com.eta.aigateway.engine

import com.eta.aigateway.data.model.HealthStatus
import com.eta.aigateway.data.model.ModelClassifier
import com.eta.aigateway.data.model.ModelGroup

/**
 * auto 模型的选择策略。
 * 不是"随便挑一个"，而是按多层规则打分排序。
 */
object AutoSelector {

    data class Choice(val group: ModelGroup, val score: Double, val reason: String)

    /**
     * 为一次请求选出候选组顺序。
     *
     * @param groups 全部模型组
     * @param taskTags 任务推断出的标签（如 heavy / light / long），空则不限
     */
    fun rank(groups: List<ModelGroup>, taskTags: Set<String> = emptySet()): List<Choice> {
        return groups
            .filter { it.enabled && it.enabledSources.isNotEmpty() }
            .filter { !AutoRouter.isExhausted(it) }
            .map { g -> score(g, taskTags) }
            .sortedByDescending { it.score }
    }

    private fun score(g: ModelGroup, taskTags: Set<String>): Choice {
        var s = 0.0
        val reasons = mutableListOf<String>()

        // 1. 健康度：健康 > 未知 > 变慢
        val health = g.bestHealth
        when (health) {
            HealthStatus.HEALTHY -> { s += 60; reasons.add("健康") }
            HealthStatus.UNKNOWN -> { s += 30; reasons.add("未验证") }
            HealthStatus.SLOW -> { s += 5; reasons.add("偏慢") }
            HealthStatus.COOLING -> { s -= 50; reasons.add("冷却中") }
        }

        // 2. 来源数量：多来源更稳
        s += (g.sources.count { it.enabled }) * 4.0

        // 3. 近期延迟：越快越好，最多加 30 分
        val latencies = g.enabledSources
            .map { com.eta.aigateway.engine.HealthTracker.avgLatency(it.key) }
            .filter { it > 0 }
        if (latencies.isNotEmpty()) {
            val avg = latencies.average()
            val latencyScore = (30.0 * (1.0 - (avg / 5000.0).coerceIn(0.0, 1.0)))
            s += latencyScore
            if (avg < 1000) reasons.add("低延迟")
        }

        // 4. 任务匹配
        val heavy = ModelClassifier.isHeavy(g.modelId)
        if ("heavy" in taskTags) {
            if (heavy) { s += 25; reasons.add("适合复杂任务") } else s -= 10
        }
        if ("light" in taskTags && !heavy) { s += 15; reasons.add("适合轻量任务") }

        // 5. 用户标签加权
        if (g.tags.contains("prefer")) { s += 40; reasons.add("已设为首选") }

        return Choice(g, s, reasons.joinToString("、").ifBlank { "无特别优势" })
    }

    /**
     * 简单任务判断：纯本地启发式，不调用模型。
     * 返回 heavy / light / long。
     */
    fun inferTaskTags(lastUserMessage: String): Set<String> {
        val text = lastUserMessage
        val tags = mutableSetOf<String>()

        // 长文本
        if (text.length > 3000) tags.add("long")

        // 复杂任务信号
        val heavySignals = listOf(
            "代码", "函数", "实现", "重构", "调试", "报错", "异常", "编译",
            "分析", "推导", "证明", "计算", "设计", "架构", "优化", "对比",
            "code", "function", "implement", "debug", "refactor", "analyze",
            "```", "class ", "def ", "fun ", "import ", "SELECT ", "docker"
        )
        if (heavySignals.any { text.contains(it, ignoreCase = true) }) tags.add("heavy")

        // 轻量任务信号
        if (text.length < 60 && heavySignals.none { text.contains(it, ignoreCase = true) }) {
            tags.add("light")
        }

        return tags
    }
}
