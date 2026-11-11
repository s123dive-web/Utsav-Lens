package com.utsavlens.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved output. Deleting a row never touches [sourceUri] (the user's original). */
@Entity(tableName = "history", indices = [Index("created_at"), Index("batch_id")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    @ColumnInfo(name = "engine_id") val engineId: String,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    @ColumnInfo(name = "output_uri") val outputUri: String,
    /** PHOTO or VIDEO. */
    @ColumnInfo(name = "media_type") val mediaType: String,
    /** Groups the variations produced by one job. */
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Persisted state of a long-running job so it survives process death (restored or cleanly failed). */
@Entity(tableName = "job", indices = [Index("state")])
data class JobEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    val state: String,
    val progress: Int = 0,
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    /** Newline-separated output URIs once the job succeeded. */
    @ColumnInfo(name = "output_uris") val outputUris: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "cloud_usage", primaryKeys = ["day", "provider_id"])
data class CloudUsageEntity(
    /** Local date, ISO yyyy-MM-dd. */
    val day: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "request_count") val requestCount: Int,
)

@Entity(tableName = "model_file")
data class ModelFileEntity(
    /** "<engineId>/<fileName>". */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "engine_id") val engineId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val url: String,
    val sha256: String,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    /** PENDING, DOWNLOADING, VERIFIED or FAILED. */
    val state: String = "PENDING",
)
