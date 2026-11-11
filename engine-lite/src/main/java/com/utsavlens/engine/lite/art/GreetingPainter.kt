package com.utsavlens.engine.lite.art

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Draws the greeting with the system typeface (no bundled fonts). StaticLayout is used so that
 * Devanagari shaping, line breaking and fallback fonts are handled by the platform.
 */
object GreetingPainter {

    /** Returns the rectangle the text occupies (empty when nothing was drawn). */
    fun draw(c: Canvas, w: Float, h: Float, text: String, pal: Pal, bottomInset: Float, opacity: Float = 1f): RectF {
        val content = text.trim()
        if (content.isEmpty()) return RectF()
        val u = minOf(w, h)
        val maxWidth = (w * 0.86f).toInt().coerceAtLeast(1)
        val shadowColor = alpha(Color.BLACK, 0.75f * opacity)
        val textColor = alpha(Color.WHITE, opacity)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.color = textColor
        paint.setShadowLayer(u * 0.012f, 0f, u * 0.004f, shadowColor)
        var size = u * 0.075f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(3)
                .build()
            if (layout.lineCount <= 2 || size <= u * 0.04f) break
            size *= 0.88f
        }
        val x = (w - maxWidth) / 2f
        val y = h - bottomInset - layout.height - u * 0.03f
        val pad = u * 0.02f
        val box = RectF(x - pad, y - pad, x + maxWidth + pad, y + layout.height + pad)
        val band = Paint(Paint.ANTI_ALIAS_FLAG)
        band.color = alpha(darken(pal.secondary, 0.55f), 0.45f * opacity)
        c.drawRoundRect(box, u * 0.03f, u * 0.03f, band)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG)
        rule.color = alpha(GOLD, 0.9f * opacity)
        rule.strokeWidth = u * 0.004f
        c.drawLine(box.left + pad * 2, box.top, box.right - pad * 2, box.top, rule)
        c.drawLine(box.left + pad * 2, box.bottom, box.right - pad * 2, box.bottom, rule)
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return box
    }
}
