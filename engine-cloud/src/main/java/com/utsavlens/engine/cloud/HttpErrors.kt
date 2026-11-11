package com.utsavlens.engine.cloud

import com.utsavlens.core.AppError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps HTTP / IO failures to typed errors. Messages carry only the status code and the provider's
 * short status enum, never response bodies or request headers, so a key cannot leak through them.
 * Only transient server-side failures are marked retryable.
 */
object HttpErrors {
    fun fromStatus(code: Int, providerStatus: String = ""): AppError {
        val msg = "http $code ${providerStatus.take(40)}".trim()
        return when (code) {
            401, 403 -> AppError(AppError.Kind.BAD_KEY, msg)
            400 -> if (providerStatus.contains("API_KEY", ignoreCase = true) || providerStatus.contains("UNAUTHENTICATED", ignoreCase = true)) {
                AppError(AppError.Kind.BAD_KEY, msg)
            } else AppError(AppError.Kind.INVALID_INPUT, msg)
            404 -> AppError(AppError.Kind.INVALID_INPUT, msg)
            429 -> AppError(AppError.Kind.QUOTA, msg)
            408 -> AppError(AppError.Kind.TIMEOUT, msg)
            500, 502, 503, 504 -> AppError(AppError.Kind.SERVER, msg, retryable = true)
            else -> if (code >= 500) AppError(AppError.Kind.SERVER, msg, retryable = true) else AppError(AppError.Kind.BAD_RESPONSE, msg)
        }
    }

    fun fromException(e: IOException): AppError = when (e) {
        is UnknownHostException, is ConnectException -> AppError(AppError.Kind.NO_NETWORK, e.javaClass.simpleName)
        is SocketTimeoutException -> AppError(AppError.Kind.TIMEOUT, e.javaClass.simpleName)
        is InterruptedIOException -> AppError(AppError.Kind.TIMEOUT, e.javaClass.simpleName)
        is SSLException -> AppError(AppError.Kind.NO_NETWORK, e.javaClass.simpleName)
        else -> AppError(AppError.Kind.SERVER, e.javaClass.simpleName, retryable = true)
    }
}
