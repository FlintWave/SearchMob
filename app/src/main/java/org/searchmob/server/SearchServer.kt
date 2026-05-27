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
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.unsafe
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
        get("/") {
            call.respondHtml { renderHomePage() }
        }
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
    head { pageHead(if (query.isBlank()) "SearchMob" else "$query · SearchMob") }
    body {
        attributes["data-page"] = "results"
        div("topbar") {
            a(href = "/", classes = "logo") { +"SearchMob" }
            form(action = "/search", method = FormMethod.get, classes = "searchbox") {
                textInput(name = "q") {
                    value = query
                    placeholder = "Search the web"
                    attributes["autocomplete"] = "off"
                    attributes["spellcheck"] = "false"
                }
                submitInput { value = "Search" }
            }
            themeToggle()
        }
        div("results") {
            when {
                query.isBlank() -> p("empty") { +"Enter a query to search." }
                results.isEmpty() -> p("empty") { +"No results for “$query”." }
                else -> {
                    p("meta") { +"Results for “$query”" }
                    results.forEach { result ->
                        div("result") {
                            div("url") { +displayUrl(result.url) }
                            a(href = result.url, classes = "title") { +result.title }
                            if (result.snippet.isNotBlank()) {
                                p("snippet") { +result.snippet }
                            }
                            if (result.engine.isNotBlank()) {
                                div("engines") {
                                    result.engine.split(",").forEach { engine ->
                                        span("chip") { +engine.trim() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        script { unsafe { +THEME_TOGGLE_JS } }
    }
}

/** Home page: a centered search box plus the OpenSearch link so a browser can add SearchMob. */
private fun HTML.renderHomePage() {
    head { pageHead("SearchMob") }
    body {
        attributes["data-page"] = "home"
        div("topbar") {
            span("logo") { +"SearchMob" }
            themeToggle()
        }
        div("home") {
            div("brand") { +"SearchMob" }
            p("tagline") { +"Private, on-device metasearch." }
            form(action = "/search", method = FormMethod.get, classes = "searchbox") {
                textInput(name = "q") {
                    placeholder = "Search the web"
                    attributes["autocomplete"] = "off"
                    attributes["autofocus"] = "autofocus"
                }
                submitInput { value = "Search" }
            }
        }
        script { unsafe { +THEME_TOGGLE_JS } }
    }
}

/** Shared <head>: meta, title, OpenSearch link, styles, and the pre-paint theme restore. */
private fun HEAD.pageHead(titleText: String) {
    meta(charset = "utf-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    title { +titleText }
    openSearchLink()
    style { unsafe { +PAGE_CSS } }
    script { unsafe { +THEME_INIT_JS } }
}

/** The light/dark toggle button (label set by [THEME_TOGGLE_JS] to reflect the current theme). */
private fun FlowContent.themeToggle() {
    button(type = ButtonType.button, classes = "theme-toggle") {
        attributes["id"] = "sm-theme-btn"
        attributes["onclick"] = "smToggle()"
        attributes["aria-label"] = "Toggle light/dark theme"
        +"Theme"
    }
}

/** A human-friendly breadcrumb form of a result URL (host › path). */
private fun displayUrl(rawUrl: String): String =
    runCatching {
        val uri = java.net.URI(rawUrl)
        val host = (uri.host ?: return rawUrl).removePrefix("www.")
        val segments = (uri.path ?: "").split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) host else host + " › " + segments.joinToString(" › ")
    }.getOrDefault(rawUrl)

/** Advertises the OpenSearch descriptor so Chrome/Firefox can offer SearchMob as a search engine. */
private fun HEAD.openSearchLink() {
    link(href = "/opensearch.xml", rel = "search", type = "application/opensearchdescription+xml") {
        attributes["title"] = "SearchMob"
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

// Self-contained stylesheet (no external fonts/CDNs). Theme via CSS variables: light by default,
// dark via prefers-color-scheme, and an explicit [data-theme] override (set by the toggle) wins.
@Suppress("ktlint:standard:max-line-length")
private val PAGE_CSS =
    """
    *{box-sizing:border-box}
    html,body{margin:0;padding:0}
    :root{
      --bg:#ffffff;--fg:#202124;--muted:#5f6368;--border:#dfe1e5;--card:#ffffff;
      --link:#1a0dab;--url:#0b8043;--snippet:#4d5156;--chip-bg:#f1f3f4;--chip-fg:#5f6368;
      --accent:#3d5afe;--shadow:0 1px 6px rgba(32,33,36,.12);--topbar:#ffffffee;
    }
    @media (prefers-color-scheme:dark){:root{
      --bg:#0e0f13;--fg:#e3e5e8;--muted:#9aa0a6;--border:#2a2c33;--card:#15171c;
      --link:#8ab4f8;--url:#5fd07f;--snippet:#bdc1c6;--chip-bg:#1f2127;--chip-fg:#c5c8ce;
      --accent:#8c9eff;--shadow:0 1px 6px rgba(0,0,0,.5);--topbar:#0e0f13ee;
    }}
    [data-theme="light"]{
      --bg:#ffffff;--fg:#202124;--muted:#5f6368;--border:#dfe1e5;--card:#ffffff;
      --link:#1a0dab;--url:#0b8043;--snippet:#4d5156;--chip-bg:#f1f3f4;--chip-fg:#5f6368;
      --accent:#3d5afe;--shadow:0 1px 6px rgba(32,33,36,.12);--topbar:#ffffffee;
    }
    [data-theme="dark"]{
      --bg:#0e0f13;--fg:#e3e5e8;--muted:#9aa0a6;--border:#2a2c33;--card:#15171c;
      --link:#8ab4f8;--url:#5fd07f;--snippet:#bdc1c6;--chip-bg:#1f2127;--chip-fg:#c5c8ce;
      --accent:#8c9eff;--shadow:0 1px 6px rgba(0,0,0,.5);--topbar:#0e0f13ee;
    }
    body{background:var(--bg);color:var(--fg);line-height:1.5;
      font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;}
    a{color:var(--link);text-decoration:none}
    a:hover{text-decoration:underline}
    .topbar{display:flex;align-items:center;gap:14px;padding:10px 18px;border-bottom:1px solid var(--border);
      position:sticky;top:0;background:var(--topbar);backdrop-filter:saturate(1.4) blur(8px);z-index:10}
    .topbar .logo{font-weight:800;font-size:20px;color:var(--accent);letter-spacing:-.5px;white-space:nowrap}
    .theme-toggle{margin-left:auto;background:transparent;border:1px solid var(--border);color:var(--fg);
      border-radius:20px;padding:6px 14px;cursor:pointer;font-size:13px;white-space:nowrap}
    .theme-toggle:hover{border-color:var(--accent);color:var(--accent)}
    .searchbox{display:flex;align-items:stretch;background:var(--card);border:1px solid var(--border);
      border-radius:26px;box-shadow:var(--shadow);overflow:hidden}
    .searchbox input[type=text]{flex:1;min-width:0;border:0;outline:0;background:transparent;color:var(--fg);
      font-size:16px;padding:13px 18px}
    .searchbox input[type=submit]{border:0;background:var(--accent);color:#fff;padding:0 22px;cursor:pointer;
      font-size:15px;font-weight:600}
    .searchbox input[type=submit]:hover{filter:brightness(1.07)}
    .home{max-width:600px;margin:0 auto;padding:13vh 20px 0;text-align:center}
    .home .brand{font-size:48px;font-weight:800;color:var(--accent);letter-spacing:-1.5px}
    .home .tagline{color:var(--muted);margin:8px 0 28px;font-size:15px}
    .home .searchbox{max-width:560px;margin:0 auto;text-align:left}
    .topbar .searchbox{flex:1;max-width:620px}
    .topbar .searchbox input[type=text]{padding:9px 16px}
    .topbar .searchbox input[type=submit]{padding:0 16px}
    .results{max-width:660px;margin:0 auto;padding:18px 20px 64px}
    .results .meta{color:var(--muted);font-size:13px;margin:2px 0 20px}
    .result{margin:0 0 26px}
    .result .url{color:var(--url);font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .result .title{display:block;font-size:20px;line-height:1.3;margin:1px 0 3px}
    .result .snippet{margin:2px 0 7px;color:var(--snippet);font-size:14px}
    .engines{display:flex;flex-wrap:wrap;gap:6px}
    .chip{background:var(--chip-bg);color:var(--chip-fg);font-size:11px;padding:2px 9px;border-radius:10px}
    .empty{color:var(--muted);text-align:center;padding:48px 0}
    @media (max-width:560px){.topbar .logo{display:none}}
    """.trimIndent()

// Runs in <head> before first paint to restore the saved theme (avoids a flash of the wrong theme).
private val THEME_INIT_JS =
    "(function(){try{var t=localStorage.getItem('sm-theme');" +
        "if(t){document.documentElement.setAttribute('data-theme',t);}}catch(e){}})();"

// Defines smToggle() (flips + persists the theme) and labels the button to show the alternative theme.
@Suppress("ktlint:standard:max-line-length")
private val THEME_TOGGLE_JS =
    """
    (function(){
      function resolved(){var d=document.documentElement.getAttribute('data-theme');if(d)return d;
        return (window.matchMedia&&matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light';}
      function label(){var b=document.getElementById('sm-theme-btn');
        if(b)b.textContent=resolved()==='dark'?'☀ Light':'☾ Dark';}
      window.smToggle=function(){var n=resolved()==='dark'?'light':'dark';
        document.documentElement.setAttribute('data-theme',n);
        try{localStorage.setItem('sm-theme',n);}catch(e){}label();};
      label();
    })();
    """.trimIndent()

/** Returns true if [port] can be bound on loopback right now. */
fun isLoopbackPortFree(port: Int): Boolean =
    try {
        ServerSocket().use { socket ->
            // reuseAddress=true so a port left in TIME_WAIT by a just-restarted instance is still
            // considered bindable. This keeps the default port (and thus the browser's configured
            // search URL) stable across app restarts/reboots instead of falling back to a random port.
            socket.reuseAddress = true
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
