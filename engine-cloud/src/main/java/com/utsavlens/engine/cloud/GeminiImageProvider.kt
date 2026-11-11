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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Google Gemini image editing over plain REST (generateContent). The key travels only in the
 * `x-goog-api-key` header, never in the URL, and no request or response is logged.
 */
class GeminiImageProvider(private val baseClient: OkHttpClient) : ImageEditProvider {
    override val id: String = ID
    override val displayName: String = "Google Gemini"
    override val keyHelpUrl: String = "https://aistudio.google.com/app/apikey"
    override val defaultConfig = ProviderConfig(baseUrl = CloudSettings().baseUrl, modelId = CloudSettings.DEFAULT_GEMINI_IMAGE_MODEL)
    override val supportsOutfitChange: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit> {
        val url = modelUrl(config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "base url"))
        val request = Request.Builder().url(url).header(KEY_HEADER, key.reveal()).get().build()
        return execute(request, timeoutSeconds = 30) { AppResult.Success(Unit) }
    }

    override suspend fun edit(request: ImageEditRequest): AppResult<ImageEditResult> {
        val base = modelUrl(request.config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "base url"))
        val first = call(base, request, withImageConfig = request.aspect != Aspect.ORIGINAL)
        // Older image models reject imageConfig with 400: retry once without it rather than failing.
        if (request.aspect != Aspect.ORIGINAL && first is AppResult.Failure && first.error.kind == AppError.Kind.INVALID_INPUT && first.error.message.startsWith("http 400")) {
            return call(base, request, withImageConfig = false)
        }
        return first
    }

    private suspend fun call(modelUrl: String, request: ImageEditRequest, withImageConfig: Boolean): AppResult<ImageEditResult> {
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", request.prompt) })
                        add(buildJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", Base64.getEncoder().encodeToString(request.imageJpeg))
                            }
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("responseModalities", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("TEXT")); add(kotlinx.serialization.json.JsonPrimitive("IMAGE")) })
                if (withImageConfig) putJsonObject("imageConfig") { put("aspectRatio", request.aspect.key) }
            }
        }.toString()
        val http = Request.Builder()
            .url("$modelUrl:generateContent")
            .header(KEY_HEADER, request.key.reveal())
            .post(body.toRequestBody(JSON))
            .build()
        return execute(http, request.config.timeoutSeconds) { text -> parse(text) }
    }

    internal fun parse(text: String): AppResult<ImageEditResult> {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "not json"))
        }
        val blockReason = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.content
        if (!blockReason.isNullOrEmpty()) return AppResult.Failure(AppError(AppError.Kind.SAFETY_BLOCK, blockReason.take(40)))
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "no candidates"))
        val parts = (candidate["content"] as? JsonObject)?.get("parts")?.jsonArray.orEmpty()
        for (part in parts) {
            val inline = (part.jsonObject["inlineData"] ?: part.jsonObject["inline_data"]) as? JsonObject ?: continue
            val data = inline["data"]?.jsonPrimitive?.content ?: continue
            val mime = (inline["mimeType"] ?: inline["mime_type"])?.jsonPrimitive?.content ?: "image/png"
            val bytes = try {
                Base64.getDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                return AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "bad base64"))
            }
            if (bytes.isNotEmpty()) return AppResult.Success(ImageEditResult(bytes, mime))
        }
        val finish = candidate["finishReason"]?.jsonPrimitive?.content.orEmpty()
        return if (SAFETY_FINISH.any { finish.contains(it, ignoreCase = true) }) {
            AppResult.Failure(AppError(AppError.Kind.SAFETY_BLOCK, finish.take(40)))
        } else {
            AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "no image ${finish.take(30)}".trim()))
        }
    }

    private suspend fun <T> execute(request: Request, timeoutSeconds: Int, onOk: (String) -> AppResult<T>): AppResult<T> {
        val client = baseClient.newBuilder()
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        return try {
            // runInterruptible: cancelling the coroutine interrupts the blocking call.
            runInterruptible(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (response.isSuccessful) onOk(text) else AppResult.Failure(HttpErrors.fromStatus(response.code, providerStatus(text)))
                }
            }
        } catch (e: IOException) {
            AppResult.Failure(HttpErrors.fromException(e))
        }
    }

    /** Extracts only the short machine status (e.g. RESOURCE_EXHAUSTED) plus an API-key hint. */
    private fun providerStatus(body: String): String = try {
        val error = json.parseToJsonElement(body).jsonObject["error"] as? JsonObject
        val status = error?.get("status")?.jsonPrimitive?.content.orEmpty()
        val keyProblem = error?.get("message")?.jsonPrimitive?.content?.contains("API key", ignoreCase = true) == true
        if (keyProblem) "$status API_KEY_INVALID" else status
    } catch (e: Exception) {
        ""
    }

    private fun modelUrl(config: ProviderConfig): String? {
        val base = config.baseUrl.trim().trimEnd('/')
        val model = config.modelId.trim().removePrefix("models/")
        if (!base.startsWith("https://") || model.isEmpty() || !MODEL_REGEX.matches(model)) return null
        return "$base/v1beta/models/$model"
    }

    companion object {
        const val ID = "gemini"
        private const val KEY_HEADER = "x-goog-api-key"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val MODEL_REGEX = Regex("^[A-Za-z0-9._\\-]{1,100}$")
        private val SAFETY_FINISH = listOf("SAFETY", "PROHIBITED", "BLOCKLIST", "SPII", "RECITATION")
    }
}
