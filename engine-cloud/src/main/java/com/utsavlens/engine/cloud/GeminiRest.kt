package com.utsavlens.engine.cloud

import com.utsavlens.core.AppError
import com.utsavlens.core.AppResult
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for the Gemini API "Interactions" endpoint
 * (POST {base}/v1beta/interactions, shape verified against the official guides on 2026-11-11).
 * The key travels only in the `x-goog-api-key` header, never in the URL; nothing is logged.
 */
internal class GeminiRest(private val baseClient: OkHttpClient) {
    val json = Json { ignoreUnknownKeys = true }

    fun baseUrl(config: ProviderConfig): String? = config.baseUrl.trim().trimEnd('/').takeIf { it.startsWith("https://") }

    fun modelId(config: ProviderConfig): String? =
        config.modelId.trim().removePrefix("models/").takeIf { MODEL_REGEX.matches(it) }

    /** Key check that does not depend on the model ID and sends no user content. */
    suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit> {
        val base = baseUrl(config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "base url"))
        val request = Request.Builder().url("$base/v1beta/models?pageSize=1").header(KEY_HEADER, key.reveal()).get().build()
        return execute(request, 30) { AppResult.Success(Unit) }
    }

    suspend fun <T> interact(key: ApiKey, config: ProviderConfig, body: JsonObject, onOk: (JsonObject) -> AppResult<T>): AppResult<T> {
        val base = baseUrl(config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "base url"))
        val request = Request.Builder()
            .url("$base/v1beta/interactions")
            .header(KEY_HEADER, key.reveal())
            .header("Api-Revision", API_REVISION)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return execute(request, config.timeoutSeconds) { text ->
            val root = try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return@execute AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "not json"))
            onOk(root)
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

    /** Extracts only the short machine status (e.g. RESOURCE_EXHAUSTED) plus a fixed API-key hint. */
    private fun providerStatus(body: String): String = try {
        val error = (json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
        val status = (error?.get("status") as? JsonPrimitive)?.content.orEmpty()
        val keyProblem = (error?.get("message") as? JsonPrimitive)?.content?.contains("API key", ignoreCase = true) == true
        if (keyProblem) "$status API_KEY_INVALID" else status
    } catch (e: Exception) {
        ""
    }

    companion object {
        const val KEY_HEADER = "x-goog-api-key"
        const val API_REVISION = "2026-05-20"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val MODEL_REGEX = Regex("^[A-Za-z0-9._\\-]{1,100}$")
        private val SAFETY_WORDS = listOf("safety", "blocked", "prohibited", "blocklist", "policy")

        /**
         * Finds every content block `{"type": <type>, ...}` anywhere in the response, in document
         * order. The guides only name the SDK convenience accessors (output_image / output_text),
         * so the parser does not depend on the exact nesting of the REST response.
         */
        fun blocks(root: JsonElement, type: String, out: MutableList<JsonObject> = ArrayList()): List<JsonObject> {
            when (root) {
                is JsonObject -> {
                    if ((root["type"] as? JsonPrimitive)?.content == type) out += root
                    root.values.forEach { blocks(it, type, out) }
                }
                is JsonArray -> root.forEach { blocks(it, type, out) }
                else -> Unit
            }
            return out
        }

        /** True when the response says the request was refused for safety / policy reasons. */
        fun looksBlocked(root: JsonObject): Boolean {
            fun scan(e: JsonElement, key: String, depth: Int): Boolean = when {
                depth > 6 -> false
                e is JsonObject -> e.entries.any { (k, v) -> scan(v, k, depth + 1) }
                e is JsonArray -> e.any { scan(it, key, depth + 1) }
                e is JsonPrimitive && e.isString && key.lowercase() in STATUS_KEYS -> SAFETY_WORDS.any { e.content.contains(it, ignoreCase = true) }
                else -> false
            }
            return scan(root, "", 0)
        }

        private val STATUS_KEYS = setOf("status", "finish_reason", "finishreason", "block_reason", "blockreason", "reason", "stop_reason", "code")
    }
}
