package com.eta.aigateway.engine

import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.StreamEvent
import kotlinx.coroutines.flow.toList

/**
 * 上下文压缩：历史过长时用廉价模型分段摘要，避免超出窗口。
 *
 * 策略：
 *   1. 估算总 token，超过阈值才触发
 *   2. 保留最近 N 轮原文（近期上下文最重要）
 *   3. 更早的消息按块摘要成一段"背景摘要"
 *   4. 摘要失败则退化为硬截断，不影响主流程
 */
object ContextCompressor {

    data class Config(
        val enabled: Boolean = true,
        val triggerTokens: Int = 8000,     // 超过多少触发压缩
        val keepRecentTurns: Int = 6,      // 保留最近几轮原文
        val chunkSize: Int = 20,           // 每块消息数
        val summaryMaxTokens: Int = 500
    )

    /** 粗略估算：中文约 1.5 字/token，这里取 2 字符/token 的保守值 */
    fun estimateTokens(messages: List<Pair<String, String>>): Int =
        messages.sumOf { it.second.length / 2 }

    /**
     * 压缩历史。返回可直接发给主模型的压缩后历史。
     */
    suspend fun compress(
        history: List<Pair<String, String>>,
        summarizer: ModelGroup?,
        config: Config = Config(),
        runSummary: suspend (ModelGroup, List<Pair<String, String>>) -> String
    ): List<Pair<String, String>> {
        if (!config.enabled) return history

        val total = estimateTokens(history)
        if (total < config.triggerTokens) return history
        if (summarizer == null) return hardTruncate(history, config)

        val keepCount = config.keepRecentTurns * 2
        val recent = if (history.size > keepCount) history.takeLast(keepCount) else history
        val older = if (history.size > keepCount) history.dropLast(keepCount) else emptyList()

        if (older.isEmpty()) return history

        // 分块摘要
        val chunks = older.chunked(config.chunkSize)
        val summaries = mutableListOf<String>()

        for (chunk in chunks) {
            val prompt = buildSummaryPrompt(chunk)
            val text = try {
                runSummary(summarizer, listOf("user" to prompt))
            } catch (e: Exception) {
                ""
            }
            if (text.isNotBlank()) summaries.add(text.trim())
        }

        if (summaries.isEmpty()) return hardTruncate(history, config)

        val merged = "以下是本次对话更早部分的内容摘要，请据此理解上下文：\n\n" +
            summaries.joinToString("\n\n---\n\n")

        return buildList {
            add("system" to merged)
            addAll(recent)
        }
    }

    private fun buildSummaryPrompt(chunk: List<Pair<String, String>>): String {
        val body = chunk.joinToString("\n") { (role, content) ->
            val who = if (role == "user") "用户" else "助手"
            "$who：${content.take(1200)}"
        }
        return """
            请把下面这段对话压缩成简洁的摘要，保留：
            - 用户的核心诉求和已确认的结论
            - 关键事实、数据、代码片段要点
            - 尚未解决的问题

            不要复述寒暄，不要评论，直接输出摘要正文。

            对话内容：
            $body
        """.trimIndent()
    }

    /** 摘要失败时的兜底：保留最近的，截断旧的 */
    private fun hardTruncate(
        history: List<Pair<String, String>>,
        config: Config
    ): List<Pair<String, String>> {
        val keep = config.keepRecentTurns * 2
        return if (history.size > keep) history.takeLast(keep) else history
    }
}
