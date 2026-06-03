package org.searchmob.update

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import java.util.concurrent.TimeUnit

class UpdateCheckCoordinatorTest {
    private fun proxyClient() = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()

    private fun repo() = PreferencesRepository(InMemoryPreferencesStore())

    private fun releaseJson(tag: String) =
        """{"tag_name":"$tag","html_url":"https://github.com/FlintWave/SearchMob/releases/tag/$tag"}"""

    // ----- isUpdateCheckDue (pure throttle) -----

    @Test
    fun due_whenNeverChecked() {
        assertTrue(isUpdateCheckDue(lastCheckMs = 0, nowMs = 1_000))
    }

    @Test
    fun due_whenIntervalElapsed() {
        val last = 10_000L
        assertTrue(isUpdateCheckDue(last, nowMs = last + UPDATE_CHECK_INTERVAL_MS))
    }

    @Test
    fun notDue_whenWithinInterval() {
        val last = 10_000L
        assertFalse(isUpdateCheckDue(last, nowMs = last + UPDATE_CHECK_INTERVAL_MS - 1))
    }

    // ----- checkIfDue gating -----

    @Test
    fun checkIfDue_returnsInfoWhenEnabledDueAndNewer() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.06.00")))
            server.start()
            try {
                val r = repo()
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                val coordinator =
                    UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 })
                val info = coordinator.checkIfDue()
                assertNotNull(info)
                assertEquals(260600, info!!.latestVersionCode)
                // The attempt advances the throttle even though it succeeded.
                assertEquals(1_000_000L, r.lastUpdateCheckMs())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_returnsNullWhenAlreadyUpToDate() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.05.01")))
            server.start()
            try {
                val r = repo()
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                val coordinator =
                    UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 })
                assertNull(coordinator.checkIfDue())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_doesNotContactNetworkWhenPreferenceOff() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.06.00")))
            server.start()
            try {
                val r = repo()
                r.setUpdateCheckEnabled(false)
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                val coordinator =
                    UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 })
                assertNull(coordinator.checkIfDue())
                // The gate must short-circuit before any request and before stamping the throttle.
                assertNull(
                    "no request must be made when the preference is off",
                    server.takeRequest(500, TimeUnit.MILLISECONDS),
                )
                assertEquals(0L, r.lastUpdateCheckMs())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_doesNotContactNetworkWhenNotDue() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.06.00")))
            server.start()
            try {
                val r = repo()
                r.setLastUpdateCheckMs(1_000_000)
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                // now is only one hour after the last check: not due.
                val coordinator =
                    UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 + 3_600_000 })
                assertNull(coordinator.checkIfDue())
                assertNull("no request when not due", server.takeRequest(500, TimeUnit.MILLISECONDS))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_persistsPendingUpdateWhenNewer() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.06.00")))
            server.start()
            try {
                val r = repo()
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 }).checkIfDue()
                assertEquals("26.06.00", r.pendingUpdateVersion())
                assertTrue(r.pendingUpdateUrl().endsWith("/v26.06.00"))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_clearsPendingUpdateWhenUpToDate() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.05.01")))
            server.start()
            try {
                val r = repo()
                // A stale pending record from a prior check must be cleared once we're current.
                r.setPendingUpdate("26.06.00", "https://example.test/old")
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 1_000_000 }).checkIfDue()
                assertEquals("", r.pendingUpdateVersion())
                assertEquals("", r.pendingUpdateUrl())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun checkIfDue_stampsThrottleEvenOnFailure() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            try {
                val r = repo()
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                val coordinator =
                    UpdateCheckCoordinator(r, checker, currentVersionCode = 260501, nowMs = { 2_000_000 })
                assertNull(coordinator.checkIfDue())
                // A failed network attempt still advances the throttle so it does not hammer on launch.
                assertEquals(2_000_000L, r.lastUpdateCheckMs())
            } finally {
                server.shutdown()
            }
        }
}
