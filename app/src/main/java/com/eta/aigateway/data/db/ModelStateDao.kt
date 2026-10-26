package com.eta.aigateway.data.db

import androidx.room.*
import com.eta.aigateway.data.model.ModelState
import com.eta.aigateway.data.model.SourceState

@Dao
interface ModelStateDao {
    @Query("SELECT * FROM model_states")
    suspend fun allGroups(): List<ModelState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ModelState)

    @Query("DELETE FROM model_states WHERE modelId = :id")
    suspend fun deleteGroup(id: String)

    @Query("SELECT * FROM source_states")
    suspend fun allSources(): List<SourceState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(s: SourceState)
}
