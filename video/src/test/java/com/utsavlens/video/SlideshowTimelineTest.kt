package com.utsavlens.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SlideshowTimelineTest {
    @Test fun clampsPhotoCountAndDuration() {
        assertThat(SlideshowTimeline(0, 3f).count).isEqualTo(1)
        assertThat(SlideshowTimeline(99, 3f).count).isEqualTo(20)
        assertThat(SlideshowTimeline(3, 100f).slideUs).isEqualTo(6_000_000L)
        assertThat(SlideshowTimeline(4, 3f).totalUs).isEqualTo(12_000_000L)
    }

    @Test fun transitionPeaksOnInnerBoundariesOnly() {
        val t = SlideshowTimeline(3, 3f)
        assertThat(t.transitionAlpha(3_000_000)).isEqualTo(1f)
        assertThat(t.transitionAlpha(6_000_000)).isEqualTo(1f)
        assertThat(t.transitionAlpha(1_500_000)).isEqualTo(0f)
        assertThat(t.transitionAlpha(0)).isEqualTo(0f)
        assertThat(t.transitionAlpha(9_000_000)).isEqualTo(0f)
        assertThat(t.transitionAlpha(3_175_000)).isWithin(0.01f).of(0.5f)
        assertThat(SlideshowTimeline(1, 3f).transitionAlpha(1_000_000)).isEqualTo(0f)
    }

    @Test fun zoomRestartsEverySlide() {
        val t = SlideshowTimeline(2, 2f)
        assertThat(t.zoom(0)).isEqualTo(1f)
        assertThat(t.zoom(1_000_000)).isWithin(1e-4f).of(1.04f)
        assertThat(t.zoom(2_000_000)).isEqualTo(1f)
    }

    @Test fun outputSizes() {
        assertThat(SlideshowTimeline.outputSize("9:16")).isEqualTo(1080 to 1920)
        assertThat(SlideshowTimeline.outputSize("1:1")).isEqualTo(1080 to 1080)
    }

    @Test fun greetingFadesIn() {
        val t = SlideshowTimeline(2, 3f)
        assertThat(t.greetingAlpha(0)).isEqualTo(0f)
        assertThat(t.greetingAlpha(500_000)).isWithin(1e-4f).of(0.5f)
        assertThat(t.greetingAlpha(5_000_000)).isEqualTo(1f)
    }
}
