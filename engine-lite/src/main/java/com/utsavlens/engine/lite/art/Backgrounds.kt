package com.utsavlens.engine.lite.art

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import com.utsavlens.themes.BackgroundStyle
import kotlin.math.cos
import kotlin.math.sin

object Backgrounds {

    fun draw(s: ArtScope, style: BackgroundStyle, intensity: Float) {
        when (style) {
            BackgroundStyle.NIGHT_BOKEH -> nightBokeh(s, intensity)
            BackgroundStyle.COLOR_SPLASH -> colorSplash(s, intensity)
            BackgroundStyle.SUNBURST -> sunburst(s, intensity)
            BackgroundStyle.SKY -> sky(s, intensity)
            BackgroundStyle.GRADIENT_GLOW -> gradientGlow(s, intensity)
            BackgroundStyle.FLORAL_WALL -> floralWall(s, intensity)
            BackgroundStyle.TEMPLE_ARCH -> templeArch(s, intensity)
            BackgroundStyle.SNOWY_NIGHT -> snowyNight(s, intensity)
            BackgroundStyle.CONFETTI_PARTY -> confettiParty(s, intensity)
        }
    }

    private fun vertical(s: ArtScope, top: Int, bottom: Int) {
        s.fill.shader = LinearGradient(0f, 0f, 0f, s.h, top, bottom, Shader.TileMode.CLAMP)
        s.fill.color = Color.WHITE
        s.c.drawRect(0f, 0f, s.w, s.h, s.fill)
        s.fill.shader = null
    }

    private fun nightBokeh(s: ArtScope, k: Float) {
        vertical(s, darken(s.pal.background, 0.35f), mix(s.pal.background, s.pal.secondary, 0.35f))
        val r = s.rnd(1)
        repeat((38 * k).toInt().coerceAtLeast(8)) { i ->
            val col = s.pal.all[i % 3]
            val x = r.nextFloat() * s.w
            val y = r.nextFloat() * s.h
            val rad = s.u * (0.02f + r.nextFloat() * 0.07f)
            s.glow(x, y, rad * 1.6f, col, 0.25f * s.twinkle(i.toFloat(), 1.5f))
            s.circle(x, y, rad, lighten(col, 0.3f), 0.16f)
        }
        s.glow(s.w / 2, s.h * 0.95f, s.u * 0.9f, s.pal.primary, 0.35f)
    }

    private fun colorSplash(s: ArtScope, k: Float) {
        s.c.drawColor(lighten(s.pal.background, 0.1f))
        val vivid = intArrayOf(
            s.pal.primary, s.pal.secondary, s.pal.accent,
            Color.rgb(0, 188, 212), Color.rgb(124, 77, 255), Color.rgb(118, 255, 3),
        )
        val r = s.rnd(2)
        repeat((16 * k).toInt().coerceAtLeast(6)) { i ->
            s.glow(r.nextFloat() * s.w, r.nextFloat() * s.h, s.u * (0.2f + r.nextFloat() * 0.3f), vivid[i % vivid.size], 0.75f)
        }
        repeat((260 * k).toInt()) { i ->
            s.circle(r.nextFloat() * s.w, r.nextFloat() * s.h, s.u * (0.002f + r.nextFloat() * 0.006f), vivid[i % vivid.size], 0.7f)
        }
    }

    private fun sunburst(s: ArtScope, k: Float) {
        s.c.drawColor(s.pal.background)
        val cx = s.w / 2
        val cy = s.h * 0.38f
        val reach = (s.w + s.h)
        val rays = 24
        for (i in 0 until rays) {
            val a0 = i * TAU / rays + s.t * 0.05f
            val a1 = a0 + TAU / rays / 2
            s.path.rewind()
            s.path.moveTo(cx, cy)
            s.path.lineTo(cx + reach * cos(a0), cy + reach * sin(a0))
            s.path.lineTo(cx + reach * cos(a1), cy + reach * sin(a1))
            s.path.close()
            s.c.drawPath(s.path, s.fill(if (i % 2 == 0) s.pal.primary else s.pal.accent, 0.28f * k.coerceAtMost(1.5f)))
        }
        s.glow(cx, cy, s.u * 0.75f, lighten(s.pal.accent, 0.4f), 0.85f)
    }

    private fun sky(s: ArtScope, k: Float) {
        vertical(s, mix(Color.rgb(41, 121, 255), s.pal.primary, 0.12f), Color.rgb(225, 245, 254))
        s.glow(s.w * 0.8f, s.h * 0.16f, s.u * 0.42f, Color.rgb(255, 245, 157), 0.9f)
        s.circle(s.w * 0.8f, s.h * 0.16f, s.u * 0.07f, Color.rgb(255, 253, 231))
        val r = s.rnd(3)
        repeat((5 * k).toInt().coerceAtLeast(3)) {
            val x = (r.nextFloat() * s.w + s.t * s.u * 0.01f) % (s.w * 1.2f)
            val y = s.h * (0.08f + r.nextFloat() * 0.45f)
            val size = s.u * (0.05f + r.nextFloat() * 0.05f)
            for (j in 0 until 5) s.circle(x + (j - 2) * size * 0.8f, y + (if (j % 2 == 0) 0f else -size * 0.35f), size, Color.WHITE, 0.82f)
        }
    }

    private fun gradientGlow(s: ArtScope, k: Float) {
        s.fill.shader = LinearGradient(0f, 0f, s.w, s.h, s.pal.primary, s.pal.secondary, Shader.TileMode.CLAMP)
        s.fill.color = Color.WHITE
        s.c.drawRect(0f, 0f, s.w, s.h, s.fill)
        s.fill.shader = null
        s.glow(s.w / 2, s.h * 0.42f, s.u * 0.8f, lighten(s.pal.accent, 0.3f), 0.55f * k.coerceAtMost(1.6f))
        val r = s.rnd(4)
        repeat((22 * k).toInt()) { i ->
            s.circle(r.nextFloat() * s.w, r.nextFloat() * s.h, s.u * (0.004f + r.nextFloat() * 0.01f), Color.WHITE, 0.35f * s.twinkle(i.toFloat()))
        }
    }

    private fun floralWall(s: ArtScope, k: Float) {
        vertical(s, lighten(s.pal.background, 0.15f), s.pal.background)
        val r = s.rnd(5)
        val step = s.u * 0.17f / k.coerceIn(0.6f, 1.6f)
        var row = 0
        var y = -step * 0.3f
        while (y < s.h + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < s.w + step) {
                val fx = x + (r.nextFloat() - 0.5f) * step * 0.35f
                val fy = y + (r.nextFloat() - 0.5f) * step * 0.35f
                val size = step * (0.26f + r.nextFloat() * 0.14f)
                s.leaf(fx, fy, size * 2.1f, 60f + r.nextFloat() * 60f, LEAF, 0.85f)
                s.leaf(fx, fy, size * 2.1f, 240f + r.nextFloat() * 60f, darken(LEAF, 0.15f), 0.85f)
                s.flower(fx, fy, size, s.pal.all[r.nextInt(3)])
                x += step
            }
            y += step * 0.86f
            row++
        }
        s.c.drawColor(alpha(s.pal.background, 0.18f * s.opacity))
    }

    private fun templeArch(s: ArtScope, k: Float) {
        vertical(s, mix(s.pal.background, s.pal.primary, 0.25f), s.pal.background)
        s.glow(s.w / 2, s.h * 0.4f, s.u * 0.7f, lighten(s.pal.accent, 0.35f), 0.5f * k.coerceAtMost(1.5f))
        val pillar = s.u * 0.11f
        val stone = mix(s.pal.accent, Color.rgb(121, 85, 72), 0.45f)
        for (side in 0..1) {
            val x0 = if (side == 0) 0f else s.w - pillar
            s.fill.shader = LinearGradient(x0, 0f, x0 + pillar, 0f, darken(stone, 0.25f), lighten(stone, 0.2f), Shader.TileMode.MIRROR)
            s.fill.color = Color.WHITE
            s.c.drawRect(x0, s.h * 0.16f, x0 + pillar, s.h, s.fill)
            s.fill.shader = null
            var y = s.h * 0.24f
            while (y < s.h) {
                s.c.drawLine(x0, y, x0 + pillar, y, s.stroke(darken(stone, 0.35f), s.u * 0.004f, 0.6f))
                y += s.u * 0.09f
            }
            s.c.drawRect(x0 - pillar * 0.1f, s.h * 0.14f, x0 + pillar * 1.1f, s.h * 0.18f, s.fill(darken(stone, 0.1f)))
        }
        // Scalloped arch across the top.
        val scallops = 7
        val span = (s.w - 2 * pillar) / scallops
        s.path.rewind()
        s.path.moveTo(0f, 0f)
        s.path.lineTo(s.w, 0f)
        s.path.lineTo(s.w, s.h * 0.16f)
        s.path.lineTo(s.w - pillar, s.h * 0.16f)
        for (i in 0 until scallops) {
            val xr = s.w - pillar - i * span
            val depth = s.h * (0.045f + 0.03f * (1f - kotlin.math.abs(i - (scallops - 1) / 2f) / (scallops / 2f)))
            s.path.quadTo(xr - span / 2, s.h * 0.16f - depth * 2.4f, xr - span, s.h * 0.16f)
        }
        s.path.lineTo(0f, s.h * 0.16f)
        s.path.close()
        s.c.drawPath(s.path, s.fill(darken(stone, 0.05f)))
        s.c.drawPath(s.path, s.stroke(GOLD, s.u * 0.008f, 0.9f))
    }

    private fun snowyNight(s: ArtScope, k: Float) {
        vertical(s, Color.rgb(8, 20, 48), mix(Color.rgb(30, 60, 114), s.pal.secondary, 0.2f))
        val r = s.rnd(6)
        repeat((70 * k).toInt()) { i ->
            s.circle(r.nextFloat() * s.w, r.nextFloat() * s.h * 0.7f, s.u * (0.0015f + r.nextFloat() * 0.003f), Color.WHITE, 0.8f * s.twinkle(i.toFloat(), 2f))
        }
        s.glow(s.w * 0.18f, s.h * 0.14f, s.u * 0.3f, Color.rgb(255, 249, 196), 0.6f)
        s.circle(s.w * 0.18f, s.h * 0.14f, s.u * 0.06f, Color.rgb(255, 253, 231))
        for (layer in 0..2) {
            val base = s.h * (0.78f + layer * 0.07f)
            s.path.rewind()
            s.path.moveTo(0f, s.h)
            s.path.lineTo(0f, base)
            s.path.cubicTo(s.w * 0.3f, base - s.h * (0.1f - layer * 0.02f), s.w * 0.6f, base + s.h * 0.06f, s.w, base - s.h * 0.04f)
            s.path.lineTo(s.w, s.h)
            s.path.close()
            s.c.drawPath(s.path, s.fill(mix(Color.rgb(176, 196, 222), Color.WHITE, layer / 2f)))
        }
    }

    private fun confettiParty(s: ArtScope, k: Float) {
        s.fill.shader = LinearGradient(0f, 0f, s.w, s.h, lighten(s.pal.primary, 0.25f), lighten(s.pal.secondary, 0.2f), Shader.TileMode.CLAMP)
        s.fill.color = Color.WHITE
        s.c.drawRect(0f, 0f, s.w, s.h, s.fill)
        s.fill.shader = null
        var x = -s.h
        val gap = s.u * 0.12f
        while (x < s.w) {
            s.c.drawLine(x, s.h, x + s.h, 0f, s.stroke(Color.WHITE, gap * 0.35f, 0.08f))
            x += gap
        }
        s.fill.shader = RadialGradient(s.w / 2, s.h * 0.4f, s.u * 0.8f, alpha(Color.WHITE, 0.45f * s.opacity), alpha(Color.WHITE, 0f), Shader.TileMode.CLAMP)
        s.c.drawRect(0f, 0f, s.w, s.h, s.fill)
        s.fill.shader = null
        val r = s.rnd(7)
        val cols = intArrayOf(s.pal.accent, Color.WHITE, s.pal.primary, s.pal.secondary, Color.rgb(0, 229, 255))
        repeat((90 * k).toInt()) { i ->
            val px = r.nextFloat() * s.w
            val py = r.nextFloat() * s.h
            val size = s.u * (0.008f + r.nextFloat() * 0.012f)
            s.c.save()
            s.c.rotate(r.nextFloat() * 360f, px, py)
            s.c.drawRect(px - size, py - size * 0.4f, px + size, py + size * 0.4f, s.fill(cols[i % cols.size], 0.8f))
            s.c.restore()
        }
    }
}
