package com.eta.aigateway.data.model

/**
 * 识别模型归属厂商，用于"一键勾选大厂模型"。
 * 只做前缀匹配，不猜能力。
 */
object ModelClassifier {

    private data class Rule(val prefixes: List<String>, val vendor: String)

    private val rules = listOf(
        Rule(listOf("claude-"), "Anthropic"),
        Rule(listOf("gpt-", "o1-", "o3-", "o4-", "chatgpt-", "text-embedding-"), "OpenAI"),
        Rule(listOf("gemini-", "gemma-", "learnlm-"), "Google"),
        Rule(listOf("deepseek-"), "DeepSeek"),
        Rule(listOf("llama-", "meta-llama/", "llama3"), "Meta"),
        Rule(listOf("mixtral-", "mistral-", "codestral-", "pixtral-"), "Mistral"),
        Rule(listOf("qwen-", "qwen2", "qwen3"), "阿里"),
        Rule(listOf("glm-", "chatglm"), "智谱"),
        Rule(listOf("moonshot-", "kimi-"), "Moonshot"),
        Rule(listOf("doubao-", "skylark-"), "字节"),
        Rule(listOf("ernie-", "wenxin"), "百度"),
        Rule(listOf("hunyuan-"), "腾讯"),
        Rule(listOf("command-", "c4ai-"), "Cohere"),
        Rule(listOf("grok-"), "xAI"),
        Rule(listOf("phi-"), "Microsoft"),
        Rule(listOf("nova-", "titan-"), "Amazon")
    )

    /** 取模型 id 的最后一段作为匹配对象（兼容 openrouter 的 vendor/model 形式） */
    private fun normalized(modelId: String): String {
        val id = modelId.lowercase()
        return if (id.contains('/')) id.substringAfterLast('/') else id
    }

    /** 返回厂商名，识别不出返回 null */
    fun vendorOf(modelId: String): String? {
        val n = normalized(modelId)
        return rules.firstOrNull { r -> r.prefixes.any { n.startsWith(it) } }?.vendor
    }

    /** 是否属于已知大厂 */
    fun isKnownVendor(modelId: String): Boolean = vendorOf(modelId) != null

    /** 用作列表显示名：去掉版本号后缀，保留可辨识部分 */
    fun prettyName(modelId: String): String {
        val base = if (modelId.contains('/')) modelId.substringAfterLast('/') else modelId
        return base
    }
}
