package com.eta.aigateway.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 伪流式检测。
 *
 * 有些 provider 声称 stream:true，实际先攒完整个回答再一次性吐。
 * 特征：首字延迟异常高，且首字之后极短时间内涌入大量内容。
 *
 * 记录下来供路由降权，并在仪表盘展示。
 */
object StreamQuality {

    private data class Sample(
        val firstTokenMs: Long,
        val burstRatio: Float,   // 首字后 200ms 内收到的字符数 / 总字符
        val totalChars: Int
    )

    private val samples = ConcurrentHashMap<String, MutableList<Sample>>()

    private const val MAX_SAMPLES = 10

    fun record(key: String, firstTokenMs: Long, totalChars: Int, burstChars: Int) {
        if (totalChars < 200) return   // 太短看不出
        val ratio = if (totalChars == 0) 0f else burstChars.toFloat() / totalChars
        val list = samples.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.add(Sample(firstTokenMs, ratio, totalChars))
            if (list.size > MAX_SAMPLES) list.removeAt(0)
        }
    }

    /** 疑似伪流式：首字慢且后面一次性涌入 */
    fun isFakeStream(key: String): Boolean {
        val list = samples[key] ?: return false
        val recent = synchronized(list) { list.toList() }
        if (recent.isEmpty()) return false
        val slowStart = recent.count { it.firstTokenMs > 5000 }
        val bursty = recent.count { it.burstRatio > 0.7f }
        return slowStart >= 2 && bursty >= 2
    }

    /** 平均首字延迟 */
    fun avgFirstTokenMs(key: String): Long {
        val list = samples[key] ?: return 0
        val recent = synchronized(list) { list.toList() }
        return if (recent.isEmpty()) 0 else recent.map { it.firstTokenMs }.average().toLong()
    }

    fun fakeStreamKeys(): List<String> =
        samples.keys.filter { isFakeStream(it) }

    fun clear() = samples.clear()
}
