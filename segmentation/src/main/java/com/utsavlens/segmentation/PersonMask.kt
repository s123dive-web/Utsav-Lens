package com.utsavlens.segmentation

/** Face bounding box in mask pixel coordinates. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Person confidence per pixel (0 = background, 255 = person), already feathered.
 * [personFound] false means the theme should be applied to the whole image.
 */
class PersonMask(
    val width: Int,
    val height: Int,
    val alpha: ByteArray,
    val faces: List<FaceBox>,
    val personFound: Boolean,
    /** True when the segmenter itself failed (missing native library, out of memory...). */
    val segmenterFailed: Boolean = false,
) {
    init { require(alpha.size == width * height) { "mask size mismatch" } }

    companion object {
        fun empty(width: Int, height: Int, failed: Boolean = false) =
            PersonMask(width, height, ByteArray(width * height), emptyList(), personFound = false, segmenterFailed = failed)
    }
}

/** Pure mask maths, unit-tested on the JVM. */
object MaskOps {
    /** Fraction of pixels whose confidence is above 50 %. */
    fun coverage(alpha: ByteArray): Float {
        if (alpha.isEmpty()) return 0f
        var n = 0
        for (b in alpha) if ((b.toInt() and 0xFF) > 127) n++
        return n.toFloat() / alpha.size
    }

    /** A person counts as found when a face was detected or the mask covers at least [minCoverage]. */
    fun personFound(alpha: ByteArray, faces: Int, minCoverage: Float = 0.02f): Boolean =
        faces > 0 || coverage(alpha) >= minCoverage

    fun fromConfidence(confidence: FloatArray): ByteArray =
        ByteArray(confidence.size) { i -> (confidence[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte() }

    /**
     * Sharpens the soft model output into a clean matte: values below [low] become 0, above
     * [high] become 255, with a smooth ramp in between.
     */
    fun tighten(alpha: ByteArray, low: Int = 64, high: Int = 192): ByteArray {
        val span = (high - low).coerceAtLeast(1)
        return ByteArray(alpha.size) { i ->
            val v = alpha[i].toInt() and 0xFF
            val t = ((v - low).toFloat() / span).coerceIn(0f, 1f)
            (t * t * (3f - 2f * t) * 255f + 0.5f).toInt().toByte()
        }
    }

    /** Separable box blur (two passes approximate a gaussian) used to feather the mask edge. */
    fun feather(alpha: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0 || width <= 1 || height <= 1) return alpha.copyOf()
        var src = alpha
        repeat(2) {
            val tmp = ByteArray(src.size)
            blurPass(src, tmp, width, height, radius, horizontal = true)
            val out = ByteArray(src.size)
            blurPass(tmp, out, width, height, radius, horizontal = false)
            src = out
        }
        return src
    }

    private fun blurPass(src: ByteArray, dst: ByteArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sum = 0
            for (k in -r..r) sum += src[base + k.coerceIn(0, len - 1) * stride].toInt() and 0xFF
            val window = 2 * r + 1
            for (i in 0 until len) {
                dst[base + i * stride] = (sum / window).toByte()
                val add = (i + r + 1).coerceAtMost(len - 1)
                val remove = (i - r).coerceAtLeast(0)
                sum += (src[base + add * stride].toInt() and 0xFF) - (src[base + remove * stride].toInt() and 0xFF)
            }
        }
    }

    /** Nearest-neighbour resize of a mask (bilinear smoothing is applied later when drawing). */
    fun resize(alpha: ByteArray, w: Int, h: Int, newW: Int, newH: Int): ByteArray {
        if (w == newW && h == newH) return alpha.copyOf()
        val out = ByteArray(newW * newH)
        for (y in 0 until newH) {
            val sy = (y * h / newH).coerceAtMost(h - 1)
            for (x in 0 until newW) out[y * newW + x] = alpha[sy * w + (x * w / newW).coerceAtMost(w - 1)]
        }
        return out
    }
}
