package com.eta.aigateway.data.db

import androidx.room.*
import com.eta.aigateway.data.model.BalanceSnapshot
import com.eta.aigateway.data.model.UsageRecord

data class ProviderAgg(val providerId: String, val providerName: String, val cnt: Int, val okCnt: Int, val tokens: Int)
data class ModelAgg(val modelId: String, val cnt: Int, val tokens: Int)

@Dao
interface UsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UsageRecord)

    @Query("SELECT * FROM usage_records WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(since: Long, limit: Int): List<UsageRecord>

    @Query("SELECT COUNT(*) FROM usage_records WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM usage_records WHERE timestamp >= :since AND success = 1")
    suspend fun okCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM usage_records WHERE timestamp >= :since AND source = :source")
    suspend fun countBySource(since: Long, source: String): Int

    @Query("SELECT COALESCE(SUM(promptTokens + completionTokens), 0) FROM usage_records WHERE timestamp >= :since")
    suspend fun tokensSince(since: Long): Int

    @Query("""SELECT providerId, providerName, COUNT(*) as cnt,
              SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as okCnt,
              COALESCE(SUM(promptTokens + completionTokens), 0) as tokens
              FROM usage_records WHERE timestamp >= :since
              GROUP BY providerId ORDER BY cnt DESC""")
    suspend fun byProvider(since: Long): List<ProviderAgg>

    @Query("""SELECT modelId, COUNT(*) as cnt,
              COALESCE(SUM(promptTokens + completionTokens), 0) as tokens
              FROM usage_records WHERE timestamp >= :since
              GROUP BY modelId ORDER BY cnt DESC LIMIT 10""")
    suspend fun topModels(since: Long): List<ModelAgg>

    @Query("DELETE FROM usage_records WHERE timestamp < :before")
    suspend fun purgeBefore(before: Long): Int

    @Query("DELETE FROM usage_records")
    suspend fun clearAll()
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balance_snapshots")
    suspend fun all(): List<BalanceSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: BalanceSnapshot)

    @Query("SELECT * FROM balance_snapshots WHERE providerId = :pid")
    suspend fun get(pid: String): BalanceSnapshot?
}
