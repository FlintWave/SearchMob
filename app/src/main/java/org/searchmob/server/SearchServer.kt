package org.searchmob.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
import kotlinx.html.hiddenInput
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules
import org.searchmob.server.suggest.NoSuggestionsProvider
import org.searchmob.server.suggest.SuggestionsProvider
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder

const val LOOPBACK_HOST = "127.0.0.1"

/** Wildcard bind address used only when the opt-in network mode is enabled (reachable off-device). */
const val ALL_INTERFACES_HOST = "0.0.0.0"
const val DEFAULT_PORT = 8787

/**
 * The address the embedded server binds to. Loopback-only by default; binds to all interfaces
 * ("0.0.0.0") only when the user has opted into network mode, so other machines on the LAN/Tailscale
 * network can reach it. Pure so the binding decision is unit-testable without a running server.
 */
fun bindHost(networkAccessEnabled: Boolean): String = if (networkAccessEnabled) ALL_INTERFACES_HOST else LOOPBACK_HOST

/** Upper bound on the accepted `q` length; longer input is truncated before reaching the provider. */
const val MAX_QUERY_LENGTH = 512

/** Maximum number of suggestions returned from `/suggest` (local + opt-in upstream, merged). */
const val MAX_SUGGESTIONS = 8

/** Content type for OpenSearch Suggestions JSON, as the spec and browsers expect. */
const val SUGGESTIONS_CONTENT_TYPE_SUBTYPE = "x-suggestions+json"

/**
 * Configures the SearchMob HTTP routes on an [Application]. Shared by the real [SearchServer] and by
 * `testApplication` tests so the HTTP contract is exercised identically. No request/access logging is
 * installed (privacy default); query data exists only in memory for the duration of a request.
 */
fun Application.searchModule(
    provider: SearchResultProvider,
    guard: RequestWakeGuard = NoopRequestWakeGuard,
    suggestionsProvider: SuggestionsProvider = NoSuggestionsProvider,
    rankingPreferences: RankingPreferences? = null,
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
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    guard.aroundRequest { provider.searchWithCorrection(query) }
                }
            val rules = rankingPreferences?.load() ?: RankingRules.EMPTY
            // Only the loopback owner gets the editing controls; a network visitor sees a read-only
            // page, because the mutation routes below are loopback-only.
            val editable = rankingPreferences != null && isOwnerRequest(call)
            call.respondHtml { renderResultsPage(query, outcome, rules, editable) }
        }
        get("/api/search") {
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    guard.aroundRequest { provider.searchWithCorrection(query) }
                }
            call.respond(
                SearchResponse(
                    query = query,
                    results = outcome.results,
                    didYouMean = outcome.didYouMean,
                    showingResultsFor = outcome.showingResultsFor,
                ),
            )
        }
        get("/suggest") {
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            // Blank/empty (including whitespace-only) returns the empty pair ["", []] and never touches
            // a suggestion source, so an idle/empty address bar costs nothing and echoes nothing back.
            if (query.isBlank()) {
                call.respondText(
                    suggestionsJson("", emptyList()),
                    ContentType("application", SUGGESTIONS_CONTENT_TYPE_SUBTYPE),
                )
                return@get
            }
            call.respondText(
                suggestionsJson(query, suggestionsProvider.suggest(query, MAX_SUGGESTIONS)),
                ContentType("application", SUGGESTIONS_CONTENT_TYPE_SUBTYPE),
            )
        }
        get("/opensearch.xml") {
            call.respondText(
                openSearchDescriptor(boundPort()),
                ContentType("application", "opensearchdescription+xml"),
            )
        }
        // Personalization edits from the served UI. Owner-only (loopback) + same-origin: a device
        // reaching the server over the network can search but cannot change the owner's rules.
        post("/rules/domain") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val params = call.receiveParameters()
            val domain = params["domain"].orEmpty().trim()
            val rule = runCatching { RankRule.valueOf(params["action"].orEmpty().trim().uppercase()) }.getOrNull()
            if (domain.isNotEmpty() && rule != null) rankingPreferences!!.setDomainRule(domain, rule)
            redirectBack(call)
        }
        post("/scope") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val lens = call.receiveParameters()["lens"].orEmpty().trim()
            rankingPreferences!!.setActiveLens(lens.ifEmpty { null })
            redirectBack(call)
        }
    }
}

/** Loopback names only: the served editing routes are for the machine's own browser, never the LAN. */
internal fun isLoopbackHost(host: String): Boolean {
    val h = host.trim().lowercase()
    return h == "localhost" || h == "::1" || h.startsWith("127.")
}

private fun isOwnerRequest(call: ApplicationCall): Boolean = isLoopbackHost(call.request.origin.remoteHost)

/** CSRF guard: a present `Origin` must be one of our own (loopback) origins; absent is same-origin. */
private fun sameOrigin(call: ApplicationCall): Boolean {
    val origin = call.request.headers["Origin"] ?: return true
    val host = runCatching { URI(origin).host }.getOrNull() ?: return false
    return isLoopbackHost(host)
}

/**
 * Gate a mutation route: respond and return false unless personalization is available and the
 * caller is the loopback owner posting from our own origin. Keeps the route bodies terse.
 */
private suspend fun guardMutation(
    call: ApplicationCall,
    rankingPreferences: RankingPreferences?,
): Boolean {
    if (rankingPreferences == null) {
        call.respondText("Personalization is read-only here.", status = HttpStatusCode.ServiceUnavailable)
        return false
    }
    if (!isOwnerRequest(call) || !sameOrigin(call)) {
        call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
        return false
    }
    return true
}

/** Return to the page the POST came from when it is one of our own origins; else home. */
private suspend fun redirectBack(call: ApplicationCall) {
    val target =
        call.request.headers["Referer"]
            ?.takeIf { runCatching { URI(it).host }.getOrNull()?.let(::isLoopbackHost) == true }
            ?: "/"
    call.respondRedirect(target, permanent = false)
}

private fun HTML.renderResultsPage(
    query: String,
    outcome: SearchOutcome,
    rules: RankingRules = RankingRules.EMPTY,
    editable: Boolean = false,
) {
    val results = outcome.results
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
                results.isEmpty() -> {
                    outcome.didYouMean?.let { didYouMeanLine(it) }
                    p("empty") { +"No results for “$query”." }
                }
                else -> {
                    if (outcome.showingResultsFor != null) {
                        p("meta") { +"Showing results for “${outcome.showingResultsFor}”" }
                    } else {
                        p("meta") { +"Results for “$query”" }
                    }
                    outcome.didYouMean?.let { didYouMeanLine(it) }
                    if (editable) scopeBar(rules)
                    results.forEach { result ->
                        div("result") {
                            div("url") { +displayUrl(result.url) }
                            // Only emit a clickable link for http(s) URLs. A javascript:/data:/file: URL
                            // would survive HTML escaping in href and could execute in the loopback origin,
                            // so render its title as plain text instead.
                            if (isSafeHttpUrl(result.url)) {
                                a(href = result.url, classes = "title") { +result.title }
                            } else {
                                span("title") { +result.title }
                            }
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
                            if (editable) rankControls(result.url, rules)
                        }
                    }
                }
            }
        }
        script { unsafe { +THEME_TOGGLE_JS } }
    }
}

/** A "Did you mean: <correction>" line linking to a fresh search for the correction. */
private fun FlowContent.didYouMeanLine(correction: String) {
    p("didyoumean") {
        +"Did you mean: "
        a(href = "/search?q=${URLEncoder.encode(correction, "UTF-8")}") { +correction }
    }
}

/** Scope (lens) selector; rendered only when the profile has at least one lens defined. */
private fun FlowContent.scopeBar(rules: RankingRules) {
    if (rules.lenses.isEmpty()) return
    form(action = "/scope", method = FormMethod.post, classes = "scopebar") {
        label { +"Scope:" }
        select {
            attributes["name"] = "lens"
            attributes["onchange"] = "this.form.submit()"
            option {
                attributes["value"] = ""
                +"No scope"
            }
            rules.lenses.forEach { lens ->
                option {
                    attributes["value"] = lens.name
                    if (lens.name == rules.activeLens) attributes["selected"] = "selected"
                    +lens.name
                }
            }
        }
        // JS auto-submits on change; this covers the JS-off case.
        button(type = ButtonType.submit) { +"Apply" }
    }
}

/** Per-result domain controls (block / lower / raise / pin / reset) as a single POST form. */
private fun FlowContent.rankControls(
    url: String,
    rules: RankingRules,
) {
    val domain = DomainRanker.host(url) ?: return
    val current = rules.domainRules[domain]
    form(action = "/rules/domain", method = FormMethod.post, classes = "rank") {
        span("state") { +domain }
        hiddenInput(name = "domain") { value = domain }
        listOf(
            RankRule.BLOCK to "Block",
            RankRule.LOWER to "Lower",
            RankRule.RAISE to "Raise",
            RankRule.PIN to "Pin",
        ).forEach { (rule, lbl) ->
            button(type = ButtonType.submit, classes = if (current == rule) "on" else "") {
                attributes["name"] = "action"
                attributes["value"] = rule.name
                +lbl
            }
        }
        if (current != null) {
            button(type = ButtonType.submit) {
                attributes["name"] = "action"
                attributes["value"] = "NORMAL"
                +"Reset"
            }
        }
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

/**
 * True only when [url] parses and uses an http or https scheme. Anything else (javascript:, data:,
 * file:, a relative/scheme-less value, or a URL that fails to parse) is treated as unsafe so it never
 * becomes a clickable href.
 */
fun isSafeHttpUrl(url: String): Boolean =
    runCatching {
        when (java.net.URI(url).scheme?.lowercase()) {
            "http", "https" -> true
            else -> false
        }
    }.getOrDefault(false)

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

/**
 * Builds the OpenSearch Suggestions JSON body: the two-element array `["<query>", ["s1", "s2", ...]]`.
 * Built with kotlinx.serialization so the query and every suggestion are correctly JSON-escaped (the
 * query is browser-controlled, so manual string concatenation would be unsafe).
 */
fun suggestionsJson(
    query: String,
    suggestions: List<String>,
): String =
    buildJsonArray {
        add(query)
        addJsonArray { suggestions.forEach { add(it) } }
    }.toString()

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
  <Url type="application/x-suggestions+json" template="$origin/suggest?q={searchTerms}"/>
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
    .scopebar{display:flex;align-items:center;gap:8px;margin:0 0 18px;font-size:13px;color:var(--muted)}
    .scopebar select{font-size:13px;padding:3px 6px;border:1px solid var(--border);border-radius:6px;background:var(--card);color:var(--fg)}
    .rank{display:flex;flex-wrap:wrap;gap:6px;margin-top:5px;align-items:center}
    .rank .state{font-size:11px;color:var(--muted);margin-right:2px}
    .rank button{font-size:11px;padding:2px 9px;border:1px solid var(--border);border-radius:10px;background:var(--card);color:var(--muted);cursor:pointer}
    .rank button:hover{border-color:var(--accent);color:var(--fg)}
    .rank button.on{background:var(--accent);color:#fff;border-color:var(--accent)}
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
 * Embedded Ktor (CIO) HTTP server. Bound ONLY to loopback by default; binds to all interfaces when the
 * opt-in network mode is enabled (see [bindHost]). Its lifecycle is owned by the foreground service.
 * Falls back to an available port if the preferred one is busy and exposes the bound port and host.
 */
class SearchServer(
    private val provider: SearchResultProvider = StubSearchResultProvider(),
    private val guard: RequestWakeGuard = NoopRequestWakeGuard,
    private val suggestionsProvider: SuggestionsProvider = NoSuggestionsProvider,
    private val rankingPreferences: RankingPreferences? = null,
) {
    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    var boundPort: Int = -1
        private set

    /** The address the running server is bound to (loopback by default), or null when stopped. */
    @Volatile
    var boundHost: String? = null
        private set

    val isRunning: Boolean get() = server != null

    /**
     * Starts the server (idempotent). Binds to loopback unless [networkAccessEnabled] is true, in which
     * case it binds to all interfaces so the LAN/Tailscale network can reach it. Returns the bound port.
     */
    @Synchronized
    fun start(
        preferredPort: Int = DEFAULT_PORT,
        networkAccessEnabled: Boolean = false,
    ): Int {
        server?.let { return boundPort }
        val host = bindHost(networkAccessEnabled)
        val port = if (isLoopbackPortFree(preferredPort)) preferredPort else freeLoopbackPort()
        val engine =
            embeddedServer(CIO, host = host, port = port) {
                searchModule(provider, guard, suggestionsProvider, rankingPreferences) { port }
            }
        engine.start(wait = false)
        server = engine
        boundPort = port
        boundHost = host
        return port
    }

    /**
     * Stops the running server (if any) and starts a fresh one on the host implied by
     * [networkAccessEnabled]. Used by the service to switch between loopback and all-interfaces binding
     * when the preference changes, without tearing down the foreground service. No-op-equivalent if the
     * host is already correct (still rebinds to keep the call simple and the binding authoritative).
     */
    @Synchronized
    fun restart(
        preferredPort: Int = DEFAULT_PORT,
        networkAccessEnabled: Boolean = false,
    ): Int {
        stop()
        return start(preferredPort, networkAccessEnabled)
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
        boundHost = null
    }
}
