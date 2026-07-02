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

    private class EmptyResultProvider : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> = emptyList()
    }

    @Test
    fun emptyResultsUnderActiveScopeShowScopeBarAndClearControl() {
        // Regression: an active scope that hid every result used to leave a blank page with no way to
        // see or clear the scope, since the scope bar only rendered when results existed.
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")), activeLens = "Docs")) }
        val server = SearchServer(provider = EmptyResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, html) = get(port, "/search?q=threejs")
            assertEquals(200, code)
            assertTrue("scope bar should render on the empty page", html.contains("class=\"scopebar\""))
            assertTrue("emptiness should be attributed to the scope", html.contains("No results match the"))
            assertTrue("a clear-scope control should be offered", html.contains("class=\"clearscope\""))
            assertTrue("clear-scope posts an empty lens to /scope", html.contains("action=\"/scope\""))
        } finally {
            server.stop()
        }
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

    /** POST a form and return the (status code, Location header) without following the redirect. */
    private fun postFormRedirect(
        port: Int,
        path: String,
        form: String,
    ): Pair<Int, String?> {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.instanceFollowRedirects = false
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(form.toByteArray()) }
        val code = c.responseCode
        val location = c.getHeaderField("Location")
        c.disconnect()
        return code to location
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
    fun postScopeWithOpaqueOriginIsForbidden() {
        // Regression, reversed: `Origin: null` (an opaque origin) used to be treated as same-origin on
        // the theory that our own `Referrer-Policy: no-referrer` made a browser serialize our OWN
        // form posts that way. But an attacker page can force the exact same opaque `Origin: null`
        // onto a genuinely cross-site POST (e.g. by setting `Referrer-Policy: no-referrer` on its own
        // page, or posting from a sandboxed iframe), which made that a CSRF bypass. We now serve
        // `Referrer-Policy: same-origin` instead, so our own same-origin posts carry a real, non-null
        // origin, and an opaque `Origin: null` is rejected as cross-site. See SearchServerSecurityTest
        // for the same regression exercised via `testApplication`.
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertEquals(403, postFormWithOrigin(port, "/scope", "lens=Docs", origin = "null"))
            assertEquals(null, runBlocking { prefs.load() }.activeLens)
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
    fun scopeAndRuleMutationsRedirectBackToTheResultsPage() {
        // Regression: applying a scope/rule from the results page must return to that search (carried
        // via hidden q/sort/vertical fields), not dump the owner on the home page and lose the
        // results. The redirect deliberately does not rely on the Referer header - see
        // `redirectToResults`'s doc comment for why.
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (scopeCode, scopeLoc) =
                postFormRedirect(port, "/scope", "lens=Docs&q=privacy&sort=fresh&vertical=news")
            assertTrue("expected a redirect, got $scopeCode", scopeCode in 300..399)
            assertTrue("redirect should land on the search, was $scopeLoc", scopeLoc!!.startsWith("/search?q=privacy"))
            assertTrue(scopeLoc.contains("vertical=news"))

            val (ruleCode, ruleLoc) =
                postFormRedirect(
                    port,
                    "/rules/domain",
                    "domain=news.example&action=BLOCK&q=privacy&sort=fresh&vertical=web",
                )
            assertTrue("expected a redirect, got $ruleCode", ruleCode in 300..399)
            assertTrue("redirect should land on the search, was $ruleLoc", ruleLoc!!.startsWith("/search?q=privacy"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun scopeMutationWithoutAQueryFallsBackHome() {
        // The home-page scope selector carries no query; it must still work (fall back to "/"), not error.
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, loc) = postFormRedirect(port, "/scope", "lens=Docs")
            assertTrue("expected a redirect, got $code", code in 300..399)
            assertEquals("/", loc)
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
