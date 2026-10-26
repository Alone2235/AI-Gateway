package com.eta.aigateway.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 分级指数退避冷却。
 *
 * 单次失败不冷却（交给重试处理）；
 * 连续失败才冷却，且冷却时长按失败次数翻倍，成功后清零。
 */
object CooldownTracker {

    private const val BASE_COOLDOWN_MS = 60_000L      // 首次冷却 60 秒
    private const val MAX_COOLDOWN_MS = 30 * 60_000L  // 上限 30 分钟

    private data class State(val until: Long, val reason: String, val strikes: Int)

    private val cooldowns = ConcurrentHashMap<String, State>()

    /** 不可重试的硬错误：直接长冷却 */
    fun markHardFailure(key: String, code: Int, reason: String) {
        val ms = when (code) {
            401, 403 -> 60 * 60_000L     // 鉴权问题，1 小时
            402 -> 30 * 60_000L          // 余额不足
            404 -> 30 * 60_000L          // 模型不存在
            else -> BASE_COOLDOWN_MS
        }
        cooldowns[key] = State(System.currentTimeMillis() + ms, reason, 99)
    }

    /** 软错误（429/5xx）：重试全失败后调用，按次数翻倍 */
    fun markSoftFailure(key: String, reason: String) {
        val prev = cooldowns[key]?.strikes ?: 0
        val strikes = prev + 1
        val ms = (BASE_COOLDOWN_MS * (1L shl (strikes - 1).coerceAtMost(5)))
            .coerceAtMost(MAX_COOLDOWN_MS)
        cooldowns[key] = State(System.currentTimeMillis() + ms, reason, strikes)
    }

    /** 成功一次，冷却清零 */
    fun markSuccess(key: String) {
        cooldowns.remove(key)
    }

    fun isCooling(key: String): Boolean {
        val e = cooldowns[key] ?: return false
        if (System.currentTimeMillis() >= e.until) {
            cooldowns.remove(key)
            return false
        }
        return true
    }

    fun cooldownUntil(key: String): Long = cooldowns[key]?.until ?: 0

    fun remainingMs(key: String): Long {
        val e = cooldowns[key] ?: return 0
        return (e.until - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun reason(key: String): String? = cooldowns[key]?.reason

    fun strikes(key: String): Int = cooldowns[key]?.strikes ?: 0

    fun clear() = cooldowns.clear()
}
