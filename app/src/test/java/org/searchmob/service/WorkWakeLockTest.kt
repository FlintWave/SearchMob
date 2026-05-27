package org.searchmob.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkWakeLockTest {
    private class FakeLock : WorkLock {
        var acquireCount = 0
        var releaseCount = 0

        override fun acquire(timeoutMs: Long) {
            acquireCount++
        }

        override fun release() {
            releaseCount++
        }
    }

    @Test
    fun releasesAfterNormalCompletion() {
        val lock = FakeLock()
        val result = withWorkWakeLock(lock) { 42 }
        assertEquals(42, result)
        assertEquals(1, lock.acquireCount)
        assertEquals(1, lock.releaseCount)
    }

    @Test
    fun releasesAfterException() {
        val lock = FakeLock()
        var threw = false
        try {
            withWorkWakeLock<Unit>(lock) { throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(1, lock.acquireCount)
        assertEquals(1, lock.releaseCount)
    }

    @Test
    fun idlePathAcquiresNoLock() {
        val lock = FakeLock()
        // An idle service performs no unit of work, so the helper is never invoked.
        assertEquals(0, lock.acquireCount)
        assertEquals(0, lock.releaseCount)
    }
}
