package org.searchmob.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.http.Politeness

class PolitenessTest {
    @Test
    fun spacesRequestsToSameHost() =
        runTest {
            var now = 1_000L
            val slept = mutableListOf<Long>()
            val politeness =
                Politeness(minIntervalMs = 1_000, now = { now }, sleep = {
                    slept.add(it)
                    now += it
                })
            politeness.acquire("host")
            now += 200
            politeness.acquire("host")
            assertEquals(listOf(800L), slept)
        }

    @Test
    fun noSleepWhenAlreadySpaced() =
        runTest {
            var now = 0L
            val slept = mutableListOf<Long>()
            val politeness = Politeness(minIntervalMs = 1_000, now = { now }, sleep = { slept.add(it) })
            politeness.acquire("h")
            now += 1_500
            politeness.acquire("h")
            assertTrue(slept.isEmpty())
        }

    @Test
    fun backoffOnlyForRetryableCodes() {
        assertNull(Politeness.backoffMs(200, 0))
        assertEquals(500L, Politeness.backoffMs(429, 0))
        assertEquals(1_000L, Politeness.backoffMs(503, 1))
        assertNull(Politeness.backoffMs(429, 3))
    }
}
