package com.utsavlens.app.jobs

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.utsavlens.core.AppError
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.EngineIds
import com.utsavlens.core.job.JobKind
import com.utsavlens.core.job.JobRecovery
import com.utsavlens.core.job.JobSnapshot
import com.utsavlens.core.job.JobState
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.data.db.JobEntity
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns every long-running job. Each job is persisted BEFORE it starts, so after process death it
 * is either still listed as running by a live worker or failed cleanly by [reconcileOnStart].
 * The foreground service only keeps the process alive and shows progress; work runs here.
 */
class JobController(
    private val context: Context,
    private val db: AppDatabase,
    private val engines: EngineRegistry,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val workers = ConcurrentHashMap<String, Job>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _live = MutableStateFlow<Map<String, LiveJob>>(emptyMap())
    /** Jobs currently executing in this process. */
    val live: StateFlow<Map<String, LiveJob>> = _live.asStateFlow()

    /** Custom runners for non-transform jobs (video export, model download) are registered by their modules. */
    private val runners = ConcurrentHashMap<JobKind, suspend (jobId: String, requestJson: String, report: (LiveJob) -> Unit) -> List<String>>()

    fun registerRunner(kind: JobKind, runner: suspend (jobId: String, requestJson: String, report: (LiveJob) -> Unit) -> List<String>) {
        runners[kind] = runner
    }

    suspend fun submitTransform(engineId: String, request: TransformRequest): String {
        val jobId = request.jobId.ifEmpty { newJobId() }
        val stored = StoredJob(engineId = engineId, transform = request.copy(jobId = jobId))
        persistAndStart(jobId, JobKind.TRANSFORM, json.encodeToString(StoredJob.serializer(), stored))
        return jobId
    }

    suspend fun submitCustom(kind: JobKind, requestJson: String): String {
        val jobId = newJobId()
        persistAndStart(jobId, kind, requestJson)
        return jobId
    }

    /** Re-runs a finished/failed transform job, optionally on another engine ("Use LITE instead"). */
    suspend fun resubmit(jobId: String, engineIdOverride: String? = null): String? {
        val row = db.jobDao().get(jobId) ?: return null
        if (row.kind != JobKind.TRANSFORM.name) {
            return submitCustom(JobKind.valueOf(row.kind), row.requestJson)
        }
        val stored = decode(row.requestJson) ?: return null
        val request = stored.transform ?: return null
        return submitTransform(engineIdOverride ?: stored.engineId, request.copy(jobId = newJobId()))
    }

    fun cancel(jobId: String) {
        workers[jobId]?.cancel()
    }

    fun newJobId(): String = UUID.randomUUID().toString()

    fun observe(jobId: String): Flow<JobView?> = combine(db.jobDao().observe(jobId), live) { row, liveMap ->
        if (row == null) return@combine null
        val l = liveMap[jobId]
        val stored = if (row.kind == JobKind.TRANSFORM.name) decode(row.requestJson) else null
        val state = runCatching { JobState.valueOf(row.state) }.getOrDefault(JobState.FAILED)
        val failure = row.failureReason?.let { r -> runCatching { AppError.Kind.valueOf(r) }.getOrDefault(AppError.Kind.UNKNOWN) }
        JobView(
            jobId = jobId,
            kind = runCatching { JobKind.valueOf(row.kind) }.getOrDefault(JobKind.TRANSFORM),
            state = state,
            percent = l?.percent ?: row.progress,
            stage = l?.stage,
            step = l?.step ?: 0,
            totalSteps = l?.totalSteps ?: 0,
            preview = l?.preview,
            failure = if (state == JobState.FAILED) failure ?: AppError.Kind.UNKNOWN else null,
            interrupted = state == JobState.FAILED && row.failureReason == INTERRUPTED,
            notes = if (state == JobState.SUCCEEDED) row.failureReason?.split(',').orEmpty() else emptyList(),
            engineId = stored?.engineId.orEmpty(),
            canFallbackToLite = state == JobState.FAILED && stored != null && stored.engineId != EngineIds.LITE,
            outputs = row.outputUris?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
        )
    }

    /** Called once per process start: jobs that were running when the process died are failed cleanly. */
    suspend fun reconcileOnStart() = withContext(dispatchers.io) {
        try {
            val unfinished = db.jobDao().unfinished()
            val snapshots = unfinished.map {
                JobSnapshot(it.id, runCatching { JobKind.valueOf(it.kind) }.getOrDefault(JobKind.TRANSFORM), JobState.valueOf(it.state), it.progress)
            }
            val now = System.currentTimeMillis()
            JobRecovery.reconcile(snapshots, workers.keys.toSet()).filter { it.state == JobState.FAILED }.forEach {
                db.jobDao().update(it.id, JobState.FAILED.name, it.progress, INTERRUPTED, null, now)
            }
            db.jobDao().prune(now - 30L * 24 * 3600 * 1000)
        } catch (e: Exception) {
            logger.warn(TAG, "Job reconcile failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun persistAndStart(jobId: String, kind: JobKind, requestJson: String) {
        val now = System.currentTimeMillis()
        withContext(dispatchers.io) {
            db.jobDao().upsert(JobEntity(jobId, kind.name, requestJson, JobState.RUNNING.name, createdAt = now, updatedAt = now))
        }
        _live.update { it + (jobId to LiveJob(jobId, kind)) }
        startService()
        val worker = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { execute(jobId, kind, requestJson) }
        workers[jobId] = worker
        worker.start()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, JobService::class.java))
        } catch (e: Exception) {
            // Not allowed to start a foreground service right now (app in background): the job
            // still runs, just without the keep-alive notification.
            logger.warn(TAG, "Foreground service not started: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun execute(jobId: String, kind: JobKind, requestJson: String) {
        var lastPersisted = 0
        var terminal: Triple<JobState, String?, List<String>>? = null
        val report: (LiveJob) -> Unit = { l -> _live.update { it + (jobId to l) } }
        try {
            if (kind == JobKind.TRANSFORM) {
                val stored = decode(requestJson)
                val request = stored?.transform
                val engine = stored?.let { engines[it.engineId] }
                if (stored == null || request == null || engine == null) {
                    terminal = Triple(JobState.FAILED, AppError.Kind.INVALID_INPUT.name, emptyList())
                } else {
                    engine.transform(request).collect { p ->
                        when (p) {
                            is TransformProgress.Running -> {
                                report(LiveJob(jobId, kind, p.percent, p.stage, p.step, p.totalSteps, p.preview ?: _live.value[jobId]?.preview))
                                if (p.percent - lastPersisted >= 10) {
                                    lastPersisted = p.percent
                                    db.jobDao().progress(jobId, p.percent, System.currentTimeMillis())
                                }
                            }
                            is TransformProgress.Success -> {
                                recordHistory(jobId, stored, request, p.outputUris)
                                terminal = Triple(JobState.SUCCEEDED, p.notes.joinToString(",") { it.name }.ifEmpty { null }, p.outputUris)
                            }
                            is TransformProgress.Failure -> terminal = Triple(JobState.FAILED, p.error.kind.name, emptyList())
                        }
                    }
                    if (terminal == null) terminal = Triple(JobState.FAILED, AppError.Kind.UNKNOWN.name, emptyList())
                }
            } else {
                val runner = runners[kind]
                terminal = if (runner == null) Triple(JobState.FAILED, AppError.Kind.UNSUPPORTED.name, emptyList())
                else Triple(JobState.SUCCEEDED, null, runner(jobId, requestJson, report))
            }
        } catch (e: CancellationException) {
            terminal = Triple(JobState.CANCELLED, AppError.Kind.CANCELLED.name, emptyList())
        } catch (e: JobFailedException) {
            terminal = Triple(JobState.FAILED, e.kind.name, emptyList())
        } catch (e: OutOfMemoryError) {
            terminal = Triple(JobState.FAILED, AppError.Kind.OUT_OF_MEMORY.name, emptyList())
        } catch (e: Exception) {
            logger.warn(TAG, "Job crashed: ${e.javaClass.simpleName}")
            terminal = Triple(JobState.FAILED, AppError.Kind.UNKNOWN.name, emptyList())
        } finally {
            val (state, reason, outputs) = terminal ?: Triple(JobState.FAILED, AppError.Kind.UNKNOWN.name, emptyList())
            withContext(NonCancellable + dispatchers.io) {
                try {
                    db.jobDao().update(
                        jobId, state.name, if (state == JobState.SUCCEEDED) 100 else lastPersisted,
                        reason, outputs.joinToString("\n").ifEmpty { null }, System.currentTimeMillis(),
                    )
                } catch (e: Exception) {
                    logger.warn(TAG, "Cannot persist job result: ${e.javaClass.simpleName}")
                }
            }
            workers.remove(jobId)
            _live.update { it - jobId }
        }
    }

    private suspend fun recordHistory(jobId: String, stored: StoredJob, request: TransformRequest, outputs: List<String>) {
        if (!request.saveToGallery) return
        val now = System.currentTimeMillis()
        val optionsJson = json.encodeToString(com.utsavlens.engine.api.TransformOptions.serializer(), request.options)
        db.historyDao().insertAll(outputs.map { uri ->
            HistoryEntity(
                sourceUri = request.sourceUri, themeId = request.themeId, engineId = stored.engineId,
                optionsJson = optionsJson, outputUri = uri, mediaType = "PHOTO", batchId = jobId, createdAt = now,
            )
        })
    }

    private fun decode(text: String): StoredJob? = try {
        json.decodeFromString(StoredJob.serializer(), text)
    } catch (_: Exception) {
        null
    }

    /** Thrown by custom runners to fail a job with a specific, user-explainable reason. */
    class JobFailedException(val kind: AppError.Kind, message: String = "") : Exception(message)

    companion object {
        private const val TAG = "JobController"
        const val INTERRUPTED = "CANCELLED_BY_PROCESS_DEATH"
    }
}
