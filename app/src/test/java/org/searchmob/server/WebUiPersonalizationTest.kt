package org.searchmob.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules
import java.net.HttpURLConnection
import java.net.URL

/**
 * The served-UI personalization controls and their loopback-only mutation routes, exercised against
 * a real loopback server (so requests are genuinely from 127.0.0.1, i.e. the owner). Mirrors the
 * desktop `test_rules_endpoints`.
 */
class WebUiPersonalizationTest {
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

    private class OneResultProvider : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A page", url = "https://news.example/x", snippet = "s", engine = "e"))
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
    ): Pair<Int, String> {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        c.disconnect()
        return code to body
    }

    private fun postForm(
        port: Int,
        path: String,
        form: String,
    ): Int {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.instanceFollowRedirects = false
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(form.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        return code
    }

    // OkHttp variant: HttpURLConnection silently drops `Origin` (it is on its restricted-header list),
    // so the CSRF/same-origin tests below post through OkHttp, which sends the header verbatim.
    private fun postFormWithOrigin(
        port: Int,
        path: String,
        form: String,
        origin: String,
    ): Int {
        val client = OkHttpClient.Builder().followRedirects(false).build()
        val body = form.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request =
            Request.Builder()
                .url("http://$LOOPBACK_HOST:$port$path")
                .header("Origin", origin)
                .post(body)
                .build()
        return client.newCall(request).execute().use { it.code }
    }

    @Test
    fun loopbackPostSetsAndResetsADomainRule() {
        val prefs = RankingPreferences(FakeStore())
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))

            val code = postForm(port, "/rules/domain", "domain=news.example&action=BLOCK")
            assertTrue("expected a redirect, got $code", code in 300..399)
            assertEquals(RankRule.BLOCK, runBlocking { prefs.load() }.domainRules["news.example"])

            postForm(port, "/rules/domain", "domain=news.example&action=NORMAL")
            assertFalse(runBlocking { prefs.load() }.domainRules.containsKey("news.example"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun postScopeSetsAndClearsActiveLens() {
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            postForm(port, "/scope", "lens=Docs")
            assertEquals("Docs", runBlocking { prefs.load() }.activeLens)
            postForm(port, "/scope", "lens=")
            assertEquals(null, runBlocking { prefs.load() }.activeLens)
        } finally {
            server.stop()
        }
    }

    @Test
    fun postScopeWithOpaqueOriginIsAllowed() {
        // Regression: a browser sends `Origin: null` (an opaque origin) for the served scope form
        // because every response sets `Referrer-Policy: no-referrer`. That must be treated as
        // same-origin, not rejected with 403 (the bug this guards against).
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val code = postFormWithOrigin(port, "/scope", "lens=Docs", origin = "null")
            assertTrue("opaque-origin POST should redirect, got $code", code in 300..399)
            assertEquals("Docs", runBlocking { prefs.load() }.activeLens)
        } finally {
            server.stop()
        }
    }

    @Test
    fun postScopeWithForeignOriginIsForbidden() {
        // A genuine cross-site POST carries a real, non-loopback Origin host and must be rejected.
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertEquals(403, postFormWithOrigin(port, "/scope", "lens=Docs", origin = "http://evil.example"))
            assertEquals(null, runBlocking { prefs.load() }.activeLens)
        } finally {
            server.stop()
        }
    }

    @Test
    fun ownerResultsPageShowsControls() {
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, html) = get(port, "/search?q=hi")
            assertEquals(200, code)
            assertTrue(html.contains("/rules/domain"))
            assertTrue(html.contains("/scope"))
            assertTrue(html.contains("news.example"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun mutationRoutesAreReadOnlyWithoutPreferences() {
        // No RankingPreferences wired -> the edit routes report unavailable, controls not shown.
        val server = SearchServer(provider = OneResultProvider())
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertEquals(503, postForm(port, "/rules/domain", "domain=news.example&action=BLOCK"))
            val (_, html) = get(port, "/search?q=hi")
            assertFalse(html.contains("/rules/domain"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun isLoopbackHostClassifiesAddresses() {
        assertTrue(isLoopbackHost("127.0.0.1"))
        assertTrue(isLoopbackHost("127.5.6.7"))
        assertTrue(isLoopbackHost("localhost"))
        assertTrue(isLoopbackHost("::1"))
        // IPv6 loopback in the textual forms a dual-stack socket or an Origin/Host header can surface.
        assertTrue(isLoopbackHost("0:0:0:0:0:0:0:1"))
        assertTrue(isLoopbackHost("[::1]"))
        assertTrue(isLoopbackHost("::1%eth0"))
        assertTrue(isLoopbackHost("::ffff:127.0.0.1"))
        assertFalse(isLoopbackHost("192.168.1.20"))
        assertFalse(isLoopbackHost("evil.example"))
        assertFalse(isLoopbackHost("::"))
        assertFalse(isLoopbackHost("2001:db8::1"))
    }
}
