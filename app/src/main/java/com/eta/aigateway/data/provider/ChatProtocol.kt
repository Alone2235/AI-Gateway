package com.eta.aigateway.data.provider

import com.eta.aigateway.data.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/** 对话协议类型 */
enum class ProtocolType(val id: String, val label: String) {
    OPENAI("openai", "OpenAI 兼容"),
    ANTHROPIC("anthropic", "Anthropic");

    companion object {
        fun from(id: String?): ProtocolType =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

/** 统一的对话协议接口 */
interface ChatProtocol {

    /** 流式对话 */
    fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        history: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int
    ): Flow<StreamEvent>

    /** 拉模型列表 */
    suspend fun listModels(baseUrl: String, apiKey: String): List<String>

    /** 连通性测试 */
    suspend fun ping(baseUrl: String, apiKey: String): Pair<Boolean, String>

    companion object {
        fun of(type: ProtocolType): ChatProtocol = when (type) {
            ProtocolType.OPENAI -> OpenAIProtocol
            ProtocolType.ANTHROPIC -> AnthropicProtocol
        }
    }
}
