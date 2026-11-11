package com.utsavlens.engine.api

import com.utsavlens.core.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The only engine surface the UI knows. Implemented by LITE, LOCAL AI and HD CLOUD.
 *
 * Contract for [transform] (enforced by [com.utsavlens.engine.api.testing.EngineContract]):
 * - cold flow; collecting starts the work, cancelling the collector cancels the work;
 * - emits zero or more [TransformProgress.Running] with non-decreasing percent in 0..100;
 * - ends with exactly one terminal event ([TransformProgress.Success] or [TransformProgress.Failure])
 *   and then completes; failures are emitted, never thrown (cancellation excepted);
 * - never modifies or deletes the source media.
 */
interface TransformEngine {
    val id: String
    suspend fun capabilities(): EngineCapabilities
    suspend fun isReady(): Readiness
    fun transform(request: TransformRequest): Flow<TransformProgress>
}

data class EngineCapabilities(
    val outfitChange: Boolean,
    /** True when the engine cannot alter person pixels, so "keep same face" is locked on. */
    val faceAlwaysPreserved: Boolean,
    val maxResolutionPx: Int,
    val needsNetwork: Boolean,
    val needsDownload: Boolean,
    val maxVariations: Int = 4,
)

sealed interface Readiness {
    data object Ready : Readiness
    data class NeedsDownload(val totalBytes: Long) : Readiness
    data object NeedsKey : Readiness
    data class Unsupported(val reason: UnsupportedReason, val detail: String = "") : Readiness
}

enum class UnsupportedReason { LOW_RAM, CHIPSET, ANDROID_VERSION, STORAGE, OTHER }

@Serializable
enum class Aspect(val ratio: Float?) {
    @SerialName("original") ORIGINAL(null),
    @SerialName("1:1") SQUARE(1f),
    @SerialName("4:5") PORTRAIT_4_5(4f / 5f),
    @SerialName("9:16") STORY_9_16(9f / 16f);

    companion object {
        fun fromKey(key: String?): Aspect = when (key) {
            "1:1" -> SQUARE
            "4:5" -> PORTRAIT_4_5
            "9:16" -> STORY_9_16
            else -> ORIGINAL
        }
    }

    val key: String
        get() = when (this) {
            ORIGINAL -> "original"
            SQUARE -> "1:1"
            PORTRAIT_4_5 -> "4:5"
            STORY_9_16 -> "9:16"
        }
}

@Serializable
data class GreetingText(val text: String, val language: String = "en")

@Serializable
data class TransformOptions(
    val keepFace: Boolean = true,
    val keepBackground: Boolean = false,
    val changeOutfit: Boolean = false,
    val strength: Float = 0.7f,
    val aspect: Aspect = Aspect.ORIGINAL,
    val greeting: GreetingText? = null,
    val variations: Int = 1,
    /** Optional extra prompt text (e.g. from Prompt assist). Ignored by LITE. */
    val promptOverride: String? = null,
)

@Serializable
data class TransformRequest(
    val jobId: String,
    /** content:// or file:// URI of the user's original. Read-only for engines. */
    val sourceUri: String,
    val themeId: String,
    val options: TransformOptions = TransformOptions(),
    /** Longest output edge in pixels; engines clamp to their own maximum. */
    val outputMaxEdgePx: Int = 2048,
    val seed: Long = 0L,
    /** When false the engine writes to app cache instead of the gallery (thumbnails, previews). */
    val saveToGallery: Boolean = true,
)

/** Platform-neutral preview frame (ARGB_8888 pixels) so this module stays pure Kotlin. */
class PreviewFrame(val width: Int, val height: Int, val argb: IntArray) {
    init { require(width > 0 && height > 0 && argb.size == width * height) { "bad preview frame" } }
}

enum class TransformStage { LOADING, SEGMENTING, UPLOADING, GENERATING, COMPOSITING, SAVING }

enum class ResultNote { NO_PERSON_FOUND, DOWNSCALED, PROMPT_ASSISTED }

sealed interface TransformProgress {
    data class Running(
        val percent: Int,
        val stage: TransformStage,
        val preview: PreviewFrame? = null,
        val step: Int = 0,
        val totalSteps: Int = 0,
    ) : TransformProgress

    data class Success(val outputUris: List<String>, val notes: Set<ResultNote> = emptySet()) : TransformProgress

    data class Failure(
        val error: AppError,
        /** Every failure that LITE could still satisfy sets this so the UI offers "Use LITE instead". */
        val canFallbackToLite: Boolean = true,
    ) : TransformProgress
}

val TransformProgress.isTerminal: Boolean
    get() = this is TransformProgress.Success || this is TransformProgress.Failure
