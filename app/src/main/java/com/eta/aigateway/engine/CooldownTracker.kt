package com.eta.aigateway.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 失败冷却记录。429/503 后把 key 或 provider 打入冷宫，
 * 过一段时间自动恢复。避免一直往死路上撞。
 */
object CooldownTracker {

    private data class Entry(val until: Long, val reason: String)

    private val cooldowns = ConcurrentHashMap<String, Entry>()

    /** 记录一次失败。cooldownMs 后恢复 */
    fun markFailed(key: String, cooldownMs: Long, reason: String) {
        cooldowns[key] = Entry(System.currentTimeMillis() + cooldownMs, reason)
    }

    fun isCooling(key: String): Boolean {
        val e = cooldowns[key] ?: return false
        if (System.currentTimeMillis() >= e.until) {
            cooldowns.remove(key)
            return false
        }
        return true
    }

    fun remainingMs(key: String): Long {
        val e = cooldowns[key] ?: return 0
        return (e.until - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun reason(key: String): String? = cooldowns[key]?.reason

    fun clear() = cooldowns.clear()

    /** 根据错误码决定冷却时长 */
    fun cooldownFor(code: Int): Long = when (code) {
        429 -> 60_000L        // 限流 1 分钟
        402 -> 30 * 60_000L   // 余额不足 30 分钟
        401, 403 -> 60 * 60_000L  // 鉴权问题 1 小时
        500, 502, 503, 504 -> 30_000L  // 服务端问题 30 秒
        else -> 15_000L
    }
}
