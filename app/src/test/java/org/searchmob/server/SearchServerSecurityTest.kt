package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankingRules

/** Network-mode security hardening: response headers, result-link rel, and the Host/IP helpers. */
class SearchServerSecurityTest {
    private class OneResult : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A", url = "https://news.example/x", snippet = "s", engine = "e"))
    }

    // Copied from ScopeTokenRouteTest's FakeStore: an in-memory PreferencesStore so a real
    // RankingPreferences can back the `/scope` CSRF test below.
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

    @Test
    fun everyResponseCarriesConservativeSecurityHeaders() =
        testApplication {
            application { searchModule(OneResult()) { DEFAULT_PORT } }
            val resp = client.get("/")
            assertEquals("same-origin", resp.headers["Referrer-Policy"])
            assertEquals("nosniff", resp.headers["X-Content-Type-Options"])
            assertEquals("DENY", resp.headers["X-Frame-Options"])
            assertEquals("no-store", resp.headers["Cache-Control"])
            val csp = resp.headers["Content-Security-Policy"]
            assertTrue("CSP missing default-src 'none': $csp", csp!!.contains("default-src 'none'"))
            assertTrue("CSP missing frame-ancestors 'none': $csp", csp.contains("frame-ancestors 'none'"))
            val permissionsPolicy = resp.headers["Permissions-Policy"]
            assertTrue("Permissions-Policy missing: $permissionsPolicy", permissionsPolicy!!.contains("camera=()"))
        }

    @Test
    fun postScopeWithNullOriginIsForbiddenEvenFromLoopback() =
        testApplication {
            // Regression for the CSRF-hardening fix: a literal `Origin: null` used to be treated as
            // same-origin (the theory being that our own `Referrer-Policy: no-referrer` made a browser
            // serialize our own posts that way), but an attacker page can force the exact same opaque
            // value onto a genuinely cross-site POST (e.g. by setting `Referrer-Policy: no-referrer` on
            // its own page, or posting from a sandboxed iframe). It must now be rejected.
            val prefs = RankingPreferences(FakeStore())
            runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
            application {
                searchModule(OneResult(), rankingPreferences = prefs) { DEFAULT_PORT }
            }
            val resp =
                client.post("/scope") {
                    header("Origin", "null")
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody("lens=Docs")
                }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
            assertNull(runBlocking { prefs.load() }.activeLens)
        }

    @Test
    fun postScopeWithNoOriginSucceeds() =
        testApplication {
            // A same-origin POST with no Origin header at all (e.g. a non-browser client, or a browser
            // that omits it) is still treated as same-origin and allowed through.
            val client = createClient { followRedirects = false }
            val prefs = RankingPreferences(FakeStore())
            runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Docs")))) }
            application {
                searchModule(OneResult(), rankingPreferences = prefs) { DEFAULT_PORT }
            }
            val resp =
                client.post("/scope") {
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody("lens=Docs")
                }
            assertTrue(
                "expected a redirect, got ${resp.status}",
                resp.status.value in 300..399,
            )
            assertEquals("Docs", runBlocking { prefs.load() }.activeLens)
        }

    @Test
    fun resultLinksAreNoopenerNoreferrer() =
        testApplication {
            application { searchModule(OneResult()) { DEFAULT_PORT } }
            val body = client.get("/search?q=privacy").bodyAsText()
            assertTrue(body.contains("rel=\"noopener noreferrer\""))
        }

    @Test
    fun hostnameOnlyStripsPortAndBrackets() {
        assertEquals("example.com", hostnameOnly("example.com:8787"))
        assertEquals("example.com", hostnameOnly("Example.com"))
        assertEquals("::1", hostnameOnly("[::1]:8787"))
        assertEquals("", hostnameOnly("   "))
    }

    @Test
    fun isIpLiteralRecognizesV4AndV6() {
        assertTrue(isIpLiteral("192.168.1.20"))
        assertTrue(isIpLiteral("::1"))
        assertTrue(isIpLiteral("fe80::1"))
        assertFalse(isIpLiteral("evil.example"))
        assertFalse(isIpLiteral("999.1.1.1"))
    }

    @Test
    fun hostHeaderAllowedAcceptsLoopbackIpAndAllowed_rejectsForeign() {
        assertTrue(hostHeaderAllowed("", emptySet())) // HTTP/1.0 absent Host
        assertTrue(hostHeaderAllowed("localhost", emptySet()))
        assertTrue(hostHeaderAllowed("127.0.0.1", emptySet()))
        assertTrue(hostHeaderAllowed("192.168.1.20", emptySet())) // IP literal (LAN client)
        assertTrue(hostHeaderAllowed("mybox.local", setOf("mybox.local")))
        assertFalse(hostHeaderAllowed("evil.example", emptySet())) // foreign DNS name -> rebind guard
    }

    @Test
    fun presentedTokenPrefersQueryParamThenBearerThenCustomHeader() {
        // The `?token=` query parameter always wins when present, even alongside other sources.
        assertEquals(
            "q-token",
            presentedToken("q-token", "Bearer h-token", "x-token"),
        )
        // Falls back to a Bearer-scheme Authorization header, case-insensitive and trimmed.
        assertEquals("abc123", presentedToken(null, "Bearer abc123", null))
        assertEquals("abc123", presentedToken(null, "bearer   abc123", null))
        assertEquals("abc123", presentedToken(null, "BEARER abc123", null))
        assertEquals("abc123", presentedToken(null, "  Bearer abc123  ", null))
        // A non-Bearer Authorization header is ignored in favor of the custom header.
        assertEquals("x-token", presentedToken(null, "Basic dXNlcjpwYXNz", "x-token"))
        // Falls back to the bare custom header when nothing else is present.
        assertEquals("x-token", presentedToken(null, null, "x-token"))
        // Nothing presented at all.
        assertNull(presentedToken(null, null, null))
    }

    @Test
    fun tokenMatchesRequiresANonEmptyExpectedTokenAndAnEqualPresentedOne() {
        assertFalse(tokenMatches("secret", null)) // no token configured
        assertFalse(tokenMatches("secret", "")) // no token configured
        assertFalse(tokenMatches(null, "secret")) // nothing presented
        assertTrue(tokenMatches("secret", "secret"))
        assertFalse(tokenMatches("secret", "different"))
        assertFalse(tokenMatches("secre", "secret")) // differing length
    }

    @Test
    fun homePageAdvertisesTheSearchOperatorsHelp() =
        testApplication {
            application { searchModule(OneResult()) { DEFAULT_PORT } }
            val home = client.get("/").bodyAsText()
            assertTrue(home.contains("ophelp"))
            assertTrue(home.contains("Search operators"))
            assertTrue(home.contains("filetype:pdf"))
        }
}
