package com.utsavlens.engine.cloud

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.AppError
import com.utsavlens.core.errorOrNull
import com.utsavlens.core.getOrNull
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.ProviderConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.util.Base64

class GeminiProviderTest {
    private val provider = GeminiImageProvider(OkHttpClient())
    private val secret = "AIzaTESTKEY-do-not-leak-0123456789"

    @Test fun parsesInlineImage() {
        val b64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val ok = provider.parse("""{"candidates":[{"content":{"parts":[{"text":"here"},{"inlineData":{"mimeType":"image/png","data":"$b64"}}]},"finishReason":"STOP"}]}""")
        assertThat(ok.getOrNull()!!.imageBytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(ok.getOrNull()!!.mimeType).isEqualTo("image/png")
    }

    @Test fun promptBlockAndSafetyFinishAreSafetyErrors() {
        assertThat(provider.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}""").errorOrNull()!!.kind).isEqualTo(AppError.Kind.SAFETY_BLOCK)
        assertThat(provider.parse("""{"candidates":[{"finishReason":"IMAGE_SAFETY"}]}""").errorOrNull()!!.kind).isEqualTo(AppError.Kind.SAFETY_BLOCK)
        assertThat(provider.parse("""{"candidates":[{"content":{"parts":[{"text":"no"}]},"finishReason":"PROHIBITED_CONTENT"}]}""").errorOrNull()!!.kind)
            .isEqualTo(AppError.Kind.SAFETY_BLOCK)
    }

    @Test fun missingImageOrGarbageIsBadResponse() {
        assertThat(provider.parse("<html>").errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
        assertThat(provider.parse("{}").errorOrNull()!!.kind).isEqualTo(AppError.Kind.BAD_RESPONSE)
        assertThat(provider.parse("""{"candidates":[{"content":{"parts":[{"text":"only text"}]},"finishReason":"STOP"}]}""").errorOrNull()!!.kind)
            .isEqualTo(AppError.Kind.BAD_RESPONSE)
    }

    @Test fun statusMappingAndRetryability() {
        assertThat(HttpErrors.fromStatus(401).kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(403).kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(400, "INVALID_ARGUMENT API_KEY_INVALID").kind).isEqualTo(AppError.Kind.BAD_KEY)
        assertThat(HttpErrors.fromStatus(400, "INVALID_ARGUMENT").kind).isEqualTo(AppError.Kind.INVALID_INPUT)
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

    @Test fun rejectsNonHttpsBaseUrlWithoutAnyNetworkCall() = runBlocking {
        val result = provider.testKey(ApiKey(secret), ProviderConfig("http://insecure.example", "gemini-x"))
        assertThat(result.errorOrNull()!!.kind).isEqualTo(AppError.Kind.INVALID_INPUT)
        val bad = provider.testKey(ApiKey(secret), ProviderConfig("https://ok.example", "../../evil"))
        assertThat(bad.errorOrNull()!!.kind).isEqualTo(AppError.Kind.INVALID_INPUT)
    }

    @Test fun errorsNeverContainTheKey() {
        val body = """{"error":{"code":400,"message":"API key not valid: $secret","status":"INVALID_ARGUMENT"}}"""
        // providerStatus only forwards the status enum and a fixed hint, never the message text.
        val error = HttpErrors.fromStatus(400, "INVALID_ARGUMENT API_KEY_INVALID")
        assertThat(error.toString()).doesNotContain(secret)
        assertThat(body).contains(secret)
        assertThat(ApiKey(secret).toString()).doesNotContain(secret)
    }
}
