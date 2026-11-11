package com.utsavlens.engine.lite

import kotlin.math.roundToInt

/** Pure geometry for the output aspect options. */
object CropMath {
    /**
     * Largest rectangle with [ratio] (width / height) inside a [srcW] x [srcH] image, centred on the
     * focus point (fractions 0..1) as far as the image bounds allow. Null ratio keeps the whole image.
     * Returns [x, y, width, height].
     */
    fun cropRect(srcW: Int, srcH: Int, ratio: Float?, focusX: Float = 0.5f, focusY: Float = 0.5f): IntArray {
        if (ratio == null || ratio <= 0f || srcW <= 0 || srcH <= 0) return intArrayOf(0, 0, srcW, srcH)
        var w = srcW
        var h = (srcW / ratio).roundToInt()
        if (h > srcH) {
            h = srcH
            w = (srcH * ratio).roundToInt()
        }
        w = w.coerceIn(1, srcW)
        h = h.coerceIn(1, srcH)
        val x = ((focusX.coerceIn(0f, 1f) * srcW) - w / 2f).roundToInt().coerceIn(0, srcW - w)
        val y = ((focusY.coerceIn(0f, 1f) * srcH) - h / 2f).roundToInt().coerceIn(0, srcH - h)
        return intArrayOf(x, y, w, h)
    }

    /** Centre of mass of a mask as fractions; (0.5, 0.5) for an empty mask. */
    fun centroid(alpha: ByteArray, w: Int, h: Int): FloatArray {
        var sum = 0L
        var sx = 0L
        var sy = 0L
        val step = maxOf(1, maxOf(w, h) / 128)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val a = alpha[y * w + x].toInt() and 0xFF
                if (a > 127) { sum += 1; sx += x; sy += y }
                x += step
            }
            y += step
        }
        if (sum == 0L) return floatArrayOf(0.5f, 0.5f)
        return floatArrayOf(sx.toFloat() / sum / w, sy.toFloat() / sum / h)
    }
}
