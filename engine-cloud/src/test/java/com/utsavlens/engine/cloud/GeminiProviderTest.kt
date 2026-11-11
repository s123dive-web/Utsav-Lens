package com.utsavlens.engine.cloud

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.AppError
import com.utsavlens.core.errorOrNull
import com.utsavlens.core.getOrNull
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.ImageEditRequest
import com.utsavlens.engine.api.PromptAssistRequest
import com.utsavlens.engine.api.ProviderConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Test
import java.util.Base64

class GeminiProviderTest {
    private val provider = GeminiImageProvider(OkHttpClient())
    private val assist = GeminiPromptAssistProvider(OkHttpClient())
    private val secret = "AIzaTESTKEY-do-not-leak-0123456789"
    private val config = ProviderConfig("https://generativelanguage.googleapis.com", "gemini-nano-banana-2.1")
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private val b64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))

    @Test fun requestBodyMatchesTheDocumentedInteractionsShape() {
        val body = provider.body("gemini-nano-banana-2.1", ImageEditRequest(ApiKey(secret), config, byteArrayOf(9, 8), "make it festive", aspect = Aspect.SQUARE), withFormat = true)
        assertThat(body.getValue("model").jsonPrimitive.content).isEqualTo("gemini-nano-banana-2.1")
        val input = body.getValue("input").jsonArray
        assertThat(input[0].jsonObject.getValue("type").jsonPrimitive.content).isEqualTo("text")
        assertThat(input[0].jsonObject.getValue("text").jsonPrimitive.content).isEqualTo("make it festive")
        assertThat(input[1].jsonObject.getValue("type").jsonPrimitive.content).isEqualTo("image")
        assertThat(input[1].jsonObject.getValue("mime_type").jsonPrimitive.content).isEqualTo("image/jpeg")
        assertThat(input[1].jsonObject.getValue("data").jsonPrimitive.content).isEqualTo(Base64.getEncoder().encodeToString(byteArrayOf(9, 8)))
        assertThat(body.getValue("response_format").jsonObject.getValue("aspect_ratio").jsonPrimitive.content).isEqualTo("1:1")
        // The key is a header only: it must never be serialised into the body.
        assertThat(body.toString()).doesNotContain(secret)
        val plain = provider.body("m", ImageEditRequest(ApiKey(secret), config, byteArrayOf(1), "p"), withFormat = false)
        assertThat(plain.containsKey("response_format")).isFalse()
    }

    @Test fun findsTheLastImageBlockWhereverItIsNested() {
        val nested = provider.parse(obj("""{"id":"i1","status":"completed","outputs":[{"type":"text","text":"ok"},{"type":"image","mime_type":"image/png","data":"$b64"}]}"""))
        assertThat(nested.getOrNull()!!.imageBytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(nested.getOrNull()!!.mimeType).isEqualTo("image/png")
        val deep = provider.parse(obj("""{"steps":[{"content":[{"type":"image","data":"AAAA"}]},{"content":[{"type":"image","data":"$b64"}]}]}"""))
        assertThat(deep.getOrNull()!!.imageBytes).isEqualTo(byteArrayOf(1, 2, 3))
        val flat = provider.parse(obj("""{"output_image":{"data":"$b64","mime_type":"image/jpeg"}}"""))
        assertThat(flat.getOrNull()!!.imageBytes).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test fun refusalsAreSafetyErrorsAndMissingImagesAreBadResponses() {
        assertThat(provider.parse(obj("""{"status":"blocked","outputs":[]}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.SAFETY_BLOCK)
        assertThat(provider.parse(obj("""{"outputs":[{"type":"text","text":"cannot"}],"finish_reason":"IMAGE_SAFETY"}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.SAFETY_BLOCK)
        assertThat(provider.parse(obj("""{"status":"completed","outputs":[{"type":"text","text":"only text"}]}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
        assertThat(provider.parse(obj("""{"outputs":[{"type":"image","data":"%%%not-base64"}]}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
        // A user prompt that merely mentions "safety" must not be mistaken for a refusal.
        assertThat(provider.parse(obj("""{"status":"completed","outputs":[{"type":"text","text":"safety first"}]}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
    }

    @Test fun statusMappingAndRetryability() {
        assertThat(HttpErrors.fromStatus(401).kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(403).kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(400, "INVALID_ARGUMENT API_KEY_INVALID").kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(400, "INVALID_ARGUMENT").kind).isEqualTo(AppError.Kind.INVALID_INPUT)
        assertThat(HttpErrors.fromStatus(404).kind).isEqualTo(AppError.Kind.INVALID_INPUT)
        assertThat(HttpErrors.fromStatus(429).kind).isEqualTo(AppError.Kind.QUOTA)
        assertThat(HttpErrors.fromStatus(429).retryable).isFalse()
        assertThat(HttpErrors.fromStatus(401).retryable).isFalse()
        for (code in listOf(500, 502, 503, 504)) {
            assertThat(HttpErrors.fromStatus(code).kind).isEqualTo(AppError.Kind.SERVER)
            assertThat(HttpErrors.fromStatus(code).retryable).isTrue()
        }
        assertThat(HttpErrors.fromException(java.net.UnknownHostException("x")).kind).isEqualTo(AppError.Kind.NO_NETWORK)
        assertThat(HttpErrors.fromException(java.net.SocketTimeoutException()).kind).isEqualTo(AppError.Kind.TIMEOUT)
        assertThat(HttpErrors.fromException(java.net.SocketTimeoutException()).retryable).isFalse()
    }

    @Test fun rejectsNonHttpsBaseUrlAndBadModelIdsWithoutAnyNetworkCall() = runBlocking {
        assertThat(provider.testKey(ApiKey(secret), ProviderConfig("http://insecure.example", "m")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.INVALID_INPUT)
        val badModel = provider.edit(ImageEditRequest(ApiKey(secret), ProviderConfig("https://ok.example", "../../evil"), byteArrayOf(1), "p"))
        assertThat(badModel.errorOrNull()!!.kind).isEqualTo(AppError.Kind.INVALID_INPUT)
    }

    @Test fun errorsNeverContainTheKey() {
        val error = HttpErrors.fromStatus(400, "INVALID_ARGUMENT API_KEY_INVALID")
        assertThat(error.toString()).doesNotContain(secret)
        assertThat(ApiKey(secret).toString()).doesNotContain(secret)
    }

    @Test fun promptAssistReadsTextAndSanitisesIt() {
        val ok = assist.parse(obj("""{"outputs":[{"type":"text","text":"**Warm** golden light\nfrom the left, `diyas` near the feet."}]}"""))
        assertThat(ok.getOrNull()).isEqualTo("Warm golden light from the left, diyas near the feet.")
        assertThat(assist.parse(obj("""{"outputs":[]}""")).errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
        assertThat(GeminiPromptAssistProvider.sanitize("x".repeat(2000)).length).isEqualTo(GeminiPromptAssistProvider.MAX_CHARS)
        val instruction = GeminiPromptAssistProvider.instruction(PromptAssistRequest(ApiKey(secret), config, byteArrayOf(1), "Diwali", "base prompt", changeOutfit = true))
        assertThat(instruction).contains("Diwali")
        assertThat(instruction).contains("outfit colours")
        assertThat(instruction).doesNotContain(secret)
    }
}
