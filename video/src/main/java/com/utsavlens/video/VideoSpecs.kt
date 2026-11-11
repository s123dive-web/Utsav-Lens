package com.utsavlens.video

import kotlinx.serialization.Serializable

/** Festive slideshow from 1..20 already-transformed photos. No generative video anywhere. */
@Serializable
data class SlideshowSpec(
    val photoUris: List<String>,
    val themeId: String,
    /** "9:16" or "1:1". */
    val aspect: String = "9:16",
    val secondsPerPhoto: Float = 3f,
    val greeting: String? = null,
    /** Optional audio the user picked from their own device. */
    val audioUri: String? = null,
    val seed: Long = 1L,
)

/** Theme border, animated overlays, colour grade and greeting applied to the user's own clip. */
@Serializable
data class VideoOverlaySpec(
    val sourceUri: String,
    val themeId: String,
    val strength: Float = 0.7f,
    val greeting: String? = null,
    val seed: Long = 1L,
)

/** Pure timing maths for the slideshow, unit-tested on the JVM. */
class SlideshowTimeline(photoCount: Int, secondsPerPhoto: Float) {
    val count: Int = photoCount.coerceIn(MIN_PHOTOS, MAX_PHOTOS)
    val slideUs: Long = (secondsPerPhoto.coerceIn(MIN_SECONDS, MAX_SECONDS) * 1_000_000L).toLong()
    val totalUs: Long = slideUs * count

    /** 0..1 darkness of the dip-to-colour transition at [timeUs] (1 exactly on a slide boundary). */
    fun transitionAlpha(timeUs: Long): Float {
        if (count <= 1) return 0f
        val t = timeUs.coerceIn(0, totalUs)
        val nearest = ((t + slideUs / 2) / slideUs) * slideUs
        if (nearest <= 0L || nearest >= totalUs) return 0f
        val distance = kotlin.math.abs(t - nearest).toFloat()
        return (1f - distance / FADE_US).coerceIn(0f, 1f)
    }

    /** Slow zoom factor within the current slide (Ken Burns), 1.0 → 1.08. */
    fun zoom(timeUs: Long): Float {
        val within = ((timeUs % slideUs) + slideUs) % slideUs
        return 1f + 0.08f * within.toFloat() / slideUs
    }

    /** Greeting fades in during the first second and stays. */
    fun greetingAlpha(timeUs: Long): Float = (timeUs / 1_000_000f).coerceIn(0f, 1f)

    companion object {
        const val MIN_PHOTOS = 1
        const val MAX_PHOTOS = 20
        const val MIN_SECONDS = 1.5f
        const val MAX_SECONDS = 6f
        const val FADE_US = 350_000f

        fun outputSize(aspect: String): Pair<Int, Int> = if (aspect == "1:1") 1080 to 1080 else 1080 to 1920
    }
}
