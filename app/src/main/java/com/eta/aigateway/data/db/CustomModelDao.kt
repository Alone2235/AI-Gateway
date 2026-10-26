package com.eta.aigateway.data.db

import androidx.room.*
import com.eta.aigateway.data.model.CustomModel

@Dao
interface CustomModelDao {
    @Query("SELECT * FROM custom_models ORDER BY createdAt ASC")
    suspend fun all(): List<CustomModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: CustomModel)

    @Query("DELETE FROM custom_models WHERE id = :id")
    suspend fun delete(id: String)
}
