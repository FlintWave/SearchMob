package org.searchmob.server

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.title
import kotlinx.html.ul
import java.net.InetSocketAddress
import java.net.ServerSocket

const val LOOPBACK_HOST = "127.0.0.1"
const val DEFAULT_PORT = 8787

/**
 * Configures the SearchMob HTTP routes on an [Application]. Shared by the real [SearchServer] and by
 * `testApplication` tests so the HTTP contract is exercised identically. No request/access logging is
 * installed (privacy default); query data exists only in memory for the duration of a request.
 */
fun Application.searchModule(
    provider: SearchResultProvider,
    guard: RequestWakeGuard = NoopRequestWakeGuard,
    boundPort: () -> Int,
) {
    install(ContentNegotiation) { json() }
    routing {
        get("/healthz") {
            call.respondText("ok")
        }
        get("/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            val results = if (query.isBlank()) emptyList() else guard.aroundRequest { provider.search(query) }
            call.respondHtml { renderResultsPage(query, results) }
        }
        get("/api/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            val results = if (query.isBlank()) emptyList() else guard.aroundRequest { provider.search(query) }
            call.respond(SearchResponse(query = query, results = results))
        }
        get("/opensearch.xml") {
            call.respondText(
                openSearchDescriptor(boundPort()),
                ContentType("application", "opensearchdescription+xml"),
            )
        }
    }
}

private fun HTML.renderResultsPage(
    query: String,
    results: List<SearchResult>,
) {
    head {
        title { +if (query.isBlank()) "SearchMob" else "SearchMob — $query" }
    }
    body {
        h1 { +"SearchMob" }
        if (query.isBlank()) {
            p { +"Enter a query to search." }
        } else {
            p { +"Results for \"$query\"" }
            ul {
                results.forEach { result ->
                    li {
                        a(href = result.url) { +result.title }
                        if (result.snippet.isNotBlank()) {
                            p { +result.snippet }
                        }
                        if (result.engine.isNotBlank()) {
                            span { +"— ${result.engine}" }
                        }
                    }
                }
            }
        }
    }
}

/** Spec-compliant OpenSearch descriptor whose URL templates target the actual bound loopback origin. */
fun openSearchDescriptor(port: Int): String {
    val origin = "http://$LOOPBACK_HOST:$port"
    return """<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <ShortName>SearchMob</ShortName>
  <Description>Private on-device metasearch</Description>
  <InputEncoding>UTF-8</InputEncoding>
  <Url type="text/html" template="$origin/search?q={searchTerms}"/>
  <Url type="application/json" template="$origin/api/search?q={searchTerms}&amp;format=json"/>
</OpenSearchDescription>
"""
}

/** Returns true if [port] can be bound on loopback right now. */
fun isLoopbackPortFree(port: Int): Boolean =
    try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(LOOPBACK_HOST, port))
            true
        }
    } catch (_: Exception) {
        false
    }

/** An OS-assigned free loopback port. */
fun freeLoopbackPort(): Int =
    ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(LOOPBACK_HOST, 0))
        socket.localPort
    }

/**
 * Embedded Ktor (CIO) HTTP server bound ONLY to loopback. Its lifecycle is owned by the foreground
 * service. Falls back to an available port if the preferred one is busy and exposes the bound port.
 */
class SearchServer(
    private val provider: SearchResultProvider = StubSearchResultProvider(),
    private val guard: RequestWakeGuard = NoopRequestWakeGuard,
) {
    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    var boundPort: Int = -1
        private set

    val isRunning: Boolean get() = server != null

    /** Starts the server (idempotent). Returns the actually-bound loopback port. */
    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        server?.let { return boundPort }
        val port = if (isLoopbackPortFree(preferredPort)) preferredPort else freeLoopbackPort()
        val engine =
            embeddedServer(CIO, host = LOOPBACK_HOST, port = port) {
                searchModule(provider, guard) { port }
            }
        engine.start(wait = false)
        server = engine
        boundPort = port
        return port
    }

    /** Gracefully stops the server, draining in-flight requests within the bounded window. */
    @Synchronized
    fun stop(
        gracePeriodMillis: Long = 500,
        timeoutMillis: Long = 3000,
    ) {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
        boundPort = -1
    }
}
