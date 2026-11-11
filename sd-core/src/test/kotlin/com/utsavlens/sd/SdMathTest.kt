package com.utsavlens.sd

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

class SdMathTest {
    private val vocab = """{"<|startoftext|>":49406,"<|endoftext|>":49407,"h":1,"i":2,"i</w>":3,"hi</w>":4,"a</w>":5,"lamp</w>":6,"l":7,"a":8,"m":9,"p</w>":10,"la":11,"lam":12,"!</w>":13}"""
    private val merges = "#version: 0.2\nh i</w>\nl a\nla m\nlam p</w>\n"

    @Test fun tokenizerAppliesMergesPadsAndTruncates() {
        val tok = ClipTokenizer(vocab, merges)
        val ids = tok.encode("Hi  a LAMP!")
        assertThat(ids.size).isEqualTo(77)
        assertThat(ids.take(6)).containsExactly(49406, 4, 5, 6, 13, 49407).inOrder()
        assertThat(ids.drop(6).all { it == 49407 }).isTrue()
        val long = tok.encode((1..200).joinToString(" ") { "hi" })
        assertThat(long.size).isEqualTo(77)
        assertThat(long.first()).isEqualTo(49406)
        assertThat(long.last()).isEqualTo(49407)
        assertThat(long[75]).isEqualTo(4)
    }

    @Test fun tokenizerHandlesUnknownAndEmptyText() {
        val tok = ClipTokenizer(vocab, merges)
        assertThat(tok.encode("").take(2)).containsExactly(49406, 49407).inOrder()
        assertThat(tok.encode("zzz").size).isEqualTo(77)
    }

    @Test fun lcmTimestepsMatchReference() {
        assertThat(LcmScheduler(4).timesteps.toList()).containsExactly(999, 759, 499, 259).inOrder()
        assertThat(LcmScheduler(1).timesteps.toList()).containsExactly(999)
        assertThat(LcmScheduler(4, strength = 0.5f).timesteps.toList()).containsExactly(499, 379, 259, 139).inOrder()
        val eight = LcmScheduler(8).timesteps
        assertThat(eight.first()).isEqualTo(999)
        assertThat(eight.toList()).isInStrictOrder(Comparator.reverseOrder<Int>())
    }

    @Test fun alphasDecreaseFromNearOneToNearZero() {
        val s = LcmScheduler(4)
        assertThat(s.alpha(0)).isWithin(1e-3).of(0.99915)
        assertThat(s.alpha(999)).isWithin(1e-3).of(0.00466)
        for (i in 1 until 1000) assertThat(s.alphasCumprod[i]).isLessThan(s.alphasCumprod[i - 1])
    }

    @Test fun perfectNoisePredictionRecoversTheCleanLatent() {
        val s = LcmScheduler(4)
        val random = Random(1)
        val clean = LatentMath.gaussian(64, random)
        val noise = LatentMath.gaussian(64, random)
        val noisy = s.addNoise(clean, noise, s.timesteps[0])
        val result = s.step(0, noisy, noise, LatentMath.gaussian(64, random))
        // At t=999 the boundary condition gives c_out ~ 1, c_skip ~ 0, so denoised ~ x0.
        for (i in clean.indices) assertThat(result.denoised[i]).isWithin(0.02f).of(clean[i])
        val last = s.step(3, noisy, noise, null)
        assertThat(last.prevSample).isSameInstanceAs(last.denoised)
    }

    @Test fun guidanceEmbeddingShape() {
        val e = LcmScheduler.guidanceEmbedding(8f, 256)
        assertThat(e.size).isEqualTo(256)
        assertThat(e.all { it in -1f..1f }).isTrue()
        assertThat(LcmScheduler.guidanceEmbedding(1f, 256).take(128).all { it == 0f }).isTrue()
    }

    @Test fun blendKeepsOriginalWhereMaskIsZero() {
        val gen = FloatArray(8) { 1f }
        val orig = FloatArray(8) { -1f }
        val out = LatentMath.blend(gen, orig, floatArrayOf(1f, 0f), channels = 4)
        assertThat(out.toList()).containsExactly(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f).inOrder()
    }

    @Test fun repaintMaskProtectsPersonAndItsNeighbourCells() {
        val w = 64; val h = 64
        val alpha = ByteArray(w * h) { i -> if (i % w in 24 until 40 && i / w in 24 until 40) 255.toByte() else 0 }
        val m = LatentMath.repaintMask(alpha, w, h, 8, 8, grow = 1)
        fun at(x: Int, y: Int) = m[y * 8 + x]
        assertThat(at(3, 3)).isEqualTo(0f)
        assertThat(at(4, 4)).isEqualTo(0f)
        assertThat(at(2, 2)).isEqualTo(0f)
        assertThat(at(0, 0)).isEqualTo(1f)
        assertThat(at(7, 3)).isEqualTo(1f)
        assertThat(LatentMath.repaintMask(ByteArray(w * h), w, h, 8, 8).all { it == 1f }).isTrue()
    }

    @Test fun generationSizeFollowsAspectInMultiplesOf64() {
        assertThat(LatentMath.generationSize(1000, 1000).toList()).containsExactly(512, 512).inOrder()
        val portrait = LatentMath.generationSize(3000, 4000)
        assertThat(portrait.toList()).containsExactly(448, 576).inOrder()
        val pano = LatentMath.generationSize(8000, 1000)
        assertThat(pano[0]).isEqualTo(704)
        assertThat(pano[1]).isEqualTo(384)
        assertThat(pano.all { it % 64 == 0 }).isTrue()
    }
}
