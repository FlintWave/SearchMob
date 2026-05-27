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
    private val fixture =
        """
        {"pages":[
          {"key":"Privacy","title":"Privacy","excerpt":"<span class=\"hl\">Privacy</span> is the ability to be let alone.","description":"d"},
          {"key":"Internet_privacy","title":"Internet privacy","excerpt":"Internet privacy involves the right to store data.","description":"d2"}
        ]}
        """.trimIndent()

    @Test
    fun parsesPagesIntoNormalizedItems() {
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
    fun searchHitsRestEndpointAndParses() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(fixture))
            server.start()
            try {
                val adapter = WikipediaAdapter(baseUrl = server.url("/").toString().removeSuffix("/"))
                val result = adapter.search(SearchQuery("privacy"), EngineContext(OkHttpClient(), timeoutMs = 5_000))
                assertTrue(result is EngineResult.Success)
                assertEquals(2, (result as EngineResult.Success).items.size)
                assertTrue(server.takeRequest().path!!.startsWith("/w/rest.php/v1/search/page?q=privacy"))
            } finally {
                server.shutdown()
            }
        }
}
