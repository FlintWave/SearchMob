package org.searchmob.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.aggregate.EngineStatus
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.vertical.Vertical
import java.net.HttpURLConnection
import java.net.URL

/**
 * Served engine status: the per-engine outcome line is shown to the loopback owner only. The owner
 * gate is `editable` (a wired RankingPreferences plus a loopback request); a server without one is
 * read-only, so the line must be absent, mirroring how the editing controls are gated.
 */
class EngineStatusRouteTest {
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

    private class StatusProvider : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A page", url = "https://news.example/x", snippet = "s", engine = "alpha"))

        override suspend fun searchWithCorrection(
            query: String,
            sortMode: SortMode,
            vertical: Vertical,
            personalize: Boolean,
            activeLensOverride: String?,
        ): SearchOutcome =
            SearchOutcome(
                results = search(query),
                engineStatus =
                    listOf(
                        EngineOutcome("alpha", EngineStatus.CONTRIBUTED, 1),
                        EngineOutcome("beta", EngineStatus.FAILED, 0),
                    ),
            )
    }

    @Test
    fun ownerSeesEngineStatusLine() {
        val prefs = RankingPreferences(FakeStore())
        runBlocking { prefs.save(RankingRules()) }
        val server = SearchServer(provider = StatusProvider(), rankingPreferences = prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, html) = get(port, "/search?q=hi")
            assertEquals(200, code)
            assertTrue(html.contains("engines responded"))
            assertTrue(html.contains("class=\"engine-status"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun readOnlyServerWithoutOwnerPrefsHidesEngineStatus() {
        // No RankingPreferences wired -> `editable` is false -> the diagnostics line is not rendered.
        val server = SearchServer(provider = StatusProvider())
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (_, html) = get(port, "/search?q=hi")
            assertFalse(html.contains("engines responded"))
            assertFalse(html.contains("<details class=\"engine-status"))
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
}
