package org.searchmob.server

import org.searchmob.service.WorkLock

/**
 * Wraps per-request handling so a short, timed wake-lock is held only while a request is in flight.
 * While idle (no request) no wake-lock is held, preserving near-zero idle battery drain.
 */
interface RequestWakeGuard {
    suspend fun <T> aroundRequest(block: suspend () -> T): T
}

/** No-op guard for tests and contexts without a wake-lock (e.g. `testApplication`). */
object NoopRequestWakeGuard : RequestWakeGuard {
    override suspend fun <T> aroundRequest(block: suspend () -> T): T = block()
}

/** Acquires a timed [WorkLock] around request handling and releases it in `finally`. */
class WakeLockRequestGuard(
    private val lock: WorkLock,
    private val timeoutMs: Long = 60_000L,
) : RequestWakeGuard {
    override suspend fun <T> aroundRequest(block: suspend () -> T): T {
        lock.acquire(timeoutMs)
        try {
            return block()
        } finally {
            lock.release()
        }
    }
}
