package com.eta.aigateway.data.provider

import com.eta.aigateway.data.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/** OpenAI 兼容协议适配器，复用已有 OpenAIClient */
object OpenAIProtocol : ChatProtocol {

    override fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        history: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int
    ): Flow<StreamEvent> {
        val msgs = buildList {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { add("system" to it) }
            addAll(history)
        }
        return OpenAIClient.streamChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = msgs,
            temperature = temperature
        )
    }

    override suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        OpenAIClient.listModels(baseUrl, apiKey)

    override suspend fun ping(baseUrl: String, apiKey: String): Pair<Boolean, String> =
        OpenAIClient.ping(baseUrl, apiKey)
}
