package com.eta.aigateway.engine

import com.eta.aigateway.data.model.HealthStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * 实时健康状态。基于最近请求的滑动窗口，不依赖历史累计。
 *
 * 判定：
 *   冷却中               -> COOLING
 *   最近窗口失败率 > 50%  -> COOLING
 *   最近延迟 > 基线 1.5x  -> SLOW
 *   有成功记录           -> HEALTHY
 *   无记录               -> UNKNOWN
 */
object HealthTracker {

    private const val WINDOW_MS = 5 * 60 * 1000L      // 5 分钟窗口
    private const val BASE_LATENCY_FLOOR = 800L        // 基线下限，避免噪声

    private data class Sample(val at: Long, val ok: Boolean, val latencyMs: Long)

    private val samples = ConcurrentHashMap<String, MutableList<Sample>>()
    private val baselines = ConcurrentHashMap<String, Long>()

    /** 记录一次调用的结果 */
    fun record(key: String, ok: Boolean, latencyMs: Long) {
        val now = System.currentTimeMillis()
        val list = samples.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.add(Sample(now, ok, latencyMs))
            list.removeAll { now - it.at > WINDOW_MS }
            if (list.size > 50) list.subList(0, list.size - 50).clear()
        }
        if (ok) {
            // 慢慢收敛的基线
            val old = baselines[key] ?: latencyMs
            baselines[key] = ((old * 4) + latencyMs) / 5
        }
    }

    fun status(key: String, cooldownUntil: Long = 0): HealthStatus {
        if (cooldownUntil > System.currentTimeMillis()) return HealthStatus.COOLING
        val list = samples[key] ?: return HealthStatus.UNKNOWN
        val now = System.currentTimeMillis()
        val recent = synchronized(list) { list.filter { now - it.at <= WINDOW_MS } }
        if (recent.isEmpty()) return HealthStatus.UNKNOWN

        val okRate = recent.count { it.ok }.toDouble() / recent.size
        if (okRate < 0.5) return HealthStatus.COOLING

        val avgLatency = recent.filter { it.ok }.map { it.latencyMs }.average().toLong()
        val base = baselines[key] ?: avgLatency
        val effectiveBase = maxOf(base, BASE_LATENCY_FLOOR)
        if (avgLatency > effectiveBase * 1.5) return HealthStatus.SLOW

        return if (okRate >= 0.8) HealthStatus.HEALTHY else HealthStatus.SLOW
    }

    fun avgLatency(key: String): Long {
        val list = samples[key] ?: return 0
        val now = System.currentTimeMillis()
        val recent = synchronized(list) { list.filter { now - it.at <= WINDOW_MS && it.ok } }
        return if (recent.isEmpty()) 0 else recent.map { it.latencyMs }.average().toLong()
    }

    fun recentFailRate(key: String): Float {
        val list = samples[key] ?: return 0f
        val now = System.currentTimeMillis()
        val recent = synchronized(list) { list.filter { now - it.at <= WINDOW_MS } }
        if (recent.isEmpty()) return 0f
        return (recent.count { !it.ok }.toFloat() / recent.size)
    }

    fun clear() {
        samples.clear()
        baselines.clear()
    }
}
