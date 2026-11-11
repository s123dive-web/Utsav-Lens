package com.utsavlens.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.io.OutputNames
import com.utsavlens.engine.lite.LiteRenderer
import com.utsavlens.engine.lite.art.Pal
import com.utsavlens.engine.lite.art.alpha
import com.utsavlens.engine.lite.art.darken
import com.utsavlens.platform.MediaStoreWriter
import com.utsavlens.themes.Theme
import com.utsavlens.themes.ThemeLookup
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoExportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Video export with Media3 Transformer. Nothing here is generative: slideshows are built from
 * photos the user already transformed, and clips only get border / overlays / grade / greeting
 * drawn by the same procedural artwork the LITE engine uses.
 */
@OptIn(UnstableApi::class)
class VideoExporter(
    private val context: Context,
    private val themes: ThemeLookup,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
) {
    /** Returns the URI of the saved video (Movies/UtsavLens). [onProgress] gets 0..100. */
    suspend fun exportSlideshow(jobId: String, spec: SlideshowSpec, onProgress: (Int) -> Unit): String {
        val theme = themes.theme(spec.themeId) ?: throw VideoExportException("unknown theme")
        val photos = spec.photoUris.take(SlideshowTimeline.MAX_PHOTOS)
        if (photos.isEmpty()) throw VideoExportException("no photos")
        val timeline = SlideshowTimeline(photos.size, spec.secondsPerPhoto)
        val (width, height) = SlideshowTimeline.outputSize(spec.aspect)

        val items = photos.map { uri ->
            val media = MediaItem.Builder().setUri(Uri.parse(uri)).setImageDurationMs(timeline.slideUs / 1000).build()
            val zoom = MatrixTransformation { timeUs ->
                val z = timeline.zoom(timeUs)
                Matrix().apply { postScale(z, z) }
            }
            EditedMediaItem.Builder(media)
                .setDurationUs(timeline.slideUs)
                .setFrameRate(FRAME_RATE)
                .setEffects(Effects(emptyList<AudioProcessor>(), listOf<Effect>(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT), zoom)))
                .build()
        }
        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItems(items).build()
        spec.audioUri?.takeIf { it.isNotBlank() }?.let { audio ->
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(audio))).setRemoveVideo(true).build()
            // Looping: the user's track repeats (or is cut) to match the slideshow length.
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(audioItem).setIsLooping(true).build()
        }
        val overlay = SlideshowOverlay(theme, timeline, spec.greeting, spec.seed)
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList<AudioProcessor>(), listOf<Effect>(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))))
            .build()
        return runExport(jobId, theme.id, composition, onProgress)
    }

    suspend fun exportOverlay(jobId: String, spec: VideoOverlaySpec, onProgress: (Int) -> Unit): String {
        val theme = themes.theme(spec.themeId) ?: throw VideoExportException("unknown theme")
        val strength = spec.strength.coerceIn(0f, 1f)
        val effects = ArrayList<Effect>()
        val g = theme.lite.grade
        val warm = g.warmth * strength
        if (warm != 0f) effects += RgbAdjustment.Builder().setRedScale(1f + 0.14f * warm).setGreenScale(1f + 0.03f * warm).setBlueScale(1f - 0.14f * warm).build()
        val contrast = ((g.contrast - 1f) * strength).coerceIn(-1f, 1f)
        if (contrast != 0f) effects += Contrast(contrast)
        val saturation = ((g.saturation - 1f) * strength * 100f).coerceIn(-100f, 100f)
        if (saturation != 0f) effects += HslAdjustment.Builder().adjustSaturation(saturation).build()
        effects += OverlayEffect(ImmutableList.of<TextureOverlay>(DecorationOverlay(theme, strength, spec.greeting, spec.seed)))

        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(spec.sourceUri)))
            .setEffects(Effects(emptyList<AudioProcessor>(), effects))
            .build()
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItem(item).build()
        return runExport(jobId, theme.id, Composition.Builder(listOf(sequence)).build(), onProgress)
    }

    private suspend fun runExport(jobId: String, themeId: String, composition: Composition, onProgress: (Int) -> Unit): String {
        val dir = File(context.cacheDir, "video").apply { mkdirs() }
        val temp = File(dir, "$jobId.mp4")
        temp.delete()
        try {
            // Transformer must be created, started and polled on one Looper thread: the main thread.
            withContext(dispatchers.main) {
                val progressHolder = ProgressHolder()
                var transformer: Transformer? = null
                val poller = launch {
                    while (isActive) {
                        val t = transformer
                        if (t != null && t.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((progressHolder.progress * 0.95f).toInt().coerceIn(0, 95))
                        }
                        delay(400)
                    }
                }
                try {
                    suspendCancellableCoroutine<Unit> { cont ->
                        val listener = object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resumeWithException(VideoExportException("export failed: ${exportException.errorCodeName}", exportException))
                            }
                        }
                        val t = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(listener)
                            .build()
                        transformer = t
                        cont.invokeOnCancellation { runCatching { t.cancel() } }
                        t.start(composition, temp.absolutePath)
                    }
                } finally {
                    poller.cancel()
                }
            }
            val saved = withContext(dispatchers.io) { writer.saveVideo(OutputNames.build(themeId, "mp4"), temp) }
            onProgress(100)
            return saved.uri.toString()
        } finally {
            temp.delete()
        }
    }

    /** Full theme decorations over a user clip. Redrawn at ~15 fps to keep exports fast. */
    private class DecorationOverlay(
        private val theme: Theme,
        private val strength: Float,
        private val greeting: String?,
        private val seed: Long,
    ) : CanvasOverlay(/* useInputFrameSize = */ true) {
        private var lastDrawUs = Long.MIN_VALUE

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            if (lastDrawUs != Long.MIN_VALUE && presentationTimeUs - lastDrawUs in 0 until REDRAW_INTERVAL_US) return
            lastDrawUs = presentationTimeUs
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val t = presentationTimeUs / 1_000_000f
            LiteRenderer.drawDecorations(canvas, canvas.width, canvas.height, theme, strength, seed, t + 0.001f, greeting, greetingOpacity = t.coerceIn(0f, 1f))
        }
    }

    /** Animated particles, greeting and dip-to-colour transitions for the slideshow. */
    private class SlideshowOverlay(
        private val theme: Theme,
        private val timeline: SlideshowTimeline,
        private val greeting: String?,
        private val seed: Long,
    ) : CanvasOverlay(/* useInputFrameSize = */ true) {
        private val pal = Pal.of(theme.palette)
        private val fade = Paint()
        private var lastDrawUs = Long.MIN_VALUE

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            val transition = timeline.transitionAlpha(presentationTimeUs)
            // Transitions need every frame; the rest is redrawn at ~15 fps.
            if (transition == 0f && lastDrawUs != Long.MIN_VALUE && presentationTimeUs - lastDrawUs in 0 until REDRAW_INTERVAL_US) return
            lastDrawUs = presentationTimeUs
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val t = presentationTimeUs / 1_000_000f
            LiteRenderer.drawSlideshowDecorations(canvas, canvas.width, canvas.height, theme, seed, t + 0.001f, greeting, timeline.greetingAlpha(presentationTimeUs))
            if (transition > 0f) {
                fade.color = alpha(darken(pal.background, 0.5f), transition)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fade)
            }
        }
    }

    companion object {
        const val FRAME_RATE = 30
        private const val REDRAW_INTERVAL_US = 66_000L
    }
}
