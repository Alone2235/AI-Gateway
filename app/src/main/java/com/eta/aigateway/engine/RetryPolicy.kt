package com.eta.aigateway.engine

/** 重试参数，可在设置页调整 */
data class RetryPolicy(
    val maxAttempts: Int = 6,              // 同模型最大尝试次数
    val baseBackoffMs: Long = 300,         // 退避基数
    val perModelBudgetMs: Long = 7000,     // 单模型总等待上限
    val globalBudgetMs: Long = 25000       // 整体请求上限
) {
    /** 第 n 次（从 1 开始）失败后的等待时长 */
    fun backoffFor(attempt: Int): Long {
        val seq = longArrayOf(300, 600, 1200, 2000, 3000)
        return seq.getOrElse(attempt - 1) { 3000 }
    }

    /** 是否值得重试 */
    fun shouldRetry(code: Int): Boolean = when (code) {
        408, 409, 429, 500, 502, 503, 504, 529 -> true
        else -> false
    }
}
