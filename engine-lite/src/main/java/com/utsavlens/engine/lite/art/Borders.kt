package com.utsavlens.engine.lite.art

import android.graphics.Color
import com.utsavlens.themes.BorderStyle
import kotlin.math.max

object Borders {
    fun draw(s: ArtScope, style: BorderStyle, widthPct: Float) {
        if (style == BorderStyle.NONE || widthPct <= 0f) return
        val bw = s.u * widthPct / 100f
        when (style) {
            BorderStyle.NONE -> Unit
            BorderStyle.SIMPLE -> {
                frame(s, bw, s.pal.accent, 0.95f)
                inset(s, bw * 1.25f, Color.WHITE, s.u * 0.003f, 0.8f)
            }
            BorderStyle.RANGOLI -> {
                frame(s, bw, darken(s.pal.primary, 0.1f), 0.95f)
                along(s, bw, bw * 1.5f) { x, y, i ->
                    s.circle(x, y, bw * 0.34f, if (i % 2 == 0) s.pal.accent else Color.WHITE)
                    s.circle(x, y, bw * 0.14f, if (i % 2 == 0) s.pal.secondary else s.pal.primary)
                }
                inset(s, bw, GOLD, s.u * 0.003f, 0.9f)
            }
            BorderStyle.FLORAL -> {
                frame(s, bw * 0.35f, LEAF, 0.9f)
                along(s, bw, bw * 2.1f) { x, y, i ->
                    s.leaf(x, y, bw * 1.3f, i * 47f)
                    s.flower(x, y, bw * 0.5f, s.pal.all[i % 3], 8)
                }
            }
            BorderStyle.ZARI -> {
                frame(s, bw, darken(s.pal.secondary, 0.25f), 0.92f)
                inset(s, bw * 0.15f, GOLD, s.u * 0.004f, 1f)
                inset(s, bw * 0.92f, GOLD, s.u * 0.004f, 1f)
                along(s, bw, bw * 1.3f) { x, y, _ ->
                    s.c.save()
                    s.c.rotate(45f, x, y)
                    s.c.drawRect(x - bw * 0.2f, y - bw * 0.2f, x + bw * 0.2f, y + bw * 0.2f, s.fill(GOLD))
                    s.c.restore()
                }
            }
            BorderStyle.LIGHTS -> {
                frame(s, bw * 0.3f, Color.rgb(38, 50, 56), 0.85f)
                val cols = intArrayOf(Color.rgb(255, 82, 82), GOLD, Color.rgb(105, 240, 174), Color.rgb(64, 196, 255))
                along(s, bw, bw * 1.9f) { x, y, i ->
                    val tw = s.twinkle(i * 1.1f, 4f)
                    s.glow(x, y, bw * 1.1f, cols[i % cols.size], 0.6f * tw)
                    s.circle(x, y, bw * 0.3f, lighten(cols[i % cols.size], 0.2f))
                }
            }
            BorderStyle.TRICOLOR -> {
                val band = bw / 3f
                val cols = intArrayOf(Color.rgb(255, 153, 51), Color.WHITE, Color.rgb(19, 136, 8))
                for (i in 0..2) {
                    s.c.drawRect(0f, i * band, s.w, (i + 1) * band, s.fill(cols[i]))
                    s.c.drawRect(0f, s.h - (3 - i) * band, s.w, s.h - (2 - i) * band, s.fill(cols[i]))
                }
            }
            BorderStyle.DOTTED -> {
                inset(s, bw * 0.5f, s.pal.accent, s.u * 0.003f, 0.8f)
                along(s, bw, bw * 0.9f) { x, y, i -> s.circle(x, y, bw * 0.2f, if (i % 2 == 0) s.pal.accent else s.pal.primary) }
            }
        }
    }

    /** Solid frame made of four rectangles so the picture area stays untouched. */
    private fun frame(s: ArtScope, bw: Float, color: Int, a: Float) {
        val p = s.fill(color, a)
        s.c.drawRect(0f, 0f, s.w, bw, p)
        s.c.drawRect(0f, s.h - bw, s.w, s.h, p)
        s.c.drawRect(0f, bw, bw, s.h - bw, p)
        s.c.drawRect(s.w - bw, bw, s.w, s.h - bw, p)
    }

    private fun inset(s: ArtScope, d: Float, color: Int, width: Float, a: Float) {
        s.c.drawRect(d, d, s.w - d, s.h - d, s.stroke(color, width, a))
    }

    /** Calls [draw] at evenly spaced points along the middle of the border band. */
    private inline fun along(s: ArtScope, bw: Float, spacing: Float, draw: (Float, Float, Int) -> Unit) {
        val m = bw / 2f
        val nx = max(2, ((s.w - bw) / spacing).toInt())
        val ny = max(2, ((s.h - bw) / spacing).toInt())
        var i = 0
        for (k in 0..nx) { draw(m + (s.w - bw) * k / nx, m, i++) }
        for (k in 1..ny) { draw(s.w - m, m + (s.h - bw) * k / ny, i++) }
        for (k in 1..nx) { draw(s.w - m - (s.w - bw) * k / nx, s.h - m, i++) }
        for (k in 1 until ny) { draw(m, s.h - m - (s.h - bw) * k / ny, i++) }
    }
}
