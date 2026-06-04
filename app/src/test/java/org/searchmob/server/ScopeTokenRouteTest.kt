package org.searchmob.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.vertical.Vertical
import java.net.HttpURLConnection
import java.net.URL

/**
 * The inline `+name` scope token on the served `/search` and `/api/search` routes: the route parses
 * the token against the saved scopes, searches the cleaned query, passes the matched scope as a
 * transient override (without writing the saved scope), and echoes the original text. Mirrors the
 * desktop `test_scope_token_route`.
 */
class ScopeTokenRouteTest {
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

    /** Records the query and the scope override the route hands the provider. */
    private class RecordingProvider : SearchResultProvider {
        var seenQuery: String? = null
        var seenOverride: String? = null

        override suspend fun search(query: String): List<SearchResult> = emptyList()

        override suspend fun searchWithCorrection(
            query: String,
            sortMode: SortMode,
            vertical: Vertical,
            personalize: Boolean,
            activeLensOverride: String?,
        ): SearchOutcome {
            seenQuery = query
            seenOverride = activeLensOverride
            return SearchOutcome(
                listOf(SearchResult(title = "A page", url = "https://arxiv.org/abs/1", snippet = "s", engine = "e")),
            )
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
    ): String {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        val body = c.inputStream.bufferedReader().readText()
        c.disconnect()
        return body
    }

    @Test
    fun matchedTokenAppliesScopeAndSearchesCleanedQuery() {
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Research mode")))) }
        val provider = RecordingProvider()
        val server = SearchServer(provider = provider, rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            // `%2B` is a literal '+' (a bare '+' in a query string would decode to a space).
            val body = get(port, "/api/search?q=neural%20nets%20%2Bresearch")
            assertEquals("neural nets", provider.seenQuery)
            assertEquals("Research mode", provider.seenOverride)
            // The echo keeps the original text so the token round-trips on a re-search.
            assertTrue("expected original query echoed, got: $body", body.contains("neural nets +research"))
            // The token is transient: the saved active scope is untouched.
            assertNull(runBlocking { prefs.load() }.activeLens)
        } finally {
            server.stop()
        }
    }

    @Test
    fun unmatchedTokenPassesThroughUnchanged() {
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules(lenses = listOf(Lens(name = "Research mode")))) }
        val provider = RecordingProvider()
        val server = SearchServer(provider = provider, rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            get(port, "/api/search?q=rust%20%2Btokio")
            assertEquals("rust +tokio", provider.seenQuery)
            assertNull(provider.seenOverride)
        } finally {
            server.stop()
        }
    }
}
