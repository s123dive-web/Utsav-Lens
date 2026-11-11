package com.utsavlens.engine.lite

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CropMathTest {
    @Test fun nullRatioKeepsWholeImage() {
        assertThat(CropMath.cropRect(400, 300, null).toList()).containsExactly(0, 0, 400, 300).inOrder()
    }

    @Test fun squareFromLandscapeIsCentred() {
        assertThat(CropMath.cropRect(400, 300, 1f).toList()).containsExactly(50, 0, 300, 300).inOrder()
    }

    @Test fun storyFromLandscapeFollowsFocusButStaysInside() {
        val r = CropMath.cropRect(1600, 900, 9f / 16f, focusX = 0.95f, focusY = 0.5f)
        assertThat(r[3]).isEqualTo(900)
        assertThat(r[2]).isEqualTo(506)
        assertThat(r[0] + r[2]).isEqualTo(1600)
    }

    @Test fun portraitFourFiveFromTallImage() {
        val r = CropMath.cropRect(900, 1600, 4f / 5f, 0.5f, 0f)
        assertThat(r.toList()).containsExactly(0, 0, 900, 1125).inOrder()
    }

    @Test fun centroidOfLeftHalfMask() {
        val w = 64; val h = 64
        val alpha = ByteArray(w * h) { i -> if (i % w < 16) 255.toByte() else 0 }
        val c = CropMath.centroid(alpha, w, h)
        assertThat(c[0]).isWithin(0.03f).of(7.5f / 64f)
        assertThat(c[1]).isWithin(0.03f).of(0.5f)
        assertThat(CropMath.centroid(ByteArray(16), 4, 4).toList()).containsExactly(0.5f, 0.5f)
    }
}
