package com.eta.aigateway.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 来源可用性判定：区分「瞬时过载」和「彻底不可用」。
 *
 * 逻辑：
 *   1. 收到 5xx → 短冷却，等一会儿再看
 *   2. 冷却结束后仍然 5xx → 说明不是瞬时抖动，冷却翻倍
 *   3. 连续多轮都这样 → 判定为死源，长期停用
 *   4. 任何时候成功一次 → 立即恢复正常
 */
object SourceHealth {

    private const val SHORT_COOLDOWN = 60_000L          // 首次 1 分钟
    private const val MAX_COOLDOWN = 6 * 60 * 60_000L   // 上限 6 小时
    private const val DEAD_THRESHOLD = 3                // 连续 3 轮未恢复判死

    private data class State(
        val strikes: Int = 0,          // 连续未恢复次数
        val until: Long = 0,           // 冷却结束时间
        val dead: Boolean = false,     // 是否判定为死源
        val lastCode: Int = 0,
        val reason: String = ""
    )

    private val states = ConcurrentHashMap<String, State>()

    /** 用户开关：关掉则完全不屏蔽 5xx 来源 */
    @Volatile
    var enabled: Boolean = true

    /** 记录一次 5xx 失败 */
    fun onServerError(key: String, code: Int, reason: String) {
        if (!enabled) return
        val cur = states[key] ?: State()
        val strikes = cur.strikes + 1
        val cooldown = (SHORT_COOLDOWN * (1L shl (strikes - 1).coerceAtMost(6)))
            .coerceAtMost(MAX_COOLDOWN)
        // 开关打开时：第一次 5xx 就拉黑，之后只在冷却结束后试探一次
        val dead = strikes >= DEAD_THRESHOLD
        states[key] = State(
            strikes = strikes,
            until = System.currentTimeMillis() + if (dead) MAX_COOLDOWN else cooldown,
            dead = dead,
            lastCode = code,
            reason = reason
        )
    }

    /** 成功一次：完全恢复 */
    fun onSuccess(key: String) {
        states.remove(key)
    }

    fun isDead(key: String): Boolean = states[key]?.dead == true

    fun isCooling(key: String): Boolean {
        val s = states[key] ?: return false
        if (s.dead) return true
        if (System.currentTimeMillis() >= s.until) return false
        return true
    }

    fun cooldownUntil(key: String): Long = states[key]?.until ?: 0

    fun strikes(key: String): Int = states[key]?.strikes ?: 0

    fun reason(key: String): String? = states[key]?.reason

    /** 手动恢复一个死源（用户在 UI 里点"重试"） */
    fun revive(key: String) {
        states.remove(key)
    }

    fun deadKeys(): List<String> = states.filterValues { it.dead }.keys.toList()

    fun clear() = states.clear()

    /** 某来源是否应被路由跳过 */
    fun shouldSkip(key: String): Boolean {
        if (!enabled) return false
        return isDead(key) || isCooling(key)
    }
}
