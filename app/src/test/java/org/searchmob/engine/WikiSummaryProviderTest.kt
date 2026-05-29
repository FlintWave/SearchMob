package org.searchmob.engine

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.summary.WikiSummaryProvider

class WikiSummaryProviderTest {
    private val openSearch = """["everest",["Mount Everest"],[""],[""]]"""

    private fun summaryJson(type: String = "standard") =
        """
        {"type":"$type","title":"Mount Everest","description":"Earth's highest mountain",
         "extract":"Mount Everest is Earth's highest mountain above sea level.",
         "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Mount_Everest"}},
         "thumbnail":{"source":"https://upload.wikimedia.org/everest.jpg"}}
        """.trimIndent()

    private fun provider(server: MockWebServer) =
        WikiSummaryProvider(OkHttpClient(), baseUrl = server.url("/").toString().removeSuffix("/"))

    @Test
    fun entityLikeQueryGate() {
        assertTrue(WikiSummaryProvider.isEntityLikeQuery("mount everest"))
        assertTrue(WikiSummaryProvider.isEntityLikeQuery("Python"))
        assertFalse(WikiSummaryProvider.isEntityLikeQuery(""))
        assertFalse(WikiSummaryProvider.isEntityLikeQuery("how do I install python on ubuntu linux today"))
        assertFalse(WikiSummaryProvider.isEntityLikeQuery("https://example.com"))
        assertFalse(WikiSummaryProvider.isEntityLikeQuery("example.com"))
    }

    @Test
    fun confidentMatch() {
        assertTrue(WikiSummaryProvider.isConfidentMatch("everest", "Mount Everest"))
        assertTrue(WikiSummaryProvider.isConfidentMatch("mount everest", "Mount Everest (mountain)"))
        assertFalse(WikiSummaryProvider.isConfidentMatch("everest", "George Mallory"))
    }

    @Test
    fun happyPathReturnsSummary() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(openSearch))
        server.enqueue(MockResponse().setBody(summaryJson()))
        server.start()
        try {
            val box = runBlocking { provider(server).fetch("mount everest") }
            assertNotNull(box)
            assertEquals("Mount Everest", box!!.title)
            assertEquals("Earth's highest mountain", box.description)
            assertTrue(box.extract.contains("highest mountain"))
            assertEquals("https://en.wikipedia.org/wiki/Mount_Everest", box.url)
            assertEquals("https://upload.wikimedia.org/everest.jpg", box.thumbnailUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disambiguationIsRejected() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(openSearch))
        server.enqueue(MockResponse().setBody(summaryJson(type = "disambiguation")))
        server.start()
        try {
            assertNull(runBlocking { provider(server).fetch("mount everest") })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun lowConfidenceTitleYieldsNoBoxAndNoSummaryCall() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""["q",["Banana bread"],[""],[""]]"""))
        server.start()
        try {
            assertNull(runBlocking { provider(server).fetch("mount everest") })
            // Only the OpenSearch request was made; the summary endpoint was never reached.
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonEntityQuerySkipsNetwork() {
        val server = MockWebServer()
        server.start()
        try {
            assertNull(runBlocking { provider(server).fetch("what is the best way to learn rust") })
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
