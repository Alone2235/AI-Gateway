package com.eta.aigateway.data.model

/**
 * 厂商识别：不依赖固定前缀，而是全文关键词匹配。
 * 兼容真实世界的各种命名：
 *   deepseek-ai/DeepSeek-V3
 *   accounts/fireworks/models/llama-v3p1-70b
 *   moonshotai/Kimi-K2-Instruct
 *   anthropic/claude-3.5-sonnet
 *   Qwen/Qwen2.5-72B
 *   meta-llama/Llama-3.3-70B
 */
object ModelClassifier {

    private data class Rule(
        val vendor: String,
        val keywords: List<String>,
        val priority: Int = 0
    )

    /**
     * 关键词按从具体到宽泛排列。
     * 匹配时把模型 id 归一化成小写、去分隔符后的连续字符串再找子串。
     */
    private val rules = listOf(
        Rule("DeepSeek", listOf("deepseek"), 10),
        Rule("Moonshot", listOf("moonshot", "kimi"), 10),
        Rule("智谱", listOf("zhipu", "glm", "chatglm", "cogview", "codegeex"), 9),
        Rule("阿里", listOf("qwen", "tongyi", "qwq"), 9),
        Rule("字节", listOf("doubao", "seed", "skylark"), 8),
        Rule("百度", listOf("ernie", "wenxin"), 8),
        Rule("腾讯", listOf("hunyuan"), 8),
        Rule("MiniMax", listOf("minimax", "abab"), 8),
        Rule("讯飞", listOf("spark", "xinghuo"), 7),
        Rule("Anthropic", listOf("claude", "anthropic"), 10),
        Rule("OpenAI", listOf("gpt", "openai", "o1-", "o3-", "o4-", "davinci"), 9),
        Rule("Google", listOf("gemini", "gemma", "palm", "google"), 9),
        Rule("Meta", listOf("llama", "meta"), 9),
        Rule("Mistral", listOf("mistral", "mixtral", "codestral", "pixtral", "magistral"), 9),
        Rule("Cohere", listOf("command", "cohere", "c4ai"), 7),
        Rule("xAI", listOf("grok", "xai"), 9),
        Rule("Microsoft", listOf("phi-", "phi3", "phi4"), 7),
        Rule("Amazon", listOf("nova-", "titan", "amazon"), 6),
        Rule("Nvidia", listOf("nemotron", "nvidia"), 6),
        Rule("01.AI", listOf("yi-", "01-ai"), 6),
        Rule("InternLM", listOf("internlm", "internvl"), 6),
        Rule("Stability", listOf("stablelm", "stablediffusion"), 5),
        Rule("Baichuan", listOf("baichuan"), 6),
        Rule("StepFun", listOf("step-", "stepfun"), 6)
    )

    /** 归一化：小写 + 去掉常见分隔符，便于子串匹配 */
    private fun normalize(id: String): String =
        id.lowercase().replace("-", "").replace("_", "").replace(".", "").replace(" ", "")

    /** 返回厂商名，识别不出返回 null */
    fun vendorOf(modelId: String): String? {
        val n = normalize(modelId)
        // 按优先级排序，命中第一个
        return rules.sortedByDescending { it.priority }
            .firstOrNull { rule -> rule.keywords.any { n.contains(normalize(it)) } }
            ?.vendor
    }

    fun isKnownVendor(modelId: String): Boolean = vendorOf(modelId) != null

    /**
     * 显示名：去掉路径前缀，保留可辨识部分。
     * accounts/fireworks/models/llama-v3p1-70b -> llama-v3p1-70b
     * deepseek-ai/DeepSeek-V3 -> DeepSeek-V3
     */
    fun prettyName(modelId: String): String {
        val seg = modelId.substringAfterLast('/')
        return seg.ifBlank { modelId }
    }

    /** 是否是"重量级"模型（用于区分强弱，辅助排序） */
    fun isHeavy(modelId: String): Boolean {
        val n = normalize(modelId)
        if (n.contains("mini") || n.contains("flash") || n.contains("lite") ||
            n.contains("small") || n.contains("tiny")
        ) return false
        return n.contains("pro") || n.contains("max") || n.contains("opus") ||
            n.contains("sonnet") || n.contains("70b") || n.contains("72b") ||
            n.contains("405b") || n.contains("large") || n.contains("4o")
    }
}
