package com.utsavlens.sd

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * End-to-end validation with the real downloaded weights. Runs only when -PsdModelDir points at a
 * model directory (see .github/workflows/local-ai-validate.yml); skipped in normal builds.
 */
class RealModelTest {
    private val modelDir = System.getProperty("sd.modelDir").orEmpty()

    @Test fun repaintsBackgroundAndKeepsProtectedArea() {
        assumeTrue("no model directory given", modelDir.isNotEmpty() && File(modelDir, "unet/model.onnx").isFile)
        val w = 512; val h = 512
        // Synthetic photo: blue-grey gradient with a skin-toned "person" block in the middle.
        val image = FloatArray(3 * w * h)
        val alpha = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val person = x in 176 until 336 && y in 128 until 512
            val rgb = if (person) floatArrayOf(0.78f, 0.56f, 0.44f) else floatArrayOf(0.25f + 0.2f * x / w, 0.35f, 0.5f + 0.3f * y / h)
            for (c in 0..2) image[c * w * h + y * w + x] = rgb[c] * 2f - 1f
            if (person) alpha[y * w + x] = 255.toByte()
        }
        val mask = LatentMath.repaintMask(alpha, w, h, w / 8, h / 8)
        val pipeline = SdPipeline(File(modelDir))
        println("SD-VALIDATE model inputs:\n" + pipeline.describe())
        val started = System.currentTimeMillis()
        var lastStage = started
        val out = pipeline.generate(
            SdPipeline.Request(
                image, w, h, mask,
                prompt = "festive Diwali night, glowing clay diyas, marigold garlands, warm golden bokeh lights, fireworks, photorealistic",
                steps = 4, strength = 1f, seed = 42,
            ),
        ) { stage, step, total ->
            val now = System.currentTimeMillis()
            println("SD-VALIDATE progress $stage $step/$total (+${now - lastStage} ms)")
            lastStage = now
        }
        println("SD-VALIDATE total ${(System.currentTimeMillis() - started) / 1000} s on ${Runtime.getRuntime().availableProcessors()} threads")

        assertThat(out.size).isEqualTo(image.size)
        assertThat(out.all { it.isFinite() && it in -1f..1f }).isTrue()
        var keptDiff = 0.0; var keptN = 0; var bgDiff = 0.0; var bgN = 0
        for (y in 0 until h) for (x in 0 until w) for (c in 0..2) {
            val d = abs(out[c * w * h + y * w + x] - image[c * w * h + y * w + x]).toDouble()
            if (x in 200 until 312 && y in 160 until 480) { keptDiff += d; keptN++ }
            if (x < 120 || x > 392) { bgDiff += d; bgN++ }
        }
        println("SD-VALIDATE mean abs diff: protected=${keptDiff / keptN} repainted=${bgDiff / bgN}")
        // Protected area only goes through the VAE round trip: it must stay close to the original.
        assertThat(keptDiff / keptN).isLessThan(0.2)
        // The background must really have been generated, not copied.
        assertThat(bgDiff / bgN).isGreaterThan(0.08)

        val png = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            fun ch(c: Int) = (((out[c * w * h + y * w + x] + 1f) / 2f) * 255f).toInt().coerceIn(0, 255)
            png.setRGB(x, y, (ch(0) shl 16) or (ch(1) shl 8) or ch(2))
        }
        val dir = File(System.getProperty("sd.outDir")).apply { mkdirs() }
        ImageIO.write(png, "png", File(dir, "validate.png"))
        println("SD-VALIDATE wrote ${File(dir, "validate.png")}")
    }
}
