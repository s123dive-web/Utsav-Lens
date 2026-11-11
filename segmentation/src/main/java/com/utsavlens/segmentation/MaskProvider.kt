package com.utsavlens.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.utsavlens.core.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

interface MaskProvider {
    /** Never throws: on any failure returns an empty mask with [PersonMask.segmenterFailed] set. */
    suspend fun segment(bitmap: Bitmap): PersonMask
    fun close()
}

/**
 * Fully on-device person segmentation + face detection using MediaPipe Tasks with models bundled
 * in this module's assets (selfie_segmenter.tflite, blaze_face_short_range.tflite). No network.
 */
class MediaPipeMaskProvider(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger = AppLogger.NONE,
) : MaskProvider {

    private val mutex = Mutex()
    private var segmenter: ImageSegmenter? = null
    private var faceDetector: FaceDetector? = null
    private var broken = false

    override suspend fun segment(bitmap: Bitmap): PersonMask = withContext(dispatcher) {
        mutex.withLock {
            val work = scaleForModel(bitmap)
            try {
                if (broken) return@withLock PersonMask.empty(work.width, work.height, failed = true)
                ensureLoaded()
                run(work)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logger.warn(TAG, "Segmentation failed: ${t.javaClass.simpleName}")
                if (t is UnsatisfiedLinkError || t is NoClassDefFoundError) broken = true
                PersonMask.empty(work.width, work.height, failed = true)
            } finally {
                if (work !== bitmap) work.recycle()
            }
        }
    }

    private fun ensureLoaded() {
        if (segmenter == null) {
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(SEGMENTER_MODEL).setDelegate(Delegate.CPU).build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build()
            segmenter = ImageSegmenter.createFromOptions(context, options)
        }
        if (faceDetector == null) {
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(FACE_MODEL).setDelegate(Delegate.CPU).build())
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            faceDetector = FaceDetector.createFromOptions(context, options)
        }
    }

    private fun run(work: Bitmap): PersonMask {
        val image = BitmapImageBuilder(work).build()
        val result = segmenter!!.segment(image)
        val masks = result.confidenceMasks().orElse(emptyList())
        if (masks.isEmpty()) return PersonMask.empty(work.width, work.height, failed = true)
        // Single-output models give [person]; two-output models give [background, person].
        val maskImage = if (masks.size >= 2) masks[1] else masks[0]
        val mw = maskImage.width
        val mh = maskImage.height
        val buffer: ByteBuffer = ByteBufferExtractor.extract(maskImage)
        buffer.order(ByteOrder.nativeOrder()).rewind()
        val floats = FloatArray(mw * mh)
        buffer.asFloatBuffer().get(floats)

        var alpha = MaskOps.fromConfidence(floats)
        alpha = MaskOps.tighten(alpha)
        alpha = MaskOps.feather(alpha, mw, mh, radius = max(1, max(mw, mh) / 256))

        val faces = try {
            faceDetector!!.detect(image).detections().map { d ->
                val b = d.boundingBox()
                val sx = mw.toFloat() / work.width
                val sy = mh.toFloat() / work.height
                FaceBox(b.left * sx, b.top * sy, b.right * sx, b.bottom * sy)
            }
        } catch (t: Throwable) {
            logger.warn(TAG, "Face detection failed: ${t.javaClass.simpleName}")
            emptyList()
        }
        return PersonMask(mw, mh, alpha, faces, MaskOps.personFound(alpha, faces.size))
    }

    private fun scaleForModel(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val argb = bitmap.config == Bitmap.Config.ARGB_8888
        if (longest <= MAX_INPUT_EDGE && argb) return bitmap
        val s = if (longest > MAX_INPUT_EDGE) MAX_INPUT_EDGE.toFloat() / longest else 1f
        val scaled = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * s).toInt()), max(1, (bitmap.height * s).toInt()), true)
        if (scaled.config == Bitmap.Config.ARGB_8888) return scaled
        val copy = scaled.copy(Bitmap.Config.ARGB_8888, false)
        if (scaled !== bitmap) scaled.recycle()
        return copy
    }

    /** Releases the native models. Skipped while a segmentation is running; they reload on next use. */
    override fun close() {
        if (!mutex.tryLock()) return
        try {
            try { segmenter?.close() } catch (_: Throwable) {}
            try { faceDetector?.close() } catch (_: Throwable) {}
            segmenter = null
            faceDetector = null
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        private const val TAG = "MaskProvider"
        const val SEGMENTER_MODEL = "selfie_segmenter.tflite"
        const val FACE_MODEL = "blaze_face_short_range.tflite"
        private const val MAX_INPUT_EDGE = 1024
    }
}

/** Bitmap helpers shared by the engines and the mask debug view. */
object MaskBitmaps {
    /** ALPHA_8 bitmap of the mask at mask resolution. */
    fun toAlphaBitmap(mask: PersonMask): Bitmap {
        val bmp = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ALPHA_8)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(mask.alpha))
        return bmp
    }

    /** The person cut out of [source] (transparent elsewhere), at [source] resolution. Person pixels are copied unmodified. */
    fun cutout(source: Bitmap, mask: PersonMask): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        val alpha = toAlphaBitmap(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(alpha, null, Rect(0, 0, source.width, source.height), paint)
        alpha.recycle()
        return out
    }

    /** Debug view: person tinted green, background dimmed, face boxes outlined. */
    fun debugOverlay(source: Bitmap, mask: PersonMask): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawColor(Color.argb(150, 0, 0, 0))
        val person = cutout(source, mask)
        canvas.drawBitmap(person, 0f, 0f, null)
        val tint = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(tint).apply {
            drawColor(Color.argb(90, 0, 230, 118))
            val alpha = toAlphaBitmap(mask)
            drawBitmap(alpha, null, Rect(0, 0, source.width, source.height),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            alpha.recycle()
        }
        canvas.drawBitmap(tint, 0f, 0f, null)
        tint.recycle()
        person.recycle()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, source.width / 200f)
            color = Color.YELLOW
        }
        val sx = source.width.toFloat() / mask.width
        val sy = source.height.toFloat() / mask.height
        mask.faces.forEach { canvas.drawRect(it.left * sx, it.top * sy, it.right * sx, it.bottom * sy, stroke) }
        return out
    }
}
