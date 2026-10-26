package com.eta.aigateway.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一条聊天消息 */
@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,          // user / assistant / system
    val content: String,
    val model: String = "",    // 实际生成该消息的模型
    val providerName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val tokenIn: Int = 0,
    val tokenOut: Int = 0
)

/** 会话 */
@Entity(tableName = "sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelId: String = ""
)

/** 单个 provider 配置 */
data class ProviderConfig(
    val id: String,
    val name: String,              // 显示名，如 "OpenRouter"
    val baseUrl: String,           // https://openrouter.ai/api/v1
    val apiKeys: List<String> = emptyList(),
    val enabled: Boolean = true,
    val builtin: Boolean = false    // 内置模板
)

/** 模型条目 */
data class ModelEntry(
    val id: String,                // 模型 id，如 "anthropic/claude-3.5-sonnet"
    val displayName: String,
    val providerId: String,
    val providerName: String,
    val contextLength: Int = 0
)

/** 降级策略 */
enum class FallbackPolicy(val label: String, val desc: String) {
    STRICT("严格", "只用选中模型，失败就失败"),
    ROTATE_KEY("换 Key", "同一 provider 内轮换 API Key"),
    NEAR_MODEL("相近模型", "降级到同 provider 的相近模型"),
    ANY("完全自由", "任何可用 provider/模型")
}

/** 单模型的个性化设置 */
data class ModelPreference(
    val modelId: String,
    val policy: FallbackPolicy = FallbackPolicy.ROTATE_KEY,
    val fallbackProviders: List<String> = emptyList(),
    val pinned: Boolean = false
)

/** 流式事件 */
sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Done(val model: String, val provider: String, val tokenOut: Int = 0) : StreamEvent()
    data class Failed(val reason: String, val code: Int = 0) : StreamEvent()
    data class Switched(val fromModel: String, val toModel: String, val reason: String) : StreamEvent()
}
