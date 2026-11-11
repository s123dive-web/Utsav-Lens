package com.utsavlens.engine.lite.art

import android.graphics.Color
import android.graphics.RectF
import com.utsavlens.themes.Anchor
import com.utsavlens.themes.OverlaySpec
import com.utsavlens.themes.OverlayType
import kotlin.math.cos
import kotlin.math.sin

object Overlays {

    /** Overlays anchored to the bottom, sides or corners sit in front of the person; the rest behind. */
    fun isFront(spec: OverlaySpec): Boolean = when (spec.anchor) {
        Anchor.BOTTOM, Anchor.CORNERS, Anchor.SIDES, Anchor.BOTTOM_LEFT, Anchor.BOTTOM_RIGHT -> true
        Anchor.TOP, Anchor.FULL, Anchor.TOP_LEFT, Anchor.TOP_RIGHT -> false
    }

    fun draw(s: ArtScope, spec: OverlaySpec, index: Int) {
        val k = spec.scale
        val salt = 100 + index * 17
        when (spec.type) {
            OverlayType.DIYA_ROW -> diyaRow(s, spec.anchor, k)
            OverlayType.MARIGOLD_GARLAND -> garland(s, spec.anchor, k)
            OverlayType.RANGOLI_CORNER -> corners(s, spec.anchor) { x, y -> s.mandala(x, y, s.u * 0.2f * k, intArrayOf(s.pal.primary, s.pal.accent, s.pal.secondary, Color.WHITE), 4) }
            OverlayType.FIREWORKS -> fireworks(s, k, salt)
            OverlayType.LANTERNS -> lanterns(s, k, salt)
            OverlayType.KITES -> kites(s, k, salt)
            OverlayType.CRESCENT_STARS -> crescentStars(s, spec.anchor, k, salt)
            OverlayType.SNOWFLAKES -> snowflakes(s, k, salt)
            OverlayType.PINE_TREE -> pineTree(s, spec.anchor, k)
            OverlayType.STRING_LIGHTS -> stringLights(s, spec.anchor, k)
            OverlayType.TRICOLOR_RIBBONS -> tricolorRibbons(s, spec.anchor, k)
            OverlayType.BALLOONS -> balloons(s, spec.anchor, k, salt)
            OverlayType.PAISLEY -> corners(s, spec.anchor) { x, y -> paisley(s, x, y, k) }
            OverlayType.FLOWER_SHOWER -> flowerShower(s, k, salt)
            OverlayType.RAKHI -> rakhi(s, spec.anchor, k)
            OverlayType.KOLAM -> corners(s, spec.anchor) { x, y -> kolam(s, x, y, k) }
            OverlayType.POOKALAM -> pookalam(s, spec.anchor, k)
            OverlayType.BONFIRE -> bonfire(s, spec.anchor, k)
            OverlayType.SUGARCANE_POT -> sugarcanePot(s, spec.anchor, k)
            OverlayType.GUDI -> gudi(s, spec.anchor, k)
            OverlayType.DANDIYA -> dandiya(s, spec.anchor, k)
            OverlayType.BOW_ARROW -> bowArrow(s, spec.anchor, k)
            OverlayType.LOTUS -> lotus(s, spec.anchor, k)
            OverlayType.COLOR_CLOUDS -> colorClouds(s, k, salt)
        }
    }

    // ---- placement helpers -------------------------------------------------------------

    private inline fun corners(s: ArtScope, anchor: Anchor, draw: (Float, Float) -> Unit) {
        val pts = when (anchor) {
            Anchor.TOP_LEFT -> listOf(0f to 0f)
            Anchor.TOP_RIGHT -> listOf(s.w to 0f)
            Anchor.BOTTOM_LEFT -> listOf(0f to s.h)
            Anchor.BOTTOM_RIGHT -> listOf(s.w to s.h)
            Anchor.TOP -> listOf(0f to 0f, s.w to 0f)
            Anchor.BOTTOM -> listOf(0f to s.h, s.w to s.h)
            Anchor.SIDES, Anchor.CORNERS, Anchor.FULL -> listOf(0f to 0f, s.w to 0f, 0f to s.h, s.w to s.h)
        }
        pts.forEach { (x, y) -> draw(x, y) }
    }

    /** x position for single objects that sit at one side. */
    private fun sideX(s: ArtScope, anchor: Anchor, inset: Float): Float = when (anchor) {
        Anchor.TOP_RIGHT, Anchor.BOTTOM_RIGHT -> s.w - inset
        Anchor.TOP_LEFT, Anchor.BOTTOM_LEFT -> inset
        else -> inset
    }

    private fun isTop(anchor: Anchor) = anchor == Anchor.TOP || anchor == Anchor.TOP_LEFT || anchor == Anchor.TOP_RIGHT

    // ---- motifs -----------------------------------------------------------------------

    fun diya(s: ArtScope, x: Float, y: Float, size: Float, phase: Float) {
        val flick = s.twinkle(phase, 9f)
        s.glow(x, y - size * 0.55f, size * 1.9f, Color.rgb(255, 193, 7), 0.55f * flick)
        val clay = Color.rgb(183, 80, 40)
        s.path.rewind()
        s.path.moveTo(x - size, y - size * 0.1f)
        s.path.quadTo(x, y + size * 0.95f, x + size, y - size * 0.1f)
        s.path.quadTo(x, y + size * 0.2f, x - size, y - size * 0.1f)
        s.path.close()
        s.c.drawPath(s.path, s.fill(clay))
        s.c.drawOval(RectF(x - size, y - size * 0.3f, x + size, y + size * 0.12f), s.fill(darken(clay, 0.25f)))
        s.c.drawOval(RectF(x - size * 0.8f, y - size * 0.22f, x + size * 0.8f, y + size * 0.06f), s.fill(Color.rgb(255, 202, 40), 0.9f))
        for (i in -2..2) s.circle(x + i * size * 0.32f, y + size * 0.3f - kotlin.math.abs(i) * size * 0.07f, size * 0.05f, GOLD)
        s.petal(x, y - size * 0.1f, size * (0.95f + 0.12f * flick), size * 0.42f, 0f, Color.rgb(255, 143, 0))
        s.petal(x, y - size * 0.1f, size * 0.6f, size * 0.24f, 0f, Color.rgb(255, 241, 118))
    }

    private fun diyaRow(s: ArtScope, anchor: Anchor, k: Float) {
        val size = s.u * 0.045f * k
        val n = (s.w / (size * 3.6f)).toInt().coerceIn(3, 9)
        val y = if (isTop(anchor)) size * 2.2f else s.h - size * 1.3f
        for (i in 0 until n) diya(s, s.w * (i + 0.5f) / n, y, size, i * 1.7f)
    }

    private fun garland(s: ArtScope, anchor: Anchor, k: Float) {
        val swags = if (s.w > s.h) 5 else 3
        val span = s.w / swags
        val r = s.u * 0.024f * k
        val top = if (isTop(anchor) || anchor == Anchor.FULL) 0f else s.h - s.u * 0.16f * k
        val drop = s.u * 0.1f * k
        for (i in 0 until swags) {
            val x0 = i * span
            val n = 9
            for (j in 0..n) {
                val f = j / n.toFloat()
                val x = x0 + f * span
                val y = top + r + drop * 4f * f * (1f - f)
                if (j % 2 == 0) s.leaf(x, y, r * 2.2f, 160f + j * 7f)
                s.flower(x, y, r, if (j % 2 == 0) Color.rgb(255, 152, 0) else Color.rgb(255, 213, 79))
            }
        }
        for (i in 0..swags) {
            val x = (i * span).coerceIn(r, s.w - r)
            for (j in 0..3) s.flower(x, top + r + j * r * 1.9f, r * (1f - j * 0.08f), if (j % 2 == 0) Color.rgb(255, 213, 79) else Color.rgb(255, 152, 0))
            s.leaf(x, top + r + 4 * r * 1.9f, r * 2.4f, 180f)
        }
    }

    private fun fireworks(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val cols = intArrayOf(s.pal.accent, s.pal.primary, Color.rgb(255, 255, 255), s.pal.secondary, Color.rgb(0, 229, 255))
        repeat(4) { i ->
            val cx = s.w * (0.12f + r.nextFloat() * 0.76f)
            val cy = s.h * (0.07f + r.nextFloat() * 0.22f)
            val maxR = s.u * (0.09f + r.nextFloat() * 0.07f) * k
            val phase = if (s.t == 0f) 0.85f else ((s.t * 0.55f + i * 0.37f) % 1f)
            val rad = maxR * (0.25f + 0.75f * phase)
            val fade = (1f - phase * 0.75f).coerceIn(0f, 1f)
            val col = cols[i % cols.size]
            s.glow(cx, cy, rad * 1.2f, col, 0.3f * fade)
            val rays = 16
            for (j in 0 until rays) {
                val a = j * TAU / rays + i
                val x1 = cx + rad * 0.35f * cos(a)
                val y1 = cy + rad * 0.35f * sin(a)
                val x2 = cx + rad * cos(a)
                val y2 = cy + rad * sin(a) + rad * 0.12f * phase
                s.c.drawLine(x1, y1, x2, y2, s.stroke(col, s.u * 0.004f, 0.9f * fade))
                s.circle(x2, y2, s.u * 0.006f, lighten(col, 0.5f), fade)
            }
        }
    }

    private fun lanterns(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val n = if (s.w > s.h) 6 else 4
        for (i in 0 until n) {
            val x = s.w * (i + 0.5f) / n + (r.nextFloat() - 0.5f) * s.u * 0.04f
            val len = s.h * (0.05f + r.nextFloat() * 0.1f)
            val bw = s.u * 0.035f * k
            val bh = bw * 1.7f
            val sway = if (s.t == 0f) 0f else sin(s.t * 1.3f + i) * 4f
            s.c.save()
            s.c.rotate(sway, x, 0f)
            s.c.drawLine(x, 0f, x, len, s.stroke(GOLD, s.u * 0.003f, 0.9f))
            s.glow(x, len + bh / 2, bh * 1.5f, Color.rgb(255, 213, 79), 0.5f * s.twinkle(i.toFloat(), 2f))
            val body = s.pal.all[i % 3]
            s.c.drawRoundRect(RectF(x - bw, len, x + bw, len + bh), bw * 0.6f, bw * 0.6f, s.fill(body))
            s.c.drawRoundRect(RectF(x - bw * 0.5f, len + bh * 0.15f, x + bw * 0.5f, len + bh * 0.85f), bw * 0.4f, bw * 0.4f, s.fill(Color.rgb(255, 245, 157), 0.85f))
            s.c.drawRect(x - bw * 0.7f, len - bw * 0.25f, x + bw * 0.7f, len + bw * 0.1f, s.fill(GOLD))
            s.c.drawRect(x - bw * 0.7f, len + bh - bw * 0.1f, x + bw * 0.7f, len + bh + bw * 0.25f, s.fill(GOLD))
            s.c.drawLine(x, len + bh, x, len + bh + bw * 1.2f, s.stroke(GOLD, s.u * 0.004f))
            s.c.restore()
        }
    }

    private fun kites(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val cols = intArrayOf(s.pal.primary, s.pal.accent, s.pal.secondary, Color.rgb(0, 200, 83), Color.rgb(255, 64, 129))
        val n = if (s.w > s.h) 6 else 5
        for (i in 0 until n) {
            val cx = s.w * (0.08f + r.nextFloat() * 0.84f)
            val cy = s.h * (0.05f + r.nextFloat() * 0.3f)
            val size = s.u * (0.045f + r.nextFloat() * 0.035f) * k
            val tilt = -25f + r.nextFloat() * 50f + (if (s.t == 0f) 0f else sin(s.t * 1.6f + i) * 6f)
            s.c.save()
            s.c.rotate(tilt, cx, cy)
            s.path.rewind()
            s.path.moveTo(cx, cy - size)
            s.path.lineTo(cx + size * 0.8f, cy)
            s.path.lineTo(cx, cy + size * 1.15f)
            s.path.lineTo(cx - size * 0.8f, cy)
            s.path.close()
            val col = cols[i % cols.size]
            s.c.drawPath(s.path, s.fill(col))
            s.path.rewind()
            s.path.moveTo(cx, cy - size)
            s.path.lineTo(cx + size * 0.8f, cy)
            s.path.lineTo(cx, cy)
            s.path.close()
            s.c.drawPath(s.path, s.fill(lighten(col, 0.3f)))
            s.c.drawLine(cx, cy - size, cx, cy + size * 1.15f, s.stroke(darken(col, 0.4f), s.u * 0.002f))
            s.c.drawLine(cx - size * 0.8f, cy, cx + size * 0.8f, cy, s.stroke(darken(col, 0.4f), s.u * 0.002f))
            // tail
            s.path.rewind()
            s.path.moveTo(cx, cy + size * 1.15f)
            s.path.cubicTo(cx - size * 0.5f, cy + size * 1.8f, cx + size * 0.5f, cy + size * 2.3f, cx, cy + size * 3f)
            s.c.drawPath(s.path, s.stroke(cols[(i + 1) % cols.size], s.u * 0.004f))
            s.c.restore()
            // string towards the bottom of the frame
            s.path.rewind()
            s.path.moveTo(cx, cy)
            s.path.quadTo(cx + (s.w / 2 - cx) * 0.3f, cy + s.h * 0.3f, s.w / 2 + (i - n / 2f) * s.u * 0.05f, s.h * 0.75f)
            s.c.drawPath(s.path, s.stroke(Color.WHITE, s.u * 0.0015f, 0.45f))
        }
    }

    private fun crescentStars(s: ArtScope, anchor: Anchor, k: Float, salt: Int) {
        val right = anchor != Anchor.TOP_LEFT
        val cx = if (right) s.w * 0.78f else s.w * 0.22f
        val cy = s.h * 0.14f
        val rad = s.u * 0.1f * k
        s.glow(cx, cy, rad * 2.4f, Color.rgb(255, 245, 157), 0.45f)
        val layer = s.c.saveLayer(cx - rad * 1.2f, cy - rad * 1.2f, cx + rad * 1.2f, cy + rad * 1.2f, null)
        s.circle(cx, cy, rad, Color.rgb(255, 236, 130))
        s.fill.shader = null
        s.fill.color = Color.BLACK
        s.fill.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        s.c.drawCircle(cx + rad * 0.42f * (if (right) 1f else -1f), cy - rad * 0.12f, rad * 0.86f, s.fill)
        s.fill.xfermode = null
        s.c.restoreToCount(layer)
        s.star(cx + rad * 0.5f * (if (right) 1f else -1f), cy - rad * 0.05f, rad * 0.24f, rad * 0.1f, 5, 0f, Color.rgb(255, 236, 130))
        val r = s.rnd(salt)
        repeat(14) { i ->
            val x = r.nextFloat() * s.w
            val y = r.nextFloat() * s.h * 0.34f
            val size = s.u * (0.008f + r.nextFloat() * 0.014f) * k
            s.star(x, y, size, size * 0.4f, 5, r.nextFloat(), if (i % 3 == 0) GOLD else Color.WHITE, 0.9f * s.twinkle(i.toFloat()))
        }
    }

    private fun snowflakes(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        repeat(16) { i ->
            val x = r.nextFloat() * s.w
            val baseY = r.nextFloat() * s.h
            val y = if (s.t == 0f) baseY * 0.5f else (baseY + s.t * s.h * (0.04f + (i % 4) * 0.012f)) % s.h
            val size = s.u * (0.012f + r.nextFloat() * 0.02f) * k
            val p = s.stroke(Color.WHITE, s.u * 0.0025f, 0.85f)
            for (a in 0 until 6) {
                val ang = a * TAU / 6 + s.t * 0.3f
                val ex = x + size * cos(ang)
                val ey = y + size * sin(ang)
                s.c.drawLine(x, y, ex, ey, p)
                val mx = x + size * 0.6f * cos(ang)
                val my = y + size * 0.6f * sin(ang)
                s.c.drawLine(mx, my, mx + size * 0.3f * cos(ang + 0.8f), my + size * 0.3f * sin(ang + 0.8f), p)
                s.c.drawLine(mx, my, mx + size * 0.3f * cos(ang - 0.8f), my + size * 0.3f * sin(ang - 0.8f), p)
            }
        }
    }

    private fun pineTree(s: ArtScope, anchor: Anchor, k: Float) {
        val height = s.u * 0.42f * k
        val cx = sideX(s, anchor, s.u * 0.14f * k)
        val base = s.h - s.u * 0.02f
        s.c.drawRect(cx - height * 0.05f, base - height * 0.14f, cx + height * 0.05f, base, s.fill(Color.rgb(109, 76, 65)))
        val green = Color.rgb(27, 94, 32)
        for (i in 0..3) {
            val top = base - height + i * height * 0.19f
            val bottom = top + height * 0.36f
            val half = height * (0.14f + i * 0.065f)
            s.path.rewind()
            s.path.moveTo(cx, top)
            s.path.lineTo(cx + half, bottom)
            s.path.lineTo(cx - half, bottom)
            s.path.close()
            s.c.drawPath(s.path, s.fill(lighten(green, 0.06f * (3 - i))))
            val cols = intArrayOf(Color.rgb(229, 57, 53), GOLD, Color.rgb(41, 182, 246))
            for (j in 0..2) {
                val bx = cx - half * 0.6f + j * half * 0.6f
                s.glow(bx, bottom - height * 0.05f, height * 0.05f, cols[(i + j) % 3], 0.6f * s.twinkle((i * 3 + j).toFloat(), 4f))
                s.circle(bx, bottom - height * 0.05f, height * 0.018f, cols[(i + j) % 3])
            }
        }
        s.star(cx, base - height, height * 0.07f, height * 0.03f, 5, 0f, GOLD)
    }

    private fun stringLights(s: ArtScope, anchor: Anchor, k: Float) {
        val top = if (isTop(anchor) || anchor == Anchor.FULL) s.u * 0.015f else s.h - s.u * 0.1f
        val swags = if (s.w > s.h) 4 else 3
        val span = s.w / swags
        val cols = intArrayOf(Color.rgb(255, 82, 82), GOLD, Color.rgb(105, 240, 174), Color.rgb(64, 196, 255), Color.rgb(224, 64, 251))
        var idx = 0
        for (i in 0 until swags) {
            val x0 = i * span
            s.path.rewind()
            s.path.moveTo(x0, top)
            s.path.quadTo(x0 + span / 2, top + s.u * 0.12f * k, x0 + span, top)
            s.c.drawPath(s.path, s.stroke(Color.rgb(55, 71, 79), s.u * 0.003f))
            for (j in 1..6) {
                val f = j / 7f
                val x = x0 + f * span
                val y = top + s.u * 0.24f * k * f * (1f - f)
                val col = cols[idx % cols.size]
                s.glow(x, y + s.u * 0.012f, s.u * 0.035f * k, col, 0.6f * s.twinkle(idx * 1.3f, 4f))
                s.c.drawOval(RectF(x - s.u * 0.008f * k, y, x + s.u * 0.008f * k, y + s.u * 0.024f * k), s.fill(col))
                idx++
            }
        }
    }

    private fun tricolorRibbons(s: ArtScope, anchor: Anchor, k: Float) {
        val saffron = Color.rgb(255, 153, 51)
        val green = Color.rgb(19, 136, 8)
        val band = s.u * 0.028f * k
        val top = if (isTop(anchor) || anchor == Anchor.FULL) s.u * 0.02f else s.h - band * 4.5f
        val cols = intArrayOf(saffron, Color.WHITE, green)
        for (b in 0..2) {
            s.path.rewind()
            val y = top + b * band
            s.path.moveTo(0f, y)
            val waves = 4
            for (i in 0 until waves) {
                val x0 = i * s.w / waves
                val amp = band * 1.2f * (if (i % 2 == 0) 1f else -1f) * (if (s.t == 0f) 1f else cos(s.t * 1.2f + i))
                s.path.quadTo(x0 + s.w / waves / 2, y + amp, x0 + s.w / waves, y)
            }
            s.c.drawPath(s.path, s.stroke(cols[b], band, 0.95f))
        }
        // bunting
        val n = 9
        for (i in 0 until n) {
            val x = s.w * (i + 0.5f) / n
            val y = top + band * 3.6f
            s.path.rewind()
            s.path.moveTo(x - band * 1.1f, y)
            s.path.lineTo(x + band * 1.1f, y)
            s.path.lineTo(x, y + band * 2.2f)
            s.path.close()
            s.c.drawPath(s.path, s.fill(cols[i % 3], 0.95f))
            if (i % 3 == 1) s.c.drawPath(s.path, s.stroke(Color.rgb(0, 0, 128), s.u * 0.0015f, 0.5f))
        }
    }

    private fun balloons(s: ArtScope, anchor: Anchor, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val cols = intArrayOf(s.pal.primary, s.pal.accent, s.pal.secondary, Color.rgb(0, 200, 83), Color.rgb(255, 64, 129), Color.rgb(41, 182, 246))
        val perSide = 3
        val sides = when (anchor) {
            Anchor.TOP_LEFT, Anchor.BOTTOM_LEFT -> listOf(0)
            Anchor.TOP_RIGHT, Anchor.BOTTOM_RIGHT -> listOf(1)
            else -> listOf(0, 1)
        }
        for (side in sides) for (i in 0 until perSide) {
            val x = (if (side == 0) s.u * (0.06f + i * 0.055f) else s.w - s.u * (0.06f + i * 0.055f))
            val bob = if (s.t == 0f) 0f else sin(s.t * 1.4f + i + side * 2) * s.u * 0.012f
            val y = s.h * (0.2f + r.nextFloat() * 0.3f) + bob
            val rx = s.u * 0.05f * k
            val ry = rx * 1.25f
            val col = cols[(i + side * 3) % cols.size]
            s.path.rewind()
            s.path.moveTo(x, y + ry)
            s.path.quadTo(x + rx * 0.6f, y + ry * 2.4f, x + (if (side == 0) rx else -rx) * 0.4f, s.h * 0.98f)
            s.c.drawPath(s.path, s.stroke(Color.WHITE, s.u * 0.002f, 0.6f))
            s.c.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), s.fill(col, 0.95f))
            s.c.drawOval(RectF(x - rx * 0.55f, y - ry * 0.7f, x - rx * 0.15f, y - ry * 0.15f), s.fill(Color.WHITE, 0.4f))
            s.path.rewind()
            s.path.moveTo(x - rx * 0.14f, y + ry * 1.12f)
            s.path.lineTo(x + rx * 0.14f, y + ry * 1.12f)
            s.path.lineTo(x, y + ry * 0.96f)
            s.path.close()
            s.c.drawPath(s.path, s.fill(darken(col, 0.2f)))
        }
    }

    private fun paisley(s: ArtScope, cx: Float, cy: Float, k: Float) {
        val dirX = if (cx <= 0f) 1f else -1f
        val dirY = if (cy <= 0f) 1f else -1f
        val size = s.u * 0.16f * k
        val henna = mix(s.pal.primary, Color.rgb(121, 60, 20), 0.35f)
        for (n in 0..2) {
            val ox = cx + dirX * size * (0.45f + n * 0.75f)
            val oy = cy + dirY * size * (0.5f + (2 - n) * 0.5f)
            val sz = size * (0.5f - n * 0.07f)
            s.c.save()
            s.c.rotate((if (dirX * dirY > 0) 35f else -35f) + n * 25f * dirX, ox, oy)
            s.path.rewind()
            s.path.moveTo(ox, oy + sz)
            s.path.cubicTo(ox - sz * 1.1f, oy + sz * 0.6f, ox - sz * 0.9f, oy - sz * 0.9f, ox + sz * 0.1f, oy - sz * 0.9f)
            s.path.cubicTo(ox + sz * 0.9f, oy - sz * 0.9f, ox + sz * 0.7f, oy - sz * 0.1f, ox + sz * 0.25f, oy - sz * 0.25f)
            s.path.cubicTo(ox + sz * 0.9f, oy + sz * 0.3f, ox + sz * 0.5f, oy + sz * 0.9f, ox, oy + sz)
            s.path.close()
            s.c.drawPath(s.path, s.fill(henna, 0.25f))
            s.c.drawPath(s.path, s.stroke(henna, s.u * 0.005f))
            for (d in 0 until 10) {
                val a = d * TAU / 10
                s.circle(ox - sz * 0.15f + sz * 0.42f * cos(a), oy + sz * 0.1f + sz * 0.5f * sin(a), s.u * 0.004f, henna)
            }
            s.flower(ox - sz * 0.15f, oy + sz * 0.1f, sz * 0.18f, s.pal.accent, 8)
            s.c.restore()
        }
    }

    private fun flowerShower(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val cols = intArrayOf(s.pal.accent, s.pal.primary, Color.rgb(255, 255, 255), Color.rgb(255, 128, 171))
        repeat(26) { i ->
            val x = r.nextFloat() * s.w
            val baseY = r.nextFloat() * s.h * 0.55f
            val y = if (s.t == 0f) baseY else (baseY + s.t * s.h * (0.05f + (i % 5) * 0.01f)) % (s.h * 0.9f)
            val size = s.u * (0.012f + r.nextFloat() * 0.014f) * k
            if (i % 3 == 0) s.flower(x, y, size, cols[i % cols.size], 6, 0.9f)
            else s.petal(x, y, size * 2.2f, size * 0.8f, r.nextFloat() * 360f + s.t * 40f, cols[i % cols.size], 0.85f)
        }
    }

    private fun rakhi(s: ArtScope, anchor: Anchor, k: Float) {
        val y = if (isTop(anchor) || anchor == Anchor.FULL || anchor == Anchor.CORNERS) s.u * 0.08f * k else s.h - s.u * 0.1f * k
        val strands = intArrayOf(Color.rgb(211, 47, 47), GOLD, Color.rgb(255, 111, 0))
        for (i in strands.indices) {
            s.path.rewind()
            s.path.moveTo(0f, y + (i - 1) * s.u * 0.006f)
            s.path.cubicTo(s.w * 0.3f, y - s.u * 0.05f, s.w * 0.7f, y + s.u * 0.05f, s.w, y + (i - 1) * s.u * 0.006f)
            s.c.drawPath(s.path, s.stroke(strands[i], s.u * 0.005f))
        }
        for (i in 0..8) if (i != 4) s.circle(s.w * (i + 0.5f) / 9f, y + (if (i < 4) -1 else 1) * s.u * 0.012f * (kotlin.math.abs(4 - i) / 4f), s.u * 0.009f * k, if (i % 2 == 0) GOLD else Color.WHITE)
        val cx = s.w / 2
        s.glow(cx, y, s.u * 0.12f * k, GOLD, 0.4f)
        s.mandala(cx, y, s.u * 0.065f * k, intArrayOf(Color.rgb(211, 47, 47), GOLD, s.pal.secondary), 3, rotation = s.t * 12f)
        for (side in -1..1 step 2) for (j in 0..3) {
            s.c.drawLine(cx + side * s.u * 0.065f * k, y, cx + side * s.u * (0.095f + j * 0.004f) * k, y + (j - 1.5f) * s.u * 0.012f, s.stroke(Color.rgb(211, 47, 47), s.u * 0.003f))
        }
    }

    private fun kolam(s: ArtScope, cx: Float, cy: Float, k: Float) {
        val rad = s.u * 0.2f * k
        val white = Color.WHITE
        for (ring in 1..3) {
            val rr = rad * ring / 3f
            s.c.drawCircle(cx, cy, rr, s.stroke(white, s.u * 0.004f, 0.9f))
            val n = ring * 6
            for (i in 0 until n) {
                val a = i * TAU / n
                val px = cx + rr * cos(a)
                val py = cy + rr * sin(a)
                s.circle(px, py, s.u * 0.006f, white, 0.95f)
                s.c.drawCircle(cx + (rr - rad / 6f) * cos(a + TAU / n / 2), cy + (rr - rad / 6f) * sin(a + TAU / n / 2), rad / 7f, s.stroke(white, s.u * 0.003f, 0.8f))
            }
        }
        s.circle(cx, cy, s.u * 0.012f, s.pal.accent)
    }

    private fun pookalam(s: ArtScope, anchor: Anchor, k: Float) {
        val rad = s.u * 0.3f * k
        val cx = when (anchor) {
            Anchor.BOTTOM_LEFT -> 0f
            Anchor.BOTTOM_RIGHT -> s.w
            else -> s.w / 2
        }
        val cy = s.h + rad * 0.35f
        val cols = intArrayOf(Color.rgb(255, 235, 59), Color.rgb(255, 152, 0), Color.rgb(211, 47, 47), Color.WHITE, Color.rgb(56, 142, 60), Color.rgb(142, 36, 170))
        for (ring in cols.size downTo 1) {
            val rr = rad * ring / cols.size
            s.circle(cx, cy, rr, cols[ring - 1])
            val n = 10 + ring * 4
            for (i in 0 until n) s.circle(cx + rr * cos(i * TAU / n), cy + rr * sin(i * TAU / n), rr * 0.09f, lighten(cols[ring - 1], 0.25f))
        }
        s.mandala(cx, cy, rad * 0.45f, intArrayOf(Color.rgb(255, 152, 0), Color.rgb(255, 235, 59), Color.WHITE), 3)
    }

    private fun bonfire(s: ArtScope, anchor: Anchor, k: Float) {
        val size = s.u * 0.16f * k
        val cx = when (anchor) {
            Anchor.BOTTOM_RIGHT -> s.w - size * 1.1f
            Anchor.BOTTOM -> s.w / 2
            else -> size * 1.1f
        }
        val base = s.h - size * 0.12f
        s.glow(cx, base - size * 0.7f, size * 2.6f, Color.rgb(255, 111, 0), 0.5f * s.twinkle(0f, 7f))
        val wood = Color.rgb(93, 64, 55)
        for (i in -1..1 step 2) {
            s.c.save()
            s.c.rotate(i * 16f, cx, base)
            s.c.drawRoundRect(RectF(cx - size, base - size * 0.13f, cx + size, base + size * 0.05f), size * 0.08f, size * 0.08f, s.fill(if (i < 0) wood else darken(wood, 0.2f)))
            s.c.restore()
        }
        val flames = intArrayOf(Color.rgb(213, 0, 0), Color.rgb(255, 109, 0), Color.rgb(255, 196, 0), Color.rgb(255, 245, 157))
        for (layer in flames.indices) {
            val scale = 1f - layer * 0.22f
            for (j in -1..1) {
                val wob = s.twinkle(layer * 2f + j, 8f)
                s.petal(cx + j * size * 0.36f * scale, base - size * 0.05f, size * (1.5f + 0.2f * wob - kotlin.math.abs(j) * 0.45f) * scale, size * 0.42f * scale, j * 9f, flames[layer], 0.92f)
            }
        }
    }

    private fun sugarcanePot(s: ArtScope, anchor: Anchor, k: Float) {
        val cane = Color.rgb(104, 159, 56)
        val height = s.h * 0.6f * k.coerceAtMost(1.4f)
        for (side in 0..1) for (j in 0..1) {
            val x = if (side == 0) s.u * (0.03f + j * 0.035f) else s.w - s.u * (0.03f + j * 0.035f)
            val lean = (if (side == 0) 1f else -1f) * (3f + j * 4f)
            s.c.save()
            s.c.rotate(lean, x, s.h)
            s.c.drawLine(x, s.h, x, s.h - height, s.stroke(if (j == 0) cane else mix(cane, Color.rgb(106, 27, 154), 0.35f), s.u * 0.016f * k))
            var y = s.h - height * 0.1f
            while (y > s.h - height) {
                s.c.drawLine(x - s.u * 0.008f * k, y, x + s.u * 0.008f * k, y, s.stroke(darken(cane, 0.4f), s.u * 0.003f))
                y -= height * 0.12f
            }
            for (l in -2..2) s.leaf(x, s.h - height, s.u * 0.14f * k, l * 28f, Color.rgb(67, 160, 71))
            s.c.restore()
        }
        // Pongal pot with overflowing rice.
        val right = anchor == Anchor.BOTTOM_RIGHT
        val cx = if (right) s.w - s.u * 0.2f * k else s.u * 0.2f * k
        val r = s.u * 0.085f * k
        val cy = s.h - r * 1.05f
        val clay = Color.rgb(191, 96, 48)
        s.circle(cx, cy, r, clay)
        s.c.drawOval(RectF(cx - r * 0.75f, cy - r * 1.12f, cx + r * 0.75f, cy - r * 0.72f), s.fill(darken(clay, 0.2f)))
        for (i in -3..3) s.circle(cx + i * r * 0.2f, cy - r * (1.0f + 0.12f * cos(i.toFloat())), r * 0.2f, Color.WHITE)
        for (i in -2..2) s.circle(cx + i * r * 0.3f, cy - r * 0.62f, r * 0.13f, Color.WHITE, 0.95f)
        s.c.drawArc(RectF(cx - r * 0.8f, cy - r * 0.5f, cx + r * 0.8f, cy + r * 0.5f), 20f, 140f, false, s.stroke(Color.WHITE, s.u * 0.004f, 0.85f))
        for (i in -2..2) s.circle(cx + i * r * 0.28f, cy + r * 0.1f, r * 0.05f, GOLD)
    }

    private fun gudi(s: ArtScope, anchor: Anchor, k: Float) {
        val right = anchor != Anchor.TOP_LEFT && anchor != Anchor.BOTTOM_LEFT
        val x = if (right) s.w - s.u * 0.12f * k else s.u * 0.12f * k
        val top = s.h * 0.1f
        val bottom = s.h * 0.78f
        val dir = if (right) -1f else 1f
        s.c.save()
        s.c.rotate(dir * 8f, x, bottom)
        s.c.drawLine(x, bottom, x, top, s.stroke(Color.rgb(141, 110, 99), s.u * 0.012f * k))
        // silk cloth
        val cw = s.u * 0.09f * k
        s.path.rewind()
        s.path.moveTo(x - cw * 0.5f, top + s.u * 0.02f)
        s.path.lineTo(x + cw * 0.5f, top + s.u * 0.02f)
        s.path.quadTo(x + cw * 0.9f, top + s.u * 0.16f * k, x + cw * 0.6f, top + s.u * 0.3f * k)
        s.path.lineTo(x - cw * 0.6f, top + s.u * 0.3f * k)
        s.path.quadTo(x - cw * 0.9f, top + s.u * 0.16f * k, x - cw * 0.5f, top + s.u * 0.02f)
        s.path.close()
        s.c.drawPath(s.path, s.fill(s.pal.primary))
        s.c.drawPath(s.path, s.stroke(GOLD, s.u * 0.006f))
        s.c.drawLine(x - cw * 0.6f, top + s.u * 0.27f * k, x + cw * 0.6f, top + s.u * 0.27f * k, s.stroke(GOLD, s.u * 0.008f))
        // inverted copper pot
        val pr = s.u * 0.04f * k
        val copper = Color.rgb(205, 127, 50)
        s.c.drawArc(RectF(x - pr, top - pr * 1.2f, x + pr, top + pr * 0.8f), 180f, 180f, true, s.fill(copper))
        s.c.drawRect(x - pr * 0.75f, top - pr * 0.25f, x + pr * 0.75f, top + pr * 0.1f, s.fill(darken(copper, 0.2f)))
        s.circle(x - pr * 0.3f, top - pr * 0.7f, pr * 0.14f, lighten(copper, 0.5f), 0.8f)
        // neem leaves and flower garland
        for (i in -2..2) s.leaf(x, top + s.u * 0.03f, s.u * 0.07f * k, 180f + i * 22f, Color.rgb(56, 142, 60))
        for (i in 0..6) s.flower(x + sin(i * 0.9f) * cw * 0.25f, top + s.u * (0.05f + i * 0.035f) * k, s.u * 0.014f * k, if (i % 2 == 0) Color.rgb(255, 152, 0) else Color.rgb(255, 235, 59))
        s.c.restore()
    }

    private fun dandiya(s: ArtScope, anchor: Anchor, k: Float) {
        val len = s.u * 0.26f * k
        val places = when (anchor) {
            Anchor.BOTTOM_LEFT -> listOf(s.u * 0.16f)
            Anchor.BOTTOM_RIGHT -> listOf(s.w - s.u * 0.16f)
            else -> listOf(s.u * 0.16f, s.w - s.u * 0.16f)
        }
        for ((n, cx) in places.withIndex()) {
            val cy = s.h - s.u * 0.14f * k
            val swing = if (s.t == 0f) 0f else sin(s.t * 3f + n) * 6f
            for (side in -1..1 step 2) {
                s.c.save()
                s.c.rotate(side * (38f + swing), cx, cy)
                val col = if (side < 0) s.pal.primary else s.pal.secondary
                s.c.drawLine(cx, cy + len / 2, cx, cy - len / 2, s.stroke(col, s.u * 0.014f * k))
                for (b in 0..5) s.c.drawLine(cx - s.u * 0.007f * k, cy - len / 2 + b * len / 5.5f, cx + s.u * 0.007f * k, cy - len / 2 + b * len / 5.5f + s.u * 0.006f, s.stroke(GOLD, s.u * 0.004f))
                for (tl in -1..1) s.c.drawLine(cx, cy + len / 2, cx + tl * s.u * 0.012f, cy + len / 2 + s.u * 0.035f * k, s.stroke(s.pal.accent, s.u * 0.004f))
                s.circle(cx, cy + len / 2, s.u * 0.01f * k, GOLD)
                s.c.restore()
            }
        }
    }

    private fun bowArrow(s: ArtScope, anchor: Anchor, k: Float) {
        val right = anchor != Anchor.TOP_LEFT && anchor != Anchor.BOTTOM_LEFT
        val size = s.u * 0.17f * k
        val cx = if (right) s.w - size * 0.95f else size * 0.95f
        val cy = if (isTop(anchor)) size * 1.15f else s.h - size * 1.15f
        s.c.save()
        s.c.rotate(if (right) -30f else 210f, cx, cy)
        val bow = RectF(cx - size * 0.55f, cy - size, cx + size * 0.55f, cy + size)
        s.c.drawArc(bow, -75f, 150f, false, s.stroke(GOLD, s.u * 0.012f * k))
        s.c.drawArc(bow, -75f, 150f, false, s.stroke(darken(GOLD, 0.35f), s.u * 0.003f))
        val tipX = cx + size * 0.55f * cos(1.309f)
        val tipY = size * sin(1.309f)
        s.c.drawLine(tipX, cy - tipY, tipX, cy + tipY, s.stroke(Color.WHITE, s.u * 0.003f, 0.9f))
        s.c.drawLine(cx - size * 0.75f, cy, cx + size * 0.85f, cy, s.stroke(Color.rgb(141, 110, 99), s.u * 0.007f * k))
        s.path.rewind()
        s.path.moveTo(cx + size * 1.02f, cy)
        s.path.lineTo(cx + size * 0.8f, cy - size * 0.09f)
        s.path.lineTo(cx + size * 0.8f, cy + size * 0.09f)
        s.path.close()
        s.c.drawPath(s.path, s.fill(s.pal.primary))
        for (f in 0..2) {
            s.c.drawLine(cx - size * (0.75f - f * 0.06f), cy, cx - size * (0.85f - f * 0.06f), cy - size * 0.08f, s.stroke(s.pal.accent, s.u * 0.004f))
            s.c.drawLine(cx - size * (0.75f - f * 0.06f), cy, cx - size * (0.85f - f * 0.06f), cy + size * 0.08f, s.stroke(s.pal.accent, s.u * 0.004f))
        }
        s.c.restore()
    }

    private fun lotus(s: ArtScope, anchor: Anchor, k: Float) {
        val size = s.u * 0.1f * k
        val xs = when (anchor) {
            Anchor.BOTTOM_LEFT -> listOf(size * 1.3f)
            Anchor.BOTTOM_RIGHT -> listOf(s.w - size * 1.3f)
            else -> listOf(size * 1.3f, s.w - size * 1.3f)
        }
        val pink = Color.rgb(240, 98, 146)
        for (cx in xs) {
            val cy = if (isTop(anchor)) size * 1.4f else s.h - size * 0.25f
            s.c.drawOval(RectF(cx - size * 1.3f, cy - size * 0.12f, cx + size * 1.3f, cy + size * 0.3f), s.fill(Color.rgb(46, 125, 50), 0.9f))
            for (a in intArrayOf(-68, 68, -44, 44)) s.petal(cx, cy, size * 1.05f, size * 0.36f, a.toFloat(), darken(pink, 0.12f))
            for (a in intArrayOf(-22, 22, 0)) s.petal(cx, cy, size * 1.25f, size * 0.38f, a.toFloat(), lighten(pink, if (a == 0) 0.3f else 0.12f))
            s.circle(cx, cy - size * 0.2f, size * 0.12f, GOLD)
        }
    }

    private fun colorClouds(s: ArtScope, k: Float, salt: Int) {
        val r = s.rnd(salt)
        val cols = intArrayOf(Color.rgb(255, 23, 68), Color.rgb(255, 196, 0), Color.rgb(0, 230, 118), Color.rgb(41, 121, 255), Color.rgb(213, 0, 249), Color.rgb(255, 109, 0))
        repeat(10) { i ->
            val edge = i % 4
            val f = r.nextFloat()
            val x = when (edge) { 0 -> f * s.w; 1 -> s.w; 2 -> f * s.w; else -> 0f }
            val y = when (edge) { 0 -> 0f; 1 -> f * s.h; 2 -> s.h; else -> f * s.h }
            val rad = s.u * (0.22f + r.nextFloat() * 0.16f) * k * (if (s.t == 0f) 1f else 0.9f + 0.1f * sin(s.t + i))
            s.glow(x, y, rad, cols[i % cols.size], 0.8f)
        }
    }
}
