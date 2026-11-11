package com.utsavlens.engine.local

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.StatFs
import com.utsavlens.core.AppError
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.EngineIds
import com.utsavlens.core.io.OutputNames
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.EngineCapabilities
import com.utsavlens.engine.api.OptionsPolicy
import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.ResultNote
import com.utsavlens.engine.api.TransformEngine
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.engine.api.TransformStage
import com.utsavlens.engine.lite.CropMath
import com.utsavlens.engine.lite.art.GreetingPainter
import com.utsavlens.engine.lite.art.Pal
import com.utsavlens.platform.BitmapIo
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.sd.LatentMath
import com.utsavlens.sd.SdCancelledException
import com.utsavlens.sd.SdModelException
import com.utsavlens.sd.SdPipeline
import com.utsavlens.segmentation.MaskBitmaps
import com.utsavlens.segmentation.MaskOps
import com.utsavlens.segmentation.MaskProvider
import com.utsavlens.segmentation.PersonMask
import com.utsavlens.themes.ThemeLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException

/**
 * LOCAL AI (experimental): on-device Stable Diffusion 1.5-family (LCM) background repainting with
 * ONNX Runtime. The person mask is protected during generation (latent blending) and the original
 * person pixels are pasted back afterwards, so the face is always identical. Fully offline once
 * the model has been downloaded.
 */
class LocalAiEngine(
    private val context: Context,
    private val themes: ThemeLookup,
    private val masks: MaskProvider,
    private val store: ModelStore,
    private val settings: SettingsStore,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger = AppLogger.NONE,
) : TransformEngine {

    override val id: String = EngineIds.LOCAL

    private val caps = EngineCapabilities(
        outfitChange = false, faceAlwaysPreserved = true, maxResolutionPx = 2048,
        needsNetwork = false, needsDownload = true, maxVariations = 2,
    )

    override suspend fun capabilities(): EngineCapabilities = caps

    override suspend fun isReady(): Readiness {
        val present = store.isComplete()
        DeviceGate.check(deviceInfo(), LocalModelManifest.totalBytes, present)?.let { return it }
        return if (present) Readiness.Ready else Readiness.NeedsDownload(LocalModelManifest.totalBytes - store.downloadedBytes())
    }

    fun deviceInfo(): DeviceInfo {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val free = try { StatFs(context.filesDir.path).availableBytes } catch (_: Exception) { 0L }
        return DeviceInfo(
            totalRamBytes = memory.totalMem,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            sdkInt = Build.VERSION.SDK_INT,
            freeStorageBytes = free,
            socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE,
        )
    }

    override fun transform(request: TransformRequest): Flow<TransformProgress> = channelFlow {
        try {
            execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SdCancelledException) {
            throw CancellationException("cancelled")
        } catch (e: OutOfMemoryError) {
            System.gc()
            send(TransformProgress.Failure(AppError(AppError.Kind.OUT_OF_MEMORY, "local ai")))
        } catch (e: SdModelException) {
            logger.warn(TAG, "Local AI model error: ${e.message}")
            send(TransformProgress.Failure(AppError(AppError.Kind.UNSUPPORTED, e.message.orEmpty().take(80))))
        } catch (e: IOException) {
            send(TransformProgress.Failure(AppError(AppError.Kind.IO, e.javaClass.simpleName)))
        } catch (e: Throwable) {
            // Includes UnsatisfiedLinkError if the native runtime cannot load on this device.
            logger.warn(TAG, "Local AI failed: ${e.javaClass.simpleName}")
            send(TransformProgress.Failure(AppError(AppError.Kind.UNSUPPORTED, e.javaClass.simpleName)))
        }
    }.flowOn(dispatchers.default)

    private suspend fun ProducerScope<TransformProgress>.execute(request: TransformRequest) {
        send(TransformProgress.Running(0, TransformStage.LOADING))
        when (val ready = isReady()) {
            Readiness.Ready -> Unit
            is Readiness.Unsupported -> { send(TransformProgress.Failure(AppError(AppError.Kind.UNSUPPORTED, ready.reason.name))); return }
            else -> { send(TransformProgress.Failure(AppError(AppError.Kind.NOT_READY, "model not downloaded"))); return }
        }
        val theme = themes.theme(request.themeId)
        if (theme == null) { send(TransformProgress.Failure(AppError(AppError.Kind.INVALID_INPUT, "unknown theme"))); return }
        val options = OptionsPolicy.resolve(request.options, caps)
        val steps = settings.load().local.steps.coerceIn(2, 8)
        val uri = Uri.parse(request.sourceUri)

        val full = BitmapIo.decodeAny(context, uri, request.outputMaxEdgePx.coerceIn(512, caps.maxResolutionPx))
        var source: Bitmap = full
        try {
            send(TransformProgress.Running(3, TransformStage.SEGMENTING))
            val fullMask = masks.segment(full)
            // Apply the output shape first so generation works on exactly what will be saved.
            val focus = if (fullMask.personFound) CropMath.centroid(fullMask.alpha, fullMask.width, fullMask.height) else floatArrayOf(0.5f, 0.5f)
            val crop = CropMath.cropRect(full.width, full.height, options.aspect.ratio, focus[0], focus[1])
            if (crop[2] != full.width || crop[3] != full.height) {
                source = Bitmap.createBitmap(full, crop[0], crop[1], crop[2], crop[3])
            }
            val mask: PersonMask = if (source === full) fullMask else masks.segment(source)
            val notes = HashSet<ResultNote>()
            if (!mask.personFound) notes += ResultNote.NO_PERSON_FOUND

            val size = LatentMath.generationSize(source.width, source.height)
            val gw = size[0]
            val gh = size[1]
            val small = Bitmap.createScaledBitmap(source, gw, gh, true)
            val chw = toChw(small)
            if (small !== source) small.recycle()
            // No person: repaint everything, but only partially so the photo stays recognisable.
            val repaint = if (mask.personFound) LatentMath.repaintMask(mask.alpha, mask.width, mask.height, gw / 8, gh / 8)
            else FloatArray(gw / 8 * (gh / 8)) { 1f }
            val userStrength = options.strength.coerceIn(0f, 1f)
            val strength = when {
                options.keepBackground -> 0.25f + 0.15f * userStrength
                !mask.personFound -> 0.4f + 0.25f * userStrength
                else -> 0.6f + 0.4f * userStrength
            }
            val prompt = buildString {
                append(options.promptOverride ?: theme.prompt.positive)
                append(", high detail, sharp focus")
            }

            val outputs = ArrayList<String>(options.variations)
            for (v in 0 until options.variations) {
                currentCoroutineContext().ensureActive()
                val pipeline = SdPipeline(store.dir)
                // If the collector goes away mid-step, ask ONNX Runtime to abort the running pass.
                val canceller = launch { try { awaitCancellation() } finally { pipeline.cancel() } }
                val span = 90 / options.variations
                val base = 5 + v * span
                val generated = try {
                    runInterruptible {
                        pipeline.generate(SdPipeline.Request(chw, gw, gh, repaint, prompt, steps = steps, strength = strength, seed = request.seed + v * 7919L)) { stage, step, total ->
                            val (percent, stageEnum) = when (stage) {
                                "text" -> base to TransformStage.LOADING
                                "encode" -> base + span / 10 to TransformStage.LOADING
                                "denoise" -> base + span / 10 + (span * 7 / 10) * (step - 1) / total.coerceAtLeast(1) to TransformStage.GENERATING
                                else -> base + span * 8 / 10 to TransformStage.COMPOSITING
                            }
                            trySend(TransformProgress.Running(percent, stageEnum, step = if (stage == "denoise") step else 0, totalSteps = if (stage == "denoise") total else 0))
                        }
                    }
                } finally {
                    canceller.cancel()
                }
                send(TransformProgress.Running(base + span * 9 / 10, TransformStage.SAVING))
                val result = compose(source, mask, generated, gw, gh)
                try {
                    options.greeting?.let {
                        GreetingPainter.draw(Canvas(result), result.width.toFloat(), result.height.toFloat(), it.text, Pal.of(theme.palette), minOf(result.width, result.height) * 0.04f)
                    }
                    val write: (java.io.OutputStream) -> Unit = { out ->
                        if (!result.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw IOException("JPEG encode failed")
                    }
                    outputs += if (request.saveToGallery) {
                        writer.saveImage(OutputNames.build(theme.id + "_ai", "jpg", index = v), "image/jpeg", write).uri.toString()
                    } else {
                        writer.saveToCache("previews", "${request.jobId}_${theme.id}_ai_$v.jpg", write).toString()
                    }
                } finally {
                    result.recycle()
                }
            }
            send(TransformProgress.Running(100, TransformStage.SAVING))
            send(TransformProgress.Success(outputs, notes))
        } finally {
            if (source !== full) source.recycle()
            full.recycle()
        }
    }

    /** Upscales the generated image to the output size and pastes the untouched person back on top. */
    private fun compose(source: Bitmap, mask: PersonMask, generated: FloatArray, gw: Int, gh: Int): Bitmap {
        val pixels = IntArray(gw * gh)
        val plane = gw * gh
        for (i in 0 until plane) {
            val r = ((generated[i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((generated[plane + i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((generated[2 * plane + i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val small = Bitmap.createBitmap(pixels, gw, gh, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(small, null, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        small.recycle()
        if (mask.personFound) {
            // Slightly feathered so the generated surroundings blend into the real person edge.
            val soft = PersonMask(mask.width, mask.height, MaskOps.feather(mask.alpha, mask.width, mask.height, 1), mask.faces, true)
            val person = MaskBitmaps.cutout(source, soft)
            canvas.drawBitmap(person, 0f, 0f, null)
            person.recycle()
        }
        return out
    }

    private fun toChw(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * w * h)
        val plane = w * h
        for (i in 0 until plane) {
            val p = px[i]
            out[i] = ((p shr 16) and 0xFF) / 127.5f - 1f
            out[plane + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
            out[2 * plane + i] = (p and 0xFF) / 127.5f - 1f
        }
        return out
    }

    companion object {
        private const val TAG = "LocalAiEngine"
    }
}
