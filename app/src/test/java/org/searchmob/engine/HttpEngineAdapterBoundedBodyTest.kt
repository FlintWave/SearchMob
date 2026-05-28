package org.searchmob.engine

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpEngineAdapterBoundedBodyTest {
    /** Minimal adapter that fetches the server root and echoes the body length as a single item. */
    private class EchoAdapter(private val baseUrl: String) : HttpEngineAdapter() {
        override val id = "echo"
        override val displayName = "Echo"
        override val categories = setOf(SearchCategory.GENERAL)

        override fun buildRequest(
            query: SearchQuery,
            ctx: EngineContext,
        ): Request = Request.Builder().url(baseUrl).build()

        override fun parse(body: String): List<EngineResultItem> =
            listOf(EngineResultItem("len", "https://x.test/${body.length}", "", id, 0))
    }

    private fun ctx() = EngineContext(httpClient = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build())

    @Test
    fun withinCapBodyIsParsed() =
        runTest {
            val server = MockWebServer()
            val body = "a".repeat(1024)
            server.enqueue(MockResponse().setBody(body))
            server.start()
            try {
                val adapter = EchoAdapter(server.url("/").toString())
                val result = adapter.search(SearchQuery("q"), ctx())
                assertTrue(result is EngineResult.Success)
                assertEquals("https://x.test/1024", (result as EngineResult.Success).items.first().url)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun oversizedBodyIsTreatedAsFailure() =
        runTest {
            val server = MockWebServer()
            // One byte over the cap; must be rejected rather than parsed.
            val oversized = "a".repeat((MAX_RESPONSE_BYTES + 1).toInt())
            server.enqueue(MockResponse().setBody(oversized))
            server.start()
            try {
                val adapter = EchoAdapter(server.url("/").toString())
                val result = adapter.search(SearchQuery("q"), ctx())
                assertTrue("expected failure for oversized body, got $result", result is EngineResult.Failure)
            } finally {
                server.shutdown()
            }
        }
}
