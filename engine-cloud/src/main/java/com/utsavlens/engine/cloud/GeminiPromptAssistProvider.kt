package com.utsavlens.engine.cloud

import com.utsavlens.core.AppError
import com.utsavlens.core.AppResult
import com.utsavlens.core.settings.PromptAssistSettings
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.PromptAssistProvider
import com.utsavlens.engine.api.PromptAssistRequest
import com.utsavlens.engine.api.ProviderConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import java.util.Base64

/**
 * Optional "Prompt assist": a text/vision model looks at the photo and writes a short visual
 * direction that is appended to the theme prompt. Uses the user's own key; the app works fully
 * without it.
 */
class GeminiPromptAssistProvider(client: OkHttpClient) : PromptAssistProvider {
    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val keyHelpUrl: String = "https://aistudio.google.com/app/apikey"
    override val defaultConfig = ProviderConfig(PromptAssistSettings().baseUrl, PromptAssistSettings.DEFAULT_TEXT_MODEL, 60)

    private val rest = GeminiRest(client)

    override suspend fun testKey(key: ApiKey, config: ProviderConfig): AppResult<Unit> = rest.testKey(key, config)

    override suspend fun refine(request: PromptAssistRequest): AppResult<String> {
        val model = rest.modelId(request.config) ?: return AppResult.Failure(AppError(AppError.Kind.INVALID_INPUT, "model id"))
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("input") {
                add(buildJsonObject { put("type", "text"); put("text", instruction(request)) })
                add(buildJsonObject {
                    put("type", "image")
                    put("mime_type", "image/jpeg")
                    put("data", Base64.getEncoder().encodeToString(request.imageJpeg))
                })
            }
        }
        return rest.interact(request.key, request.config, body, ::parse)
    }

    internal fun parse(root: JsonObject): AppResult<String> {
        val text = GeminiRest.blocks(root, "text")
            .mapNotNull { (it["text"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .lastOrNull { it.isNotBlank() }
            ?: (root["output_text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val clean = sanitize(text.orEmpty())
        return if (clean.isEmpty()) {
            if (GeminiRest.looksBlocked(root)) AppResult.Failure(AppError(AppError.Kind.SAFETY_BLOCK, "blocked"))
            else AppResult.Failure(AppError(AppError.Kind.BAD_RESPONSE, "no text"))
        } else AppResult.Success(clean)
    }

    companion object {
        const val MAX_CHARS = 400

        fun instruction(request: PromptAssistRequest): String = buildString {
            append("You help prepare an instruction for an image-editing model. The photo will be edited for the theme \"")
            append(request.themeName).append("\". Look at the photo and write ONE short paragraph (at most 60 words) of extra visual direction: ")
            append("where festive decorations fit around the people, lighting direction and colour mood that suit this particular scene")
            if (request.changeOutfit) append(", and outfit colours that would suit the scene")
            append(". Do not describe, judge or change anyone's face, body or identity. Do not ask for text in the image. ")
            append("Answer with the paragraph only. Base instruction for context: ").append(request.basePrompt.take(600))
        }

        /** The model's answer is treated as plain data: one line, bounded length, no markup. */
        fun sanitize(text: String): String =
            text.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("[`*_#<>{}\\[\\]]"), "").replace(Regex("\\s{2,}"), " ").trim().take(MAX_CHARS)
    }
}
