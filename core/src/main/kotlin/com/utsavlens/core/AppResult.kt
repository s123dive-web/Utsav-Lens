package com.utsavlens.core

/** Typed result used across module boundaries instead of throwing. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Machine-readable failure kinds. [message] is for logs only and must never contain secrets. */
data class AppError(val kind: Kind, val message: String = "", val retryable: Boolean = false) {
    enum class Kind {
        NO_NETWORK, BAD_KEY, QUOTA, SAFETY_BLOCK, TIMEOUT, SERVER, BAD_RESPONSE,
        OUT_OF_MEMORY, UNSUPPORTED, NOT_READY, IO, CANCELLED, INVALID_INPUT, UNKNOWN,
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
