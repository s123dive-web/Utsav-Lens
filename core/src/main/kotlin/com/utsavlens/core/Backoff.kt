package com.utsavlens.core

import kotlinx.coroutines.delay
import kotlin.random.Random

/** Exponential backoff with jitter. Retries only failures flagged [AppError.retryable]. */
class BackoffPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 15_000,
    private val random: Random = Random.Default,
) {
    init { require(maxAttempts >= 1) }

    fun delayForAttempt(attempt: Int): Long {
        val exp = (baseDelayMs shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(maxDelayMs)
        return exp / 2 + random.nextLong(exp / 2 + 1)
    }

    suspend fun <T> run(sleep: suspend (Long) -> Unit = { delay(it) }, block: suspend (attempt: Int) -> AppResult<T>): AppResult<T> {
        var attempt = 1
        while (true) {
            val result = block(attempt)
            if (result is AppResult.Success) return result
            val error = (result as AppResult.Failure).error
            if (!error.retryable || attempt >= maxAttempts) return result
            sleep(delayForAttempt(attempt))
            attempt++
        }
    }
}
