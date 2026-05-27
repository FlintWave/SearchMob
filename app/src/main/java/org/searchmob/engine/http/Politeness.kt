package org.searchmob.engine.http

import kotlinx.coroutines.delay

/**
 * Per-host request politeness: enforces a minimum spacing between requests to the same host and
 * computes backoff for retryable rate-limit responses, so a single mobile IP does not hammer an
 * engine and trip its bot-detection.
 */
class Politeness(
    private val minIntervalMs: Long = 1_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val lastByHost = HashMap<String, Long>()

    /** Suspends until at least [minIntervalMs] has elapsed since the previous request to [host]. */
    suspend fun acquire(host: String) {
        val last = lastByHost[host]
        if (last != null) {
            val wait = minIntervalMs - (now() - last)
            if (wait > 0) sleep(wait)
        }
        lastByHost[host] = now()
    }

    companion object {
        /** Backoff delay for a retry on a rate-limit response, or null if not retryable / exhausted. */
        fun backoffMs(
            statusCode: Int,
            attempt: Int,
            baseMs: Long = 500L,
            maxAttempts: Int = 3,
        ): Long? {
            if (statusCode != 429 && statusCode != 503) return null
            if (attempt >= maxAttempts) return null
            return baseMs * (1L shl attempt) // 500, 1000, 2000, ...
        }
    }
}
