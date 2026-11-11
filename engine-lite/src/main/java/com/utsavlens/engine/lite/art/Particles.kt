package com.utsavlens.engine.lite.art

import android.graphics.Color
import android.graphics.RectF
import com.utsavlens.themes.ParticleType
import kotlin.math.sin

object Particles {
    /** [avoid] rectangles (faces) are never covered by a particle. */
    fun draw(s: ArtScope, type: ParticleType, density: Float, avoid: List<RectF> = emptyList()) {
        if (type == ParticleType.NONE || density <= 0f) return
        val r = s.rnd(900)
        val count = (density * when (type) {
            ParticleType.SPARKLE -> 46
            ParticleType.COLOR_POWDER -> 150
            ParticleType.PETALS -> 36
            ParticleType.SNOW -> 110
            ParticleType.CONFETTI -> 80
            ParticleType.EMBERS -> 60
            ParticleType.NONE -> 0
        }).toInt()
        val vivid = intArrayOf(Color.rgb(255, 23, 68), Color.rgb(255, 196, 0), Color.rgb(0, 230, 118), Color.rgb(41, 121, 255), Color.rgb(213, 0, 249))
        for (i in 0 until count) {
            val bx = r.nextFloat()
            val by = r.nextFloat()
            val speed = 0.03f + r.nextFloat() * 0.06f
            val sizeF = r.nextFloat()
            val spin = r.nextFloat() * 360f
            val falling = type == ParticleType.PETALS || type == ParticleType.SNOW || type == ParticleType.CONFETTI
            val rising = type == ParticleType.EMBERS
            var fy = by
            if (s.t != 0f) {
                if (falling) fy = (by + s.t * speed) % 1f
                if (rising) fy = ((by - s.t * speed) % 1f + 1f) % 1f
            }
            val x = bx * s.w + (if (s.t == 0f) 0f else sin(s.t * 1.5f + i) * s.u * 0.01f)
            val y = fy * s.h
            if (avoid.any { it.contains(x, y) }) continue
            when (type) {
                ParticleType.SPARKLE -> {
                    val size = s.u * (0.006f + sizeF * 0.014f)
                    val tw = s.twinkle(i * 0.9f, 5f)
                    s.glow(x, y, size * 2.2f, GOLD, 0.35f * tw)
                    s.star(x, y, size, size * 0.22f, 4, 0f, Color.WHITE, 0.9f * tw)
                }
                ParticleType.COLOR_POWDER -> s.glow(x, y, s.u * (0.006f + sizeF * 0.02f), vivid[i % vivid.size], 0.75f)
                ParticleType.PETALS -> {
                    val size = s.u * (0.012f + sizeF * 0.014f)
                    s.petal(x, y, size * 2f, size * 0.7f, spin + s.t * 50f, if (i % 2 == 0) s.pal.accent else lighten(s.pal.primary, 0.25f), 0.88f)
                }
                ParticleType.SNOW -> s.circle(x, y, s.u * (0.003f + sizeF * 0.006f), Color.WHITE, 0.85f)
                ParticleType.CONFETTI -> {
                    val size = s.u * (0.006f + sizeF * 0.008f)
                    s.c.save()
                    s.c.rotate(spin + s.t * 120f, x, y)
                    s.c.drawRect(x - size, y - size * 0.45f, x + size, y + size * 0.45f, s.fill(if (i % 6 == 5) Color.WHITE else (vivid + s.pal.all)[i % 8], 0.9f))
                    s.c.restore()
                }
                ParticleType.EMBERS -> {
                    val size = s.u * (0.003f + sizeF * 0.005f)
                    val col = if (i % 2 == 0) Color.rgb(255, 152, 0) else Color.rgb(255, 213, 79)
                    s.glow(x, y, size * 3f, col, 0.5f * s.twinkle(i.toFloat(), 6f))
                    s.circle(x, y, size, col, 0.95f)
                }
                ParticleType.NONE -> Unit
            }
        }
    }
}
