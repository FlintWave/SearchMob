package org.searchmob.server.suggest

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.InMemoryHistoryStore
import java.util.concurrent.TimeUnit

class SuggestionsProviderTest {
    // ----- HistorySuggestionsProvider -----

    private fun seededHistory(): InMemoryHistoryStore {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        // Inserted oldest -> newest.
        store.add(HistoryEntry("kotlin coroutines", 1_000))
        store.add(HistoryEntry("kotlin flow", 2_000))
        store.add(HistoryEntry("KOTLIN flow", 3_000)) // duplicate of "kotlin flow" case-insensitively
        store.add(HistoryEntry("rust ownership", 4_000))
        store.add(HistoryEntry("kotlin sequences", 5_000))
        return store
    }

    @Test
    fun history_prefixMatchesCaseInsensitiveDistinctMostRecentFirst() =
        runTest {
            val provider = HistorySuggestionsProvider(seededHistory(), nowMsProvider = { 5_500 })
            val result = provider.suggest("kot", limit = 10)
            // Most-recent first, case-insensitive prefix match, the two "kotlin flow" rows collapse to one.
            assertEquals(listOf("kotlin sequences", "KOTLIN flow", "kotlin coroutines"), result)
        }

    @Test
    fun history_respectsLimit() =
        runTest {
            val provider = HistorySuggestionsProvider(seededHistory(), nowMsProvider = { 5_500 })
            assertEquals(listOf("kotlin sequences", "KOTLIN flow"), provider.suggest("kotlin", limit = 2))
        }

    @Test
    fun history_emptyWhenDisabledOrEmptyOrBlankQuery() =
        runTest {
            val disabled = InMemoryHistoryStore() // off by default
            assertTrue(HistorySuggestionsProvider(disabled).suggest("kot", 5).isEmpty())

            val emptyEnabled = InMemoryHistoryStore().apply { setEnabled(true) }
            assertTrue(HistorySuggestionsProvider(emptyEnabled).suggest("kot", 5).isEmpty())

            assertTrue(HistorySuggestionsProvider(seededHistory()).suggest("   ", 5).isEmpty())
        }

    // ----- UpstreamSuggestionsProvider -----

    private fun proxyClient() = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()

    @Test
    fun upstream_parsesDuckDuckGoAcListShape() =
        runTest {
            val server = MockWebServer()
            server.enqueue(
                MockResponse().setBody("""["kot",["kotlin","kotlin coroutines","kotlin flow"]]"""),
            )
            server.start()
            try {
                val provider =
                    UpstreamSuggestionsProvider(proxyClient(), baseUrl = server.url("/").toString().trimEnd('/'))
                val result = provider.suggest("kot", limit = 10)
                assertEquals(listOf("kotlin", "kotlin coroutines", "kotlin flow"), result)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun upstream_respectsLimit() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("""["k",["a","b","c","d"]]"""))
            server.start()
            try {
                val provider =
                    UpstreamSuggestionsProvider(proxyClient(), baseUrl = server.url("/").toString().trimEnd('/'))
                assertEquals(listOf("a", "b"), provider.suggest("k", limit = 2))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun upstream_returnsEmptyOnHttpError() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            try {
                val provider =
                    UpstreamSuggestionsProvider(proxyClient(), baseUrl = server.url("/").toString().trimEnd('/'))
                assertTrue(provider.suggest("kot", 10).isEmpty())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun upstream_returnsEmptyOnMalformedJson() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("not json at all"))
            server.start()
            try {
                val provider =
                    UpstreamSuggestionsProvider(proxyClient(), baseUrl = server.url("/").toString().trimEnd('/'))
                assertTrue(provider.suggest("kot", 10).isEmpty())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun upstream_returnsEmptyOnTimeout() =
        runTest {
            val server = MockWebServer()
            // Hang the connection so the short read timeout fires; must fail soft, not throw.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            try {
                val client =
                    OkHttpClient.Builder()
                        .cookieJar(CookieJar.NO_COOKIES)
                        .readTimeout(200, TimeUnit.MILLISECONDS)
                        .build()
                val provider = UpstreamSuggestionsProvider(client, baseUrl = server.url("/").toString().trimEnd('/'))
                assertTrue(provider.suggest("kot", 10).isEmpty())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun upstream_emptyForBlankQuery() =
        runTest {
            // No server interaction expected; a blank query short-circuits before any request.
            val provider = UpstreamSuggestionsProvider(proxyClient(), baseUrl = "https://unused.test")
            assertTrue(provider.suggest("  ", 10).isEmpty())
        }

    // ----- CompositeSuggestionsProvider -----

    private class StaticProvider(private val items: List<String>) : SuggestionsProvider {
        var called = false

        override suspend fun suggest(
            query: String,
            limit: Int,
        ): List<String> {
            called = true
            return items
        }
    }

    @Test
    fun composite_mergesLocalFirstAndDedupesCaseInsensitively() =
        runTest {
            val local = StaticProvider(listOf("kotlin flow", "kotlin coroutines"))
            val upstream = StaticProvider(listOf("Kotlin Flow", "kotlin multiplatform"))
            val composite =
                CompositeSuggestionsProvider(local, upstream, upstreamEnabled = { true }, maxTotal = 8)
            val result = composite.suggest("kot", limit = 8)
            // Local first; "Kotlin Flow" is a case-insensitive dup of the local "kotlin flow" and is dropped.
            assertEquals(listOf("kotlin flow", "kotlin coroutines", "kotlin multiplatform"), result)
        }

    @Test
    fun composite_capsTotal() =
        runTest {
            val local = StaticProvider(listOf("a", "b", "c"))
            val upstream = StaticProvider(listOf("d", "e", "f"))
            val composite =
                CompositeSuggestionsProvider(local, upstream, upstreamEnabled = { true }, maxTotal = 4)
            assertEquals(listOf("a", "b", "c", "d"), composite.suggest("x", limit = 8))
        }

    @Test
    fun composite_doesNotCallUpstreamWhenOptInIsOff() =
        runTest {
            val local = StaticProvider(listOf("local only"))
            val upstream = StaticProvider(listOf("should-not-appear"))
            val composite =
                CompositeSuggestionsProvider(local, upstream, upstreamEnabled = { false })
            val result = composite.suggest("loc", limit = 8)
            assertEquals(listOf("local only"), result)
            assertTrue("upstream must not be queried when opt-in is off", !upstream.called)
            assertTrue("local is always queried", local.called)
        }

    @Test
    fun composite_doesNotServeLocalHistoryWhenLocalDisabled() =
        runTest {
            // Network mode: local history must not be queried or served (it would leak to LAN clients).
            val local = StaticProvider(listOf("private local query"))
            val upstream = StaticProvider(listOf("upstream item"))
            val composite =
                CompositeSuggestionsProvider(
                    local,
                    upstream,
                    upstreamEnabled = { true },
                    localEnabled = { false },
                )
            val result = composite.suggest("p", limit = 8)
            assertEquals(listOf("upstream item"), result)
            assertTrue("local history must not be queried when disabled", !local.called)
        }

    @Test
    fun composite_emptyForBlankQuery() =
        runTest {
            val local = StaticProvider(listOf("x"))
            val upstream = StaticProvider(listOf("y"))
            val composite = CompositeSuggestionsProvider(local, upstream, upstreamEnabled = { true })
            assertTrue(composite.suggest("   ", 8).isEmpty())
        }
}
