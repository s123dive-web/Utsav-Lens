package com.utsavlens.engine.api

import com.utsavlens.core.AppResult

/** A secret that cannot leak through toString(), string templates, logs or crash text. */
class ApiKey(private val value: String) {
    fun reveal(): String = value
    val isBlank: Boolean get() = value.isBlank()
    override fun toString(): String = "ApiKey(***)"
    override fun equals(other: Any?): Boolean = other is ApiKey && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

data class ProviderConfig(val baseUrl: String, val modelId: String, val timeoutSeconds: Int = 120)

class ImageEditRequest(
    val key: ApiKey,
    val config: ProviderConfig,
    /** Already downscaled, EXIF-free JPEG bytes. */
    val imageJpeg: ByteArray,
    val prompt: String,
    val negativePrompt: String = "",
    val aspect: Aspect = Aspect.ORIGINAL,
    val seed: Long = 0L,
)

class ImageEditResult(val imageBytes: ByteArray, val mimeType: String)

/**
 * A cloud image-editing backend. The cloud engine only talks to this interface, so new providers
 * (other image APIs, OpenAI-compatible endpoints with a custom base URL) need no UI or engine change.
 * Implementations must return typed failures (never throw for HTTP/IO problems) and must never
 * put the key into an error message.
 */
interface ImageEditProvider {
    val id: String
    val displayName: String
    /** Where the user can create a key. Shown as a link on the key screen. */
    val keyHelpUrl: String
    val defaultConfig: ProviderConfig
    val supportsOutfitChange: Boolean
    suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit>
    suspend fun edit(request: ImageEditRequest): AppResult<ImageEditResult>
}

class PromptAssistRequest(
    val key: ApiKey,
    val config: ProviderConfig,
    val imageJpeg: ByteArray,
    val themeName: String,
    val basePrompt: String,
    val changeOutfit: Boolean,
)

/** Optional text/vision LLM that refines the theme prompt for a specific photo (Phase 9). */
interface PromptAssistProvider {
    val id: String
    val displayName: String
    val keyHelpUrl: String
    val defaultConfig: ProviderConfig
    suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit>
    suspend fun refine(request: PromptAssistRequest): AppResult<String>
}
