package com.utsavlens.engine.cloud

import com.utsavlens.core.AppError
import com.utsavlens.core.AppResult
import com.utsavlens.core.settings.CloudSettings
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.ImageEditProvider
import com.utsavlens.engine.api.ImageEditRequest
import com.utsavlens.engine.api.ImageEditResult
import com.utsavlens.engine.api.ProviderConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.Base64

/** Google Gemini image editing through the Interactions API with the user's own key. */
class GeminiImageProvider(client: OkHttpClient) : ImageEditProvider {
    override val id: String = ID
    override val displayName: String = "Google Gemini"
    override val keyHelpUrl: String = "https://aistudio.google.com/app/apikey"
    override val defaultConfig = ProviderConfig(baseUrl = CloudSettings().baseUrl, modelId = CloudSettings.DEFAULT_GEMINI_IMAGE_MODEL)
    override val supportsOutfitChange: Boolean = true

    private val rest = GeminiRest(client)

    override suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit> = rest.testKey(key, config)

    override suspend fun edit(request: ImageEditRequest): AppResult<ImageEditResult> {
        val model = rest.modelId(request.config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "model id"))
        val first = rest.interact(request.key, request.config, body(model, request, withFormat = true), ::parse)
        // If a model rejects response_format, retry once without it rather than failing the job.
        if (first is AppResult.Failure && first.error.kind == AppError.Kind.INVALID_INPUT && first.error.message.startsWith("http 400")) {
            return rest.interact(request.key, request.config, body(model, request, withFormat = false), ::parse)
        }
        return first
    }

    internal fun body(model: String, request: ImageEditRequest, withFormat: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        putJsonArray("input") {
            add(buildJsonObject { put("type", "text"); put("text", request.prompt) })
            add(buildJsonObject {
                put("type", "image")
                put("mime_type", "image/jpeg")
                put("data", Base64.getEncoder().encodeToString(request.imageJpeg))
            })
        }
        if (withFormat) {
            putJsonObject("response_format") {
                put("type", "image")
                put("mime_type", "image/jpeg")
                if (request.aspect != Aspect.ORIGINAL) put("aspect_ratio", request.aspect.key)
            }
        }
    }

    internal fun parse(root: JsonObject): AppResult<ImageEditResult> {
        // The last image block is the result (the guide's `output_image`).
        val image = GeminiRest.blocks(root, "image").lastOrNull { (it["data"] as? JsonPrimitive)?.isString == true }
            ?: (root["output_image"] as? JsonObject)
        val data = (image?.get("data") as? JsonPrimitive)?.content
        if (data.isNullOrEmpty()) {
            return if (GeminiRest.looksBlocked(root)) AppResult.Failure(AppError(AppError.Kind.SAFETY_BLOCK, "blocked"))
            else AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "no image"))
        }
        val bytes = try {
            Base64.getDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            return AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "bad base64"))
        }
        if (bytes.isEmpty()) return AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "empty image"))
        val mime = ((image?.get("mime_type") ?: image?.get("mimeType")) as? JsonPrimitive)?.content ?: "image/jpeg"
        return AppResult.Success(ImageEditResult(bytes, mime))
    }

    companion object {
        const val ID = "gemini"
    }
}
