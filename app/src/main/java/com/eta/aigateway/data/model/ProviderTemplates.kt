package com.eta.aigateway.data.model

/** 内置 provider 模板，用户填 Key 即可用 */
object ProviderTemplates {
    val all: List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "cerebras",
            name = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            builtin = true
        ),
        ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "moonshot",
            name = "Moonshot",
            baseUrl = "https://api.moonshot.cn/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "siliconflow",
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "modelscope",
            name = "魔搭 ModelScope",
            baseUrl = "https://api-inference.modelscope.cn/v1",
            builtin = true
        ),
        ProviderConfig(
            id = "zhipu",
            name = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            builtin = true
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic 官方",
            baseUrl = "https://api.anthropic.com/v1",
            builtin = true,
            protocol = "anthropic"
        ),
        // 自定义 provider 由用户运行时新建，不预置固定条目
    )

    fun byId(id: String): ProviderConfig? = all.firstOrNull { it.id == id }
}
