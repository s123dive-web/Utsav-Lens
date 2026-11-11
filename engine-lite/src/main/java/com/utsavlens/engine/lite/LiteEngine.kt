package com.utsavlens.engine.lite

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.utsavlens.core.AppError
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.EngineIds
import com.utsavlens.core.io.OutputNames
import com.utsavlens.engine.api.EngineCapabilities
import com.utsavlens.engine.api.OptionsPolicy
import com.utsavlens.engine.api.PreviewFrame
import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.ResultNote
import com.utsavlens.engine.api.TransformEngine
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.engine.api.TransformStage
import com.utsavlens.platform.BitmapIo
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.segmentation.MaskProvider
import com.utsavlens.segmentation.PersonMask
import com.utsavlens.themes.ThemeLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Offline engine: segmentation + compositing. Always ready, needs no network and no download. */
class LiteEngine(
    private val context: Context,
    private val themes: ThemeLookup,
    private val masks: MaskProvider,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger = AppLogger.NONE,
) : TransformEngine {

    override val id: String = EngineIds.LITE

    private val caps = EngineCapabilities(
        outfitChange = false, faceAlwaysPreserved = true, maxResolutionPx = MAX_EDGE,
        needsNetwork = false, needsDownload = false, maxVariations = 4,
    )

    private val cacheLock = Mutex()
    private var cachedMaskUri: String? = null
    private var cachedMask: PersonMask? = null
    private var cachedSmallKey: String? = null
    private var cachedSmall: Bitmap? = null

    /** Drops cached bitmaps and masks (called when the system reports memory pressure). */
    suspend fun clearCaches() = cacheLock.withLock {
        cachedMask = null
        cachedMaskUri = null
        cachedSmall = null
        cachedSmallKey = null
    }

    override suspend fun capabilities(): EngineCapabilities = caps
    override suspend fun isReady(): Readiness = Readiness.Ready

    override fun transform(request: TransformRequest): Flow<TransformProgress> = flow {
        try {
            run(request, request.outputMaxEdgePx.coerceIn(64, MAX_EDGE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            System.gc()
            emit(TransformProgress.Failure(AppError(AppError.Kind.OUT_OF_MEMORY, "lite render"), canFallbackToLite = false))
        } catch (e: IOException) {
            logger.warn(TAG, "LITE failed: ${e.javaClass.simpleName}")
            emit(TransformProgress.Failure(AppError(AppError.Kind.IO, e.javaClass.simpleName), canFallbackToLite = false))
        } catch (e: Exception) {
            logger.warn(TAG, "LITE failed: ${e.javaClass.simpleName}")
            emit(TransformProgress.Failure(AppError(AppError.Kind.UNKNOWN, e.javaClass.simpleName), canFallbackToLite = false))
        }
    }.flowOn(dispatchers.default)

    private suspend fun FlowCollector<TransformProgress>.run(request: TransformRequest, maxEdge: Int) {
        emit(TransformProgress.Running(0, TransformStage.LOADING))
        val theme = themes.theme(request.themeId)
        if (theme == null) {
            emit(TransformProgress.Failure(AppError(AppError.Kind.INVALID_INPUT, "unknown theme"), canFallbackToLite = false))
            return
        }
        val options = OptionsPolicy.resolve(request.options, caps)
        val uri = Uri.parse(request.sourceUri)
        val notes = HashSet<ResultNote>()

        var edge = maxEdge
        var source: Bitmap? = null
        // Low-memory path: halve the working size (down to 512 px) instead of failing.
        while (source == null) {
            try {
                source = loadSource(uri, request.sourceUri, edge)
            } catch (e: OutOfMemoryError) {
                System.gc()
                if (edge <= 512) throw e
                edge /= 2
                notes += ResultNote.DOWNSCALED
            }
        }
        currentCoroutineContext().ensureActive()
        emit(TransformProgress.Running(15, TransformStage.SEGMENTING))
        val mask = maskFor(request.sourceUri, uri, source)
        if (!mask.personFound) notes += ResultNote.NO_PERSON_FOUND

        val outputs = ArrayList<String>(options.variations)
        val keepSource = source === cachedSmall
        try {
            for (i in 0 until options.variations) {
                currentCoroutineContext().ensureActive()
                val base = 30 + i * 70 / options.variations
                emit(TransformProgress.Running(base, TransformStage.COMPOSITING, step = i + 1, totalSteps = options.variations))
                val result = LiteRenderer.render(source, mask, theme, options, request.seed + i * 7919L)
                try {
                    emit(TransformProgress.Running(base + 50 / options.variations, TransformStage.SAVING, preview = preview(result.bitmap), step = i + 1, totalSteps = options.variations))
                    outputs += save(request, theme.id, i, result.bitmap)
                } finally {
                    result.bitmap.recycle()
                }
            }
        } finally {
            if (!keepSource) source.recycle()
        }
        emit(TransformProgress.Running(100, TransformStage.SAVING))
        emit(TransformProgress.Success(outputs, notes))
    }

    private suspend fun loadSource(uri: Uri, key: String, edge: Int): Bitmap {
        if (edge > SMALL_EDGE) return BitmapIo.decodeAny(context, uri, edge)
        return cacheLock.withLock {
            val k = "$key@$edge"
            val cached = cachedSmall
            if (cachedSmallKey == k && cached != null && !cached.isRecycled) cached
            else BitmapIo.decodeAny(context, uri, edge).also {
                cachedSmall = it
                cachedSmallKey = k
            }
        }
    }

    /** The mask is computed once per source (from a <=1024 px copy) and reused for thumbnails and variations. */
    private suspend fun maskFor(key: String, uri: Uri, source: Bitmap): PersonMask = cacheLock.withLock {
        cachedMask?.takeIf { cachedMaskUri == key }?.let { return@withLock it }
        val forMask = if (maxOf(source.width, source.height) >= 512) source else try {
            BitmapIo.decodeAny(context, uri, 768)
        } catch (_: Exception) {
            source
        }
        val mask = masks.segment(forMask)
        if (forMask !== source) forMask.recycle()
        if (!mask.segmenterFailed) {
            cachedMask = mask
            cachedMaskUri = key
        }
        mask
    }

    private fun save(request: TransformRequest, themeId: String, index: Int, bitmap: Bitmap): String {
        val write: (java.io.OutputStream) -> Unit = { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, if (request.saveToGallery) 95 else 85, out)) throw IOException("JPEG encode failed")
        }
        return if (request.saveToGallery) {
            writer.saveImage(OutputNames.build(themeId, "jpg", index = index), "image/jpeg", write).uri.toString()
        } else {
            writer.saveToCache("previews", "${request.jobId}_${themeId}_$index.jpg", write).toString()
        }
    }

    private fun preview(bitmap: Bitmap): PreviewFrame? = try {
        val longest = maxOf(bitmap.width, bitmap.height)
        val s = if (longest > 256) 256f / longest else 1f
        val small = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * s).toInt()), maxOf(1, (bitmap.height * s).toInt()), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val frame = PreviewFrame(small.width, small.height, px)
        if (small !== bitmap) small.recycle()
        frame
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val TAG = "LiteEngine"
        const val MAX_EDGE = 3072
        /** Sources at or below this size are cached for thumbnail rendering. */
        const val SMALL_EDGE = 512
    }
}
