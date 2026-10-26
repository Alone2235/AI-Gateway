package com.eta.aigateway.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 模型组的启用状态与标签，按 modelId 存 */
@Entity(tableName = "model_states")
data class ModelState(
    @PrimaryKey val modelId: String,
    val enabled: Boolean = false,
    val tags: String = "",          // 逗号分隔：fast,smart,long
    val displayName: String = ""
)

/** 单个来源的启用状态 */
@Entity(tableName = "source_states")
data class SourceState(
    @PrimaryKey val key: String,    // providerId|modelId
    val enabled: Boolean = true
)
