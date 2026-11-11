package com.utsavlens.app.jobs

import com.utsavlens.core.AppError
import com.utsavlens.core.job.JobKind
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.engine.api.TransformStage
import com.utsavlens.video.SlideshowSpec
import com.utsavlens.video.VideoExportException
import com.utsavlens.video.VideoExporter
import com.utsavlens.video.VideoOverlaySpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Registers the video export runners with [JobController] so exports run in the foreground service with progress and cancel. */
class VideoJobs(
    private val controller: JobController,
    private val exporter: VideoExporter,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        controller.registerRunner(JobKind.SLIDESHOW) { jobId, requestJson, report ->
            val spec = decode { json.decodeFromString(SlideshowSpec.serializer(), requestJson) }
            val uri = guarded {
                exporter.exportSlideshow(jobId, spec) { p -> report(LiveJob(jobId, JobKind.SLIDESHOW, p, TransformStage.GENERATING)) }
            }
            record(jobId, spec.photoUris.firstOrNull().orEmpty(), spec.themeId, requestJson, uri)
            listOf(uri)
        }
        controller.registerRunner(JobKind.VIDEO_OVERLAY) { jobId, requestJson, report ->
            val spec = decode { json.decodeFromString(VideoOverlaySpec.serializer(), requestJson) }
            val uri = guarded {
                exporter.exportOverlay(jobId, spec) { p -> report(LiveJob(jobId, JobKind.VIDEO_OVERLAY, p, TransformStage.GENERATING)) }
            }
            record(jobId, spec.sourceUri, spec.themeId, requestJson, uri)
            listOf(uri)
        }
    }

    suspend fun startSlideshow(spec: SlideshowSpec): String =
        controller.submitCustom(JobKind.SLIDESHOW, json.encodeToString(SlideshowSpec.serializer(), spec))

    suspend fun startOverlay(spec: VideoOverlaySpec): String =
        controller.submitCustom(JobKind.VIDEO_OVERLAY, json.encodeToString(VideoOverlaySpec.serializer(), spec))

    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw JobController.JobFailedException(AppError.Kind.INVALID_INPUT)
    }

    private inline fun guarded(block: () -> String): String = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: VideoExportException) {
        throw JobController.JobFailedException(AppError.Kind.UNSUPPORTED, e.message.orEmpty())
    } catch (e: java.io.IOException) {
        throw JobController.JobFailedException(AppError.Kind.IO)
    }

    private suspend fun record(jobId: String, sourceUri: String, themeId: String, requestJson: String, outputUri: String) {
        db.historyDao().insert(
            HistoryEntity(
                sourceUri = sourceUri, themeId = themeId, engineId = "video", optionsJson = requestJson,
                outputUri = outputUri, mediaType = "VIDEO", batchId = jobId, createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
