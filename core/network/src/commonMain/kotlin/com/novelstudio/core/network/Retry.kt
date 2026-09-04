package com.novelstudio.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 指数退避重试（1s → 2s → 4s…），仅供明确幂等且不计费的读取操作使用。
 * 图像生成与 Vibe 编码结果可能已在服务端产生，禁止用此工具包装。
 */
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
