package com.utsavlens.core.settings

import kotlinx.serialization.Serializable

/**
 * All user settings as one typed, versioned document. Stored as JSON; loaded through
 * [SettingsCodec] which migrates old versions and deep-merges onto current defaults.
 * API keys are NOT stored here (see the encrypted key store in :data).
 */
@Serializable
data class AppSettings(
    val schemaVersion: Int = CURRENT_VERSION,
    val language: String = "system",
    val defaultEngine: String = "lite",
    val showMaskDebug: Boolean = false,
    val rightsNoticeSeen: Boolean = false,
    val cloud: CloudSettings = CloudSettings(),
    val local: LocalAiSettings = LocalAiSettings(),
    val promptAssist: PromptAssistSettings = PromptAssistSettings(),
    val lastOptions: StoredOptions = StoredOptions(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class CloudSettings(
    val providerId: String = "gemini",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val modelId: String = DEFAULT_GEMINI_IMAGE_MODEL,
    val uploadMaxEdgePx: Int = 1536,
    val timeoutSeconds: Int = 120,
    /** Provider ids for which the user accepted the "photo leaves the device" notice. */
    val uploadConsentProviders: List<String> = emptyList(),
) {
    companion object {
        /** Primary model of the official image generation guide (verified 2026-11-11). */
        const val DEFAULT_GEMINI_IMAGE_MODEL = "gemini-nano-banana-2.1"
    }
}

@Serializable
data class LocalAiSettings(
    /** Download the model only on Wi-Fi / unmetered networks. */
    val wifiOnly: Boolean = true,
    /** LCM denoising steps (2..8). More steps = slower, slightly more detail. */
    val steps: Int = 4,
)

@Serializable
data class PromptAssistSettings(
    val enabled: Boolean = false,
    val providerId: String = "gemini",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val modelId: String = DEFAULT_TEXT_MODEL,
) {
    companion object {
        /** Model used in the official Interactions API guide (verified 2026-11-11). */
        const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"
    }
}

@Serializable
data class StoredOptions(
    val keepBackground: Boolean = false,
    val changeOutfit: Boolean = false,
    val strength: Float = 0.7f,
    val aspect: String = "original",
    val variations: Int = 1,
)
