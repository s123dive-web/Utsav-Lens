package com.utsavlens.engine.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.engine.lite.art.ArtScope
import com.utsavlens.engine.lite.art.Backgrounds
import com.utsavlens.engine.lite.art.Borders
import com.utsavlens.engine.lite.art.GreetingPainter
import com.utsavlens.engine.lite.art.Overlays
import com.utsavlens.engine.lite.art.Pal
import com.utsavlens.engine.lite.art.Particles
import com.utsavlens.engine.lite.art.alpha
import com.utsavlens.segmentation.MaskBitmaps
import com.utsavlens.segmentation.PersonMask
import com.utsavlens.themes.GradeSpec
import com.utsavlens.themes.Theme
import kotlin.math.max
import kotlin.math.min

/**
 * The LITE compositor. No generative model: themed background, overlays, grade, particles,
 * border and greeting are drawn around the person.
 *
 * Guarantee: pixels of the person layer are copied from the source and never colour-graded,
 * filtered or repainted. Decorations may overlap the body near the frame edges, but face
 * rectangles are re-drawn last from the untouched person layer, so faces are always identical.
 */
object LiteRenderer {

    class Result(val bitmap: Bitmap, val personFound: Boolean)

    /**
     * @param source decoded original at output scale (read only; not recycled here)
     * @param mask person mask for the whole source, or null to treat the image as "no person"
     * @param t animation time in seconds (0 for stills)
     */
    fun render(source: Bitmap, mask: PersonMask?, theme: Theme, options: TransformOptions, seed: Long, t: Float = 0f): Result {
        val personFound = mask != null && mask.personFound
        val focus = if (personFound) CropMath.centroid(mask!!.alpha, mask.width, mask.height) else floatArrayOf(0.5f, 0.5f)
        val crop = CropMath.cropRect(source.width, source.height, options.aspect.ratio, focus[0], focus[1])
        val cw = crop[2]
        val ch = crop[3]
        val srcRect = Rect(crop[0], crop[1], crop[0] + cw, crop[1] + ch)
        val dst = Rect(0, 0, cw, ch)
        val strength = options.strength.coerceIn(0f, 1f)
        val pal = Pal.of(theme.palette)
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        // 1. Background: the graded original, optionally covered by the procedural theme background.
        bmpPaint.colorFilter = gradeFilter(theme.lite.grade, strength)
        canvas.drawBitmap(source, srcRect, dst, bmpPaint)
        bmpPaint.colorFilter = null
        if (personFound && !options.keepBackground) {
            val layerAlpha = (255 * (0.4f + 0.6f * strength)).toInt().coerceIn(0, 255)
            val layer = canvas.saveLayerAlpha(0f, 0f, cw.toFloat(), ch.toFloat(), layerAlpha)
            Backgrounds.draw(ArtScope(canvas, cw.toFloat(), ch.toFloat(), pal, seed, t), theme.lite.background.style, theme.lite.background.intensity)
            canvas.restoreToCount(layer)
        }
        vignette(canvas, cw, ch, theme.lite.grade.vignette * strength)

        // 2. Decorations behind the person.
        val decoOpacity = 0.45f + 0.55f * strength
        theme.lite.overlays.forEachIndexed { i, spec ->
            if (!Overlays.isFront(spec)) Overlays.draw(ArtScope(canvas, cw.toFloat(), ch.toFloat(), pal, seed, t, spec.opacity * decoOpacity), spec, i)
        }

        // 3. The person, untouched.
        var person: Bitmap? = null
        val faces = ArrayList<RectF>()
        if (personFound) {
            val m = mask!!
            val alphaBmp = MaskBitmaps.toAlphaBitmap(m)
            val sx = m.width.toFloat() / source.width
            val sy = m.height.toFloat() / source.height
            val maskSrc = Rect(
                (crop[0] * sx).toInt(), (crop[1] * sy).toInt(),
                min(m.width, ((crop[0] + cw) * sx + 0.5f).toInt()), min(m.height, ((crop[1] + ch) * sy + 0.5f).toInt()),
            )
            if (theme.lite.grade.glow > 0f) rimGlow(canvas, alphaBmp, maskSrc, dst, pal.accent, theme.lite.grade.glow * strength)
            person = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val pc = Canvas(person)
            pc.drawBitmap(source, srcRect, dst, null)
            val cut = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            pc.drawBitmap(alphaBmp, maskSrc, dst, cut)
            alphaBmp.recycle()
            canvas.drawBitmap(person, 0f, 0f, null)
            for (f in m.faces) {
                val r = RectF(f.left / sx - crop[0], f.top / sy - crop[1], f.right / sx - crop[0], f.bottom / sy - crop[1])
                r.inset(-r.width() * 0.25f, -r.height() * 0.3f)
                if (r.intersect(0f, 0f, cw.toFloat(), ch.toFloat())) faces += r
            }
        }

        // 4. Decorations in front, then the faces are restored from the untouched person layer.
        drawFront(canvas, cw.toFloat(), ch.toFloat(), theme, pal, seed, t, decoOpacity, strength, faces)
        if (person != null) {
            for (f in faces) {
                canvas.save()
                canvas.clipRect(f)
                canvas.drawBitmap(person, 0f, 0f, null)
                canvas.restore()
            }
            person.recycle()
        }

        // 5. Border and greeting.
        val scope = ArtScope(canvas, cw.toFloat(), ch.toFloat(), pal, seed, t)
        Borders.draw(scope, theme.lite.border.style, theme.lite.border.widthPct)
        options.greeting?.let {
            GreetingPainter.draw(canvas, cw.toFloat(), ch.toFloat(), it.text, pal, bottomInset = borderInset(theme, scope.u))
        }
        return Result(out, personFound)
    }

    /**
     * Everything except the photo itself, drawn on a transparent canvas. Used for video overlays
     * (called once per frame with the frame time) so that video uses the same artwork as photos.
     */
    fun drawDecorations(canvas: Canvas, w: Int, h: Int, theme: Theme, strength: Float, seed: Long, t: Float, greeting: String?, greetingOpacity: Float = 1f) {
        val pal = Pal.of(theme.palette)
        val s = strength.coerceIn(0f, 1f)
        val decoOpacity = 0.45f + 0.55f * s
        theme.lite.overlays.forEachIndexed { i, spec ->
            if (!Overlays.isFront(spec)) Overlays.draw(ArtScope(canvas, w.toFloat(), h.toFloat(), pal, seed, t, spec.opacity * decoOpacity * 0.9f), spec, i)
        }
        drawFront(canvas, w.toFloat(), h.toFloat(), theme, pal, seed, t, decoOpacity, s, emptyList())
        val scope = ArtScope(canvas, w.toFloat(), h.toFloat(), pal, seed, t)
        Borders.draw(scope, theme.lite.border.style, theme.lite.border.widthPct)
        if (!greeting.isNullOrBlank() && greetingOpacity > 0f) {
            GreetingPainter.draw(canvas, w.toFloat(), h.toFloat(), greeting, pal, borderInset(theme, scope.u), greetingOpacity)
        }
    }

    /** Lighter variant for slideshows of already-themed photos: animated particles and the greeting only. */
    fun drawSlideshowDecorations(canvas: Canvas, w: Int, h: Int, theme: Theme, seed: Long, t: Float, greeting: String?, greetingOpacity: Float) {
        val pal = Pal.of(theme.palette)
        val type = if (theme.lite.particles.type == com.utsavlens.themes.ParticleType.NONE) com.utsavlens.themes.ParticleType.SPARKLE else theme.lite.particles.type
        Particles.draw(ArtScope(canvas, w.toFloat(), h.toFloat(), pal, seed, t, 0.9f), type, theme.lite.particles.density.coerceAtLeast(0.4f))
        if (!greeting.isNullOrBlank() && greetingOpacity > 0f) {
            GreetingPainter.draw(canvas, w.toFloat(), h.toFloat(), greeting, pal, minOf(w, h) * 0.06f, greetingOpacity)
        }
    }

    private fun drawFront(canvas: Canvas, w: Float, h: Float, theme: Theme, pal: Pal, seed: Long, t: Float, decoOpacity: Float, strength: Float, faces: List<RectF>) {
        theme.lite.overlays.forEachIndexed { i, spec ->
            if (Overlays.isFront(spec)) Overlays.draw(ArtScope(canvas, w, h, pal, seed, t, spec.opacity * decoOpacity), spec, i)
        }
        Particles.draw(ArtScope(canvas, w, h, pal, seed, t, decoOpacity), theme.lite.particles.type, theme.lite.particles.density * (0.4f + 0.6f * strength), faces)
    }

    private fun borderInset(theme: Theme, u: Float): Float =
        if (theme.lite.border.style == com.utsavlens.themes.BorderStyle.NONE) u * 0.02f else u * (theme.lite.border.widthPct / 100f + 0.02f)

    /** Colour grade for the BACKGROUND only; [amount] scales it from none (0) to the theme value (1). */
    fun gradeFilter(grade: GradeSpec, amount: Float): ColorMatrixColorFilter? {
        val a = amount.coerceIn(0f, 1f)
        val sat = 1f + (grade.saturation - 1f) * a
        val con = 1f + (grade.contrast - 1f) * a
        val warm = grade.warmth * a
        if (sat == 1f && con == 1f && warm == 0f) return null
        val m = ColorMatrix().apply { setSaturation(sat) }
        val off = (1f - con) * 127.5f
        m.postConcat(ColorMatrix(floatArrayOf(
            con * (1f + 0.14f * warm), 0f, 0f, 0f, off,
            0f, con * (1f + 0.03f * warm), 0f, 0f, off,
            0f, 0f, con * (1f - 0.14f * warm), 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )))
        return ColorMatrixColorFilter(m)
    }

    private fun vignette(canvas: Canvas, w: Int, h: Int, amount: Float) {
        if (amount <= 0f) return
        val r = max(w, h) * 0.75f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, alpha(Color.BLACK, 0.85f * amount.coerceIn(0f, 1f))),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** Soft coloured halo behind the person, made by drawing a tiny copy of the mask scaled up. */
    private fun rimGlow(canvas: Canvas, alphaBmp: Bitmap, maskSrc: Rect, dst: Rect, color: Int, amount: Float) {
        if (amount <= 0f || maskSrc.width() <= 0 || maskSrc.height() <= 0) return
        val cropped = Bitmap.createBitmap(alphaBmp, maskSrc.left, maskSrc.top, maskSrc.width(), maskSrc.height())
        val small = Bitmap.createScaledBitmap(cropped, max(1, min(48, maskSrc.width())), max(1, min(48, maskSrc.height())), true)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        p.color = alpha(color, 0.6f * amount.coerceIn(0f, 1f))
        val grow = (min(dst.width(), dst.height()) * 0.03f).toInt()
        canvas.drawBitmap(small, null, Rect(dst.left - grow, dst.top - grow, dst.right + grow, dst.bottom + grow), p)
        if (small !== cropped) small.recycle()
        if (cropped !== alphaBmp) cropped.recycle()
    }
}
