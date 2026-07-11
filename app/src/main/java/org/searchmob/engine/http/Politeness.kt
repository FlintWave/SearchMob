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

    /**
     * Suspends until at least [minIntervalMs] has elapsed since the previous request to [host].
     *
     * Thread-safe: the aggregator runs adapters concurrently, so the next slot for the host is
     * RESERVED under the lock (last-slot read and write are one atomic step) and only the sleep
     * happens outside it. Two concurrent callers to the same host therefore get consecutive slots
     * spaced by the interval instead of both slipping through the old read-sleep-write race.
     */
    suspend fun acquire(host: String) {
        val slot =
            synchronized(lastByHost) {
                val current = now()
                val earliest = lastByHost[host]?.let { it + minIntervalMs } ?: current
                val reserved = maxOf(current, earliest)
                lastByHost[host] = reserved
                reserved
            }
        val wait = slot - now()
        if (wait > 0) sleep(wait)
    }

    companion object {
        /**
         * Process-wide shared instance. Registries are rebuilt per search (their config is dynamic),
         * so per-host spacing must live OUTSIDE the registry or consecutive searches would each start
         * from an empty table and the min-interval would never apply across searches - exactly the
         * single-IP hammering this class exists to prevent.
         */
        val SHARED = Politeness()

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
