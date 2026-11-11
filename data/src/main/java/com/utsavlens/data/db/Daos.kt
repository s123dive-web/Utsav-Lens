package com.utsavlens.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert suspend fun insert(item: HistoryEntity): Long
    @Insert suspend fun insertAll(items: List<HistoryEntity>)

    @Query("SELECT * FROM history ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE batch_id = :batchId ORDER BY id ASC")
    suspend fun byBatch(batchId: String): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<HistoryEntity>

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>): Int

    @Query("DELETE FROM history")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface JobDao {
    @Upsert suspend fun upsert(job: JobEntity)

    @Query("SELECT * FROM job WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM job WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM job WHERE state IN ('QUEUED','RUNNING')")
    suspend fun unfinished(): List<JobEntity>

    @Query("UPDATE job SET state = :state, progress = :progress, failure_reason = :reason, output_uris = :outputs, updated_at = :now WHERE id = :id")
    suspend fun update(id: String, state: String, progress: Int, reason: String?, outputs: String?, now: Long): Int

    @Query("UPDATE job SET progress = :progress, updated_at = :now WHERE id = :id AND state = 'RUNNING'")
    suspend fun progress(id: String, progress: Int, now: Long): Int

    @Query("DELETE FROM job WHERE state NOT IN ('QUEUED','RUNNING') AND updated_at < :before")
    suspend fun prune(before: Long): Int
}

@Dao
interface CloudUsageDao {
    @Query("SELECT request_count FROM cloud_usage WHERE day = :day AND provider_id = :providerId")
    suspend fun count(day: String, providerId: String): Int?

    @Query("SELECT COALESCE(SUM(request_count), 0) FROM cloud_usage WHERE day = :day")
    fun observeDay(day: String): Flow<Int>

    @Upsert suspend fun upsert(row: CloudUsageEntity)

    @Transaction
    suspend fun increment(day: String, providerId: String): Int {
        val next = (count(day, providerId) ?: 0) + 1
        upsert(CloudUsageEntity(day, providerId, next))
        return next
    }
}

@Dao
interface ModelFileDao {
    @Upsert suspend fun upsert(file: ModelFileEntity)

    @Query("SELECT * FROM model_file WHERE engine_id = :engineId ORDER BY file_name")
    suspend fun forEngine(engineId: String): List<ModelFileEntity>

    @Query("SELECT * FROM model_file WHERE engine_id = :engineId ORDER BY file_name")
    fun observeEngine(engineId: String): Flow<List<ModelFileEntity>>

    @Query("DELETE FROM model_file WHERE engine_id = :engineId")
    suspend fun deleteEngine(engineId: String): Int
}
