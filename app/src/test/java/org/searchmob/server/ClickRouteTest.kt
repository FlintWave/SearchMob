package org.searchmob.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.PersonalizationPreferences
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import java.net.HttpURLConnection
import java.net.URL

/**
 * The owner-only `/click` learning route, exercised against a real loopback server (so requests
 * genuinely originate from 127.0.0.1, i.e. the owner). Mirrors the desktop `test_click_route`: an
 * owner click records the skip-above update and redirects to the recorded URL; a disabled owner gets
 * plain links; a forged/stale `rid` or bad `pos` fails safe without redirecting off-site.
 */
class ClickRouteTest {
    private class FakeStore : PreferencesStore {
        private val map = mutableMapOf<String, String>()

        override fun observe(): Flow<Preferences> = flowOf(map.toMap())

        override suspend fun getAll(): Preferences = map.toMap()

        override suspend fun get(key: String): String? = map[key]

        override suspend fun put(
            key: String,
            value: String,
        ) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override suspend fun clear() = map.clear()
    }

    private class ThreeResults : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(
                SearchResult(title = "One", url = "https://a.example/1", snippet = "s", engine = "e"),
                SearchResult(title = "Two", url = "https://b.example/2", snippet = "s", engine = "e"),
                SearchResult(title = "Three", url = "https://liked.example/3", snippet = "s", engine = "e"),
            )
    }

    private fun waitForHealthz(
        port: Int,
        attempts: Int = 30,
    ): Int {
        repeat(attempts) {
            try {
                val c = URL("http://$LOOPBACK_HOST:$port/healthz").openConnection() as HttpURLConnection
                c.connectTimeout = 500
                c.readTimeout = 500
                val code = c.responseCode
                c.disconnect()
                return code
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        throw AssertionError("server did not respond on $port")
    }

    private fun get(
        port: Int,
        path: String,
    ): Triple<Int, String, String?> {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        val code = c.responseCode
        val body =
            (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        val location = c.getHeaderField("Location")
        c.disconnect()
        return Triple(code, body, location)
    }

    private fun ridFrom(html: String): String {
        val marker = "/click?rid="
        val start = html.indexOf(marker) + marker.length
        val amp = html.indexOf("&", start)
        return html.substring(start, amp)
    }

    @Test
    fun ownerPageUsesClickLinksAndRecordsThenRedirects() {
        val store = FakeStore()
        val personalization = PersonalizationPreferences(store)
        val server =
            SearchServer(
                provider = ThreeResults(),
                personalizationPreferences = personalization,
                personalizationEnabled = { true },
            )
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (_, html, _) = get(port, "/search?q=python%20list")
            assertTrue("expected /click links", html.contains("/click?rid="))
            val rid = ridFrom(html)

            // Click the third result; the two above it are skipped.
            val (code, _, location) = get(port, "/click?rid=$rid&pos=2")
            assertTrue("expected a redirect, got $code", code in 300..399)
            assertEquals("https://liked.example/3", location)

            val model = runBlocking { personalization.load() }
            assertEquals(model.config.alphaPrior + 1, model.domains["liked.example"]!!.alpha, 1e-9)
            assertEquals(model.config.betaPrior + 1, model.domains["a.example"]!!.beta, 1e-9)
            assertFalse(model.domains.containsKey("c.example"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun disabledOwnerGetsPlainLinks() {
        val server =
            SearchServer(
                provider = ThreeResults(),
                personalizationPreferences = PersonalizationPreferences(FakeStore()),
                personalizationEnabled = { false },
            )
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (_, html, _) = get(port, "/search?q=hi")
            assertFalse(html.contains("/click?rid="))
            assertTrue(html.contains("href=\"https://liked.example/3\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun forgedOrStaleRidAndBadPosFailSafe() {
        val store = FakeStore()
        val personalization = PersonalizationPreferences(store)
        val server =
            SearchServer(
                provider = ThreeResults(),
                personalizationPreferences = personalization,
                personalizationEnabled = { true },
            )
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val rid = ridFrom(get(port, "/search?q=hi").second)

            // Unknown rid, out-of-range pos, and a non-numeric pos all redirect home and learn nothing.
            for (path in listOf("/click?rid=forged&pos=0", "/click?rid=$rid&pos=99", "/click?rid=$rid&pos=x")) {
                val (code, _, location) = get(port, path)
                assertTrue("expected a redirect, got $code for $path", code in 300..399)
                assertEquals("/", location)
            }
            assertTrue(runBlocking { personalization.load() }.isEmpty())
        } finally {
            server.stop()
        }
    }
}
