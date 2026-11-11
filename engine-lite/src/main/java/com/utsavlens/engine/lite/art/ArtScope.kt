package com.utsavlens.engine.lite.art

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.utsavlens.themes.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Theme colours as ARGB ints. */
class Pal(val primary: Int, val secondary: Int, val accent: Int, val background: Int) {
    val all: IntArray = intArrayOf(primary, secondary, accent)

    companion object {
        fun of(p: Palette): Pal = Pal(parse(p.primary), parse(p.secondary), parse(p.accent), parse(p.background))

        private fun parse(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (_: IllegalArgumentException) {
            Color.GRAY
        }
    }
}

const val TAU = (2.0 * PI).toFloat()
val GOLD = Color.rgb(255, 213, 79)
val LEAF = Color.rgb(46, 125, 50)

fun alpha(color: Int, a: Float): Int =
    Color.argb((Color.alpha(color) * a.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))

fun mix(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
    )
}

fun lighten(c: Int, t: Float) = mix(c, Color.WHITE, t)
fun darken(c: Int, t: Float) = mix(c, Color.BLACK, t)

/**
 * Everything a procedural renderer needs. All artwork in this package is original and drawn
 * with Canvas primitives; nothing is loaded from image files.
 *
 * @param t animation time in seconds (0 for still images).
 * @param opacity global opacity multiplier for the layer being drawn.
 */
class ArtScope(
    val c: Canvas,
    val w: Float,
    val h: Float,
    val pal: Pal,
    val seed: Long,
    val t: Float = 0f,
    val opacity: Float = 1f,
) {
    /** Base unit: the shorter edge. */
    val u: Float = min(w, h)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val path = Path()

    fun rnd(salt: Int): Random = Random(seed * 31 + salt)

    fun fill(color: Int, a: Float = 1f): Paint = fill.apply { shader = null; this.color = alpha(color, a * opacity) }

    fun stroke(color: Int, width: Float, a: Float = 1f): Paint =
        line.apply { shader = null; this.color = alpha(color, a * opacity); strokeWidth = width }

    fun circle(x: Float, y: Float, r: Float, color: Int, a: Float = 1f) = c.drawCircle(x, y, r, fill(color, a))

    /** Soft radial glow that fades to transparent. */
    fun glow(x: Float, y: Float, r: Float, color: Int, a: Float = 1f) {
        if (r <= 0f) return
        fill.shader = RadialGradient(x, y, r, alpha(color, a * opacity), alpha(color, 0f), Shader.TileMode.CLAMP)
        fill.color = Color.WHITE
        c.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    /** Teardrop / petal pointing up from (x, y) with the given length and half-width. */
    fun petal(x: Float, y: Float, length: Float, halfWidth: Float, angleDeg: Float, color: Int, a: Float = 1f) {
        c.save()
        c.rotate(angleDeg, x, y)
        path.rewind()
        path.moveTo(x, y)
        path.quadTo(x - halfWidth, y - length * 0.5f, x, y - length)
        path.quadTo(x + halfWidth, y - length * 0.5f, x, y)
        path.close()
        c.drawPath(path, fill(color, a))
        c.restore()
    }

    fun star(x: Float, y: Float, outer: Float, inner: Float, points: Int, rotation: Float, color: Int, a: Float = 1f) {
        path.rewind()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val ang = rotation + i * TAU / (points * 2) - TAU / 4
            val px = x + r * cos(ang)
            val py = y + r * sin(ang)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, fill(color, a))
    }

    /** Marigold-like layered flower. */
    fun flower(x: Float, y: Float, r: Float, color: Int, petals: Int = 10, a: Float = 1f) {
        circle(x, y, r, darken(color, 0.18f), a)
        for (i in 0 until petals) {
            val ang = i * TAU / petals
            circle(x + r * 0.62f * cos(ang), y + r * 0.62f * sin(ang), r * 0.38f, color, a)
        }
        circle(x, y, r * 0.55f, lighten(color, 0.22f), a)
        circle(x, y, r * 0.22f, darken(color, 0.3f), a)
    }

    fun leaf(x: Float, y: Float, length: Float, angleDeg: Float, color: Int = LEAF, a: Float = 1f) =
        petal(x, y, length, length * 0.28f, angleDeg, color, a)

    /** Concentric petal rings: the basis of rangoli, kolam and pookalam. */
    fun mandala(x: Float, y: Float, r: Float, colors: IntArray, rings: Int = 4, a: Float = 1f, rotation: Float = 0f) {
        for (k in rings downTo 1) {
            val rr = r * k / rings
            val n = 6 + k * 2
            val col = colors[(k - 1) % colors.size]
            for (i in 0 until n) {
                val deg = rotation + i * 360f / n + (if (k % 2 == 0) 180f / n else 0f)
                petal(x, y, rr, rr * 0.9f * (PI.toFloat() / n), deg, col, a)
            }
            c.drawCircle(x, y, rr * 0.62f, stroke(lighten(col, 0.5f), r * 0.012f, a))
        }
        circle(x, y, r * 0.12f, colors[0], a)
        circle(x, y, r * 0.06f, Color.WHITE, a)
    }

    /** Value in 0..1 that oscillates with time; constant 1 for stills unless [still] is given. */
    fun twinkle(phase: Float, speed: Float = 3f, still: Float = 1f): Float =
        if (t == 0f) still else 0.55f + 0.45f * sin(t * speed + phase)
}
