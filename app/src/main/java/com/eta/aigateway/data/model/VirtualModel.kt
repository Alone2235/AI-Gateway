package com.eta.aigateway.data.model

/**
 * 虚拟档位：用户不用理解具体模型，按"强弱"选就行。
 * 实际调用的还是已启用的真实模型。
 */
enum class VirtualTier(
    val id: String,
    val label: String,
    val desc: String,
    val sortPriority: Int
) {
    LITE("lite", "Lite", "轻量快答，响应最快", 10),
    PRO("pro", "Pro", "日常主力，均衡", 20),
    PRO_MAX("pro-max", "Pro Max", "复杂任务，能力强", 30),
    ULTRA("ultra", "Ultra", "最强火力，只挑顶级模型", 40);

    companion object {
        fun from(id: String?): VirtualTier? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** 虚拟模型在列表中的展示项 */
data class VirtualEntry(
    val tier: VirtualTier,
    val available: Int,      // 该档位当前可用的真实模型数
    val preview: String      // 预览会用到的代表模型
)
