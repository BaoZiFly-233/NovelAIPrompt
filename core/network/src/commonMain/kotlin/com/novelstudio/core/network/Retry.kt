package com.novelstudio.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 指数退避重试（1s → 2s → 4s…），仅重试传输类错误与限流/服务端错误 */
object Retry {

    suspend fun <T> withExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        block: suspend () -> T,
    ): T {
        require(maxAttempts >= 1) { "maxAttempts 至少为 1" }
        var currentDelay = initialDelayMs
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val isLastAttempt = attempt == maxAttempts - 1
                if (isLastAttempt || !shouldRetry(throwable)) throw throwable
            }
            delay(currentDelay)
            currentDelay *= 2
        }
        error("unreachable")
    }

    private fun shouldRetry(throwable: Throwable): Boolean = when {
        throwable is NaiApiException -> NaiApiException.isRetryable(throwable.statusCode)
        else -> isTransportError(throwable)
    }
}
