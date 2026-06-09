package org.searchmob.engine

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.WikipediaAdapter

class WikipediaAdapterTest {
    // OpenSearch shape: [query, titles[], snippets[], urls[]].
    private val fixture =
        """
        ["privacy",
         ["Privacy","Internet privacy"],
         ["Privacy is the ability to be let alone.","Internet privacy involves the right to store data."],
         ["https://en.wikipedia.org/wiki/Privacy","https://en.wikipedia.org/wiki/Internet_privacy"]]
        """.trimIndent()

    @Test
    fun parsesOpenSearchArrayIntoNormalizedItems() {
        val items = WikipediaAdapter().parse(fixture)
        assertEquals(2, items.size)
        assertEquals("Privacy", items[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Privacy", items[0].url)
        assertEquals("Privacy is the ability to be let alone.", items[0].snippet)
        assertEquals(0, items[0].position)
        assertEquals("wikipedia", items[0].engineId)
        assertEquals(1, items[1].position)
    }

    @Test
    fun nonEntityQueryContributesNothing() {
        // OpenSearch returns empty result arrays for a query that names no article: the adapter must
        // contribute nothing rather than fall back to noisy full-text matches.
        val items = WikipediaAdapter().parse("""["club 541 palm springs gay",[],[],[]]""")
        assertEquals(0, items.size)
    }

    @Test
    fun searchHitsOpenSearchEndpointAndParses() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(fixture))
            server.start()
            try {
                val adapter = WikipediaAdapter(baseUrl = server.url("/").toString().removeSuffix("/"))
                val result = adapter.search(SearchQuery("privacy"), EngineContext(OkHttpClient(), timeoutMs = 5_000))
                assertTrue(result is EngineResult.Success)
                assertEquals(2, (result as EngineResult.Success).items.size)
                val path = server.takeRequest().path!!
                assertTrue(path.startsWith("/w/api.php?action=opensearch"))
                assertTrue(path.contains("search=privacy"))
            } finally {
                server.shutdown()
            }
        }
}
