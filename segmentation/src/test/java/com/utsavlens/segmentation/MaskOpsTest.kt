package com.utsavlens.segmentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskOpsTest {
    private fun square(size: Int, from: Int, to: Int) = ByteArray(size * size) { i ->
        val x = i % size; val y = i / size
        if (x in from until to && y in from until to) 255.toByte() else 0
    }

    @Test fun coverageCountsConfidentPixels() {
        assertThat(MaskOps.coverage(square(10, 0, 5))).isWithin(1e-6f).of(0.25f)
        assertThat(MaskOps.coverage(ByteArray(0))).isEqualTo(0f)
    }

    @Test fun personFoundByFaceOrByCoverage() {
        val empty = ByteArray(100)
        assertThat(MaskOps.personFound(empty, faces = 0)).isFalse()
        assertThat(MaskOps.personFound(empty, faces = 1)).isTrue()
        assertThat(MaskOps.personFound(square(10, 0, 5), faces = 0)).isTrue()
    }

    @Test fun featherSoftensEdgesButKeepsInteriorAndExterior() {
        val size = 40
        val out = MaskOps.feather(square(size, 10, 30), size, size, radius = 2)
        fun at(x: Int, y: Int) = out[y * size + x].toInt() and 0xFF
        assertThat(at(20, 20)).isEqualTo(255)
        assertThat(at(0, 0)).isEqualTo(0)
        assertThat(at(10, 20)).isIn(1..254)
        assertThat(at(29, 20)).isIn(1..254)
    }

    @Test fun featherWithZeroRadiusIsACopy() {
        val src = square(8, 2, 6)
        val out = MaskOps.feather(src, 8, 8, 0)
        assertThat(out).isEqualTo(src)
        assertThat(out).isNotSameInstanceAs(src)
    }

    @Test fun tightenPushesValuesToExtremes() {
        val out = MaskOps.tighten(byteArrayOf(0, 40, 128.toByte(), 220.toByte(), 255.toByte()))
        assertThat(out.map { it.toInt() and 0xFF }).containsExactly(0, 0, 128, 255, 255).inOrder()
    }

    @Test fun confidenceIsClampedAndScaled() {
        val out = MaskOps.fromConfidence(floatArrayOf(-1f, 0f, 0.5f, 1f, 3f))
        assertThat(out.map { it.toInt() and 0xFF }).containsExactly(0, 0, 128, 255, 255).inOrder()
    }

    @Test fun resizeKeepsShape() {
        val out = MaskOps.resize(square(4, 0, 2), 4, 4, 8, 8)
        assertThat(MaskOps.coverage(out)).isWithin(1e-6f).of(0.25f)
    }
}
