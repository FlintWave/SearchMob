package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Network-mode security hardening: response headers, result-link rel, and the Host/IP helpers. */
class SearchServerSecurityTest {
    private class OneResult : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A", url = "https://news.example/x", snippet = "s", engine = "e"))
    }

    @Test
    fun everyResponseCarriesConservativeSecurityHeaders() =
        testApplication {
            application { searchModule(OneResult()) { DEFAULT_PORT } }
            val resp = client.get("/")
            assertEquals("no-referrer", resp.headers["Referrer-Policy"])
            assertEquals("nosniff", resp.headers["X-Content-Type-Options"])
            assertEquals("DENY", resp.headers["X-Frame-Options"])
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
}
