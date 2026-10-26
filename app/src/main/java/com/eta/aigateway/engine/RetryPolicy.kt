package com.eta.aigateway.engine

/** 重试参数，可在设置页调整 */
data class RetryPolicy(
    val maxAttempts: Int = 6,              // 同模型最大尝试次数
    val baseBackoffMs: Long = 300,         // 退避基数
    val perModelBudgetMs: Long = 12000,    // 单模型总等待上限
    val globalBudgetMs: Long = 90000       // 整体请求上限（思考模型耗时长，放宽）
) {
    /** 第 n 次（从 1 开始）失败后的等待时长 */
    fun backoffFor(attempt: Int): Long {
        val seq = longArrayOf(300, 600, 1200, 2000, 3000)
        return seq.getOrElse(attempt - 1) { 3000 }
    }

    /**
     * 是否值得重试。
     * 429 是瞬时限流，值得多试；
     * 503/500/502 多半是上游真的挂了，同一来源连续 2 次失败就放弃。
     */
    fun shouldRetry(code: Int): Boolean = when (code) {
        408, 409, 429 -> true
        500, 502, 503, 504, 529 -> true
        else -> false
    }

    /** 同一来源连续同码失败几次就放弃 */
    fun giveUpAfterSameCode(code: Int): Int = when (code) {
        // 上游服务不可用，重试毫无意义，立刻换来源
        500, 502, 503, 504, 529 -> 1
        else -> maxAttempts
    }
}
