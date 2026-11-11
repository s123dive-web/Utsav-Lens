package com.utsavlens.app.jobs

import com.utsavlens.core.AppError
import com.utsavlens.core.job.JobKind
import com.utsavlens.core.job.JobState
import com.utsavlens.engine.api.PreviewFrame
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.engine.api.TransformStage
import kotlinx.serialization.Serializable

/** What is persisted in the job table so a job can be shown, retried or failed after process death. */
@Serializable
data class StoredJob(
    val engineId: String = "",
    val transform: TransformRequest? = null,
)

/** In-memory progress of a running job (never persisted). */
data class LiveJob(
    val jobId: String,
    val kind: JobKind,
    val percent: Int = 0,
    val stage: TransformStage = TransformStage.LOADING,
    val step: Int = 0,
    val totalSteps: Int = 0,
    val preview: PreviewFrame? = null,
)

/** What the progress screen renders: persisted state merged with live progress. */
data class JobView(
    val jobId: String,
    val kind: JobKind,
    val state: JobState,
    val percent: Int,
    val stage: TransformStage?,
    val step: Int,
    val totalSteps: Int,
    val preview: PreviewFrame?,
    val failure: AppError.Kind?,
    /** True when the job died with the app process (failed cleanly on next start). */
    val interrupted: Boolean,
    val notes: List<String>,
    val engineId: String,
    val canFallbackToLite: Boolean,
    val outputs: List<String>,
)
