package com.utsavlens.sd

import java.util.Random

object LatentMath {
    fun gaussian(size: Int, random: Random): FloatArray = FloatArray(size) { random.nextGaussian().toFloat() }

    /**
     * Blended-latent inpainting: where [repaintMask] is 1 the generated latent is used, where it
     * is 0 the (re-noised) original latent is kept. Mask has one value per latent pixel and is
     * applied to all [channels].
     */
    fun blend(generated: FloatArray, original: FloatArray, repaintMask: FloatArray, channels: Int = 4): FloatArray {
        val plane = repaintMask.size
        require(generated.size == plane * channels && original.size == generated.size) { "latent / mask size mismatch" }
        return FloatArray(generated.size) { i ->
            val m = repaintMask[i % plane]
            m * generated[i] + (1f - m) * original[i]
        }
    }

    /**
     * Converts a person mask (0..255, 255 = person) at any resolution into a latent-resolution
     * repaint mask (1 = repaint). A latent cell is protected if ANY person pixel falls into it or
     * into its neighbours ([grow] cells), so the model never paints over the person's outline.
     */
    fun repaintMask(personAlpha: ByteArray, w: Int, h: Int, latentW: Int, latentH: Int, grow: Int = 1): FloatArray {
        val person = BooleanArray(latentW * latentH)
        for (y in 0 until h) {
            val ly = (y * latentH / h).coerceAtMost(latentH - 1)
            for (x in 0 until w) {
                if ((personAlpha[y * w + x].toInt() and 0xFF) > 96) person[ly * latentW + (x * latentW / w).coerceAtMost(latentW - 1)] = true
            }
        }
        return FloatArray(latentW * latentH) { i ->
            val cx = i % latentW
            val cy = i / latentW
            var protect = false
            var dy = -grow
            while (dy <= grow && !protect) {
                var dx = -grow
                while (dx <= grow && !protect) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until latentW && y in 0 until latentH && person[y * latentW + x]) protect = true
                    dx++
                }
                dy++
            }
            if (protect) 0f else 1f
        }
    }

    /** Generation size: multiples of 64 with roughly 512x512 pixels, following the photo's shape. */
    fun generationSize(srcW: Int, srcH: Int, targetArea: Int = 512 * 512, min: Int = 384, max: Int = 704): IntArray {
        val ratio = srcW.toDouble() / srcH.coerceAtLeast(1)
        var w = Math.round(Math.sqrt(targetArea * ratio) / 64.0).toInt() * 64
        var h = Math.round(Math.sqrt(targetArea / ratio) / 64.0).toInt() * 64
        w = w.coerceIn(min, max)
        h = h.coerceIn(min, max)
        return intArrayOf(w, h)
    }
}
