package com.eta.aigateway.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一次调用记录。source: app（App 内聊天）/ api（外部客户端） */
@Entity(tableName = "usage_records", indices = [androidx.room.Index("timestamp")])
data class UsageRecord(
    @PrimaryKey val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String,
    val modelRequested: String,
    val providerId: String,
    val providerName: String,
    val source: String = "app",
    val success: Boolean,
    val httpCode: Int = 0,
    val errorMsg: String = "",
    val latencyMs: Long = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val fallbackDepth: Int = 0
)

/** 余额快照 */
@Entity(tableName = "balance_snapshots")
data class BalanceSnapshot(
    @PrimaryKey val providerId: String,
    val providerName: String,
    val supported: Boolean,
    val totalCredits: Double = 0.0,
    val usedCredits: Double = 0.0,
    val remaining: Double = 0.0,
    val currency: String = "USD",
    val rawInfo: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String = ""
)
