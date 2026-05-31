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
import kotlinx.coroutines.flow.first
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.fileInput
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.img
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.textArea
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.Goggles
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.engine.vertical.Vertical
import org.searchmob.engine.vertical.Verticals
import org.searchmob.server.suggest.NoSuggestionsProvider
import org.searchmob.server.suggest.SuggestionsProvider
import org.searchmob.ui.prefs.PreferencesRepository
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
    userPreferences: PreferencesRepository? = null,
    historyStore: HistoryStore? = null,
    boundPort: () -> Int,
) {
    install(ContentNegotiation) { json() }

    // The owner (loopback) can reach the Settings page only when both backing stores are wired.
    fun settingsAvailable(call: ApplicationCall): Boolean =
        rankingPreferences != null && userPreferences != null && isOwnerRequest(call)
    routing {
        get("/") {
            val link = settingsAvailable(call)
            call.respondHtml { renderHomePage(link) }
        }
        get("/healthz") {
            call.respondText("ok")
        }
        get("/search") {
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            val vertical = Vertical.fromValue(call.request.queryParameters["vertical"])
            // An explicit `?sort=` wins; absent it, the vertical picks the sensible default sort.
            val sortParam = call.request.queryParameters["sort"]
            val sortMode = if (sortParam != null) SortMode.fromValue(sortParam) else Verticals.defaultSort(vertical)
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    guard.aroundRequest { provider.searchWithCorrection(query, sortMode, vertical) }
                }
            val rules = rankingPreferences?.load() ?: RankingRules.EMPTY
            // Only the loopback owner gets the editing controls; a network visitor sees a read-only
            // page, because the mutation routes below are loopback-only.
            val editable = rankingPreferences != null && isOwnerRequest(call)
            val link = settingsAvailable(call)
            call.respondHtml {
                renderResultsPage(query, outcome, rules, editable, sortMode.value, vertical.value, link)
            }
        }
        get("/api/search") {
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            val vertical = Vertical.fromValue(call.request.queryParameters["vertical"])
            val sortParam = call.request.queryParameters["sort"]
            val sortMode = if (sortParam != null) SortMode.fromValue(sortParam) else Verticals.defaultSort(vertical)
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    guard.aroundRequest { provider.searchWithCorrection(query, sortMode, vertical) }
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

        // Settings page + preference / personalization writes. Owner-only (loopback): the page 404s
        // and the writes 403 for a network visitor, and 503 when the backing store is not wired.
        get("/settings") {
            if (rankingPreferences == null || userPreferences == null || !isOwnerRequest(call)) {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
                return@get
            }
            val rules = rankingPreferences.load()
            val prefs =
                SettingsView(
                    sortMode = userPreferences.sortMode.first(),
                    aiSlopMode = userPreferences.aiSlopMode(),
                    summaryEnabled = userPreferences.summaryEnabled(),
                    upstreamSuggestionsEnabled = userPreferences.upstreamSuggestionsEnabled.first(),
                )
            val history = historyStore?.list(System.currentTimeMillis())?.take(HISTORY_VIEW_LIMIT)
            val saved = call.request.queryParameters["saved"] == "1"
            call.respondHtml { renderSettingsPage(prefs, rules, history, historyStore != null, saved) }
        }
        post("/settings/prefs") {
            if (!guardPrefs(call, userPreferences)) return@post
            val params = call.receiveParameters()
            params["sort_mode"]?.trim()?.takeIf { it in VALID_SORTS }?.let { userPreferences!!.setSortMode(it) }
            params["ai_slop_mode"]?.trim()?.takeIf { it in VALID_SLOP }?.let { userPreferences!!.setAiSlopMode(it) }
            userPreferences!!.setSummaryEnabled(params["summary_enabled"].isFormOn())
            userPreferences.setUpstreamSuggestionsEnabled(params["upstream_suggestions_enabled"].isFormOn())
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        post("/settings/lens") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val params = call.receiveParameters()
            val name = params["name"].orEmpty().trim()
            if (name.isNotEmpty()) {
                rankingPreferences!!.upsertLens(
                    Lens(
                        name = name,
                        includeDomains = csvList(params["include_domains"]),
                        excludeDomains = csvList(params["exclude_domains"]),
                        includeKeywords = csvList(params["include_keywords"]),
                        excludeKeywords = csvList(params["exclude_keywords"]),
                    ),
                )
            }
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        post("/settings/lens/delete") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val name = call.receiveParameters()["name"].orEmpty().trim()
            if (name.isNotEmpty()) rankingPreferences!!.removeLens(name)
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        post("/settings/goggles") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val text = call.receiveParameters()["goggles"].orEmpty().take(MAX_GOGGLE_CHARS)
            val parsed = Goggles.parse(text)
            if (parsed.isNotEmpty()) rankingPreferences!!.addGoggles(parsed)
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        post("/settings/goggles/clear") {
            if (!guardMutation(call, rankingPreferences)) return@post
            rankingPreferences!!.clearGoggles()
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        post("/settings/history/clear") {
            if (!guardPrefs(call, userPreferences)) return@post
            historyStore?.clear()
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
    }
}

/** The live preference values the served Settings page renders. */
private data class SettingsView(
    val sortMode: String,
    val aiSlopMode: String,
    val summaryEnabled: Boolean,
    val upstreamSuggestionsEnabled: Boolean,
)

private val VALID_SORTS = setOf("fresh", "date", "relevance")
private val VALID_SLOP = setOf("off", "downrank", "hide")
private const val HISTORY_VIEW_LIMIT = 50
private const val MAX_GOGGLE_CHARS = 512 * 1024

/** A form checkbox is present (any value) when checked, absent when not; HTML omits unchecked boxes. */
private fun String?.isFormOn(): Boolean = this != null

/** Split a comma/newline-separated form field into clean, lowercased, de-duplicated entries. */
private fun csvList(raw: String?): List<String> =
    raw.orEmpty()
        .replace("\n", ",")
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

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

/** Like [guardMutation] but for the Settings preference / history routes (backed by [userPreferences]). */
private suspend fun guardPrefs(
    call: ApplicationCall,
    userPreferences: PreferencesRepository?,
): Boolean {
    if (userPreferences == null) {
        call.respondText("Settings are read-only here.", status = HttpStatusCode.ServiceUnavailable)
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
    sortMode: String = "fresh",
    vertical: String = "web",
    settingsLink: Boolean = false,
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
            settingsLink(settingsLink)
            themeToggle()
        }
        div("results") {
            // Category tabs render whenever there is a query, so the user can switch verticals even
            // from a vertical that returned nothing.
            if (query.isNotBlank()) verticalBar(query, vertical)
            if (query.isNotBlank()) outcome.summary?.let { summaryBox(it) }
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
                    sortBar(query, sortMode)
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

/** A knowledge-panel-style Wikipedia summary card shown above the results. */
private fun FlowContent.summaryBox(summary: WikiSummary) {
    div("summary") {
        if (summary.thumbnailUrl != null && isSafeHttpUrl(summary.thumbnailUrl)) {
            img(src = summary.thumbnailUrl, alt = "") { attributes["loading"] = "lazy" }
        }
        div("sbody") {
            p("stitle") {
                if (summary.url.isNotBlank() && isSafeHttpUrl(summary.url)) {
                    a(href = summary.url) {
                        attributes["rel"] = "noopener noreferrer"
                        +summary.title
                    }
                } else {
                    +summary.title
                }
            }
            if (summary.description.isNotBlank()) p("sdesc") { +summary.description }
            p("sextract") { +summary.extract }
            p("ssource meta") { +"From Wikipedia" }
        }
    }
}

/** Result sort selector. GET so the choice is bookmarkable; carries the query in a hidden field. */
private fun FlowContent.sortBar(
    query: String,
    sortMode: String,
) {
    form(action = "/search", method = FormMethod.get, classes = "scopebar") {
        hiddenInput(name = "q") { value = query }
        label { +"Sort:" }
        select {
            attributes["name"] = "sort"
            attributes["onchange"] = "this.form.submit()"
            listOf(
                "fresh" to "Freshest + Relevant",
                "date" to "Date",
                "relevance" to "Relevance",
            ).forEach { (value, lbl) ->
                option {
                    attributes["value"] = value
                    if (value == sortMode) attributes["selected"] = "selected"
                    +lbl
                }
            }
        }
        button(type = ButtonType.submit) { +"Apply" }
    }
}

/**
 * Category tabs (Web / News / Forums / Academic) as GET links carrying the current query. Each link
 * re-runs the search scoped to that vertical; the active one is marked so CSS can style it.
 */
private fun FlowContent.verticalBar(
    query: String,
    vertical: String,
) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    div("verticalbar") {
        listOf(
            "web" to "Web",
            "news" to "News",
            "forums" to "Forums",
            "academic" to "Academic",
        ).forEach { (value, lbl) ->
            val classes = if (value == vertical) "chip active" else "chip"
            a(href = "/search?q=$encoded&vertical=$value", classes = classes) { +lbl }
        }
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
private fun HTML.renderHomePage(settingsLink: Boolean = false) {
    head { pageHead("SearchMob") }
    body {
        attributes["data-page"] = "home"
        div("topbar") {
            span("logo") { +"SearchMob" }
            settingsLink(settingsLink)
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

/** A Settings-page link for the loopback owner (the route itself is owner-only). */
private fun FlowContent.settingsLink(show: Boolean) {
    if (show) a(href = "/settings", classes = "settings-link") { +"Settings" }
}

private fun FlowContent.selectField(
    name: String,
    options: List<Pair<String, String>>,
    current: String,
) {
    select {
        attributes["name"] = name
        options.forEach { (value, lbl) ->
            option {
                attributes["value"] = value
                if (value == current) attributes["selected"] = "selected"
                +lbl
            }
        }
    }
}

private fun FlowContent.checkRow(
    name: String,
    lbl: String,
    checked: Boolean,
) {
    label("checkrow") {
        checkBoxInput(name = name) {
            value = "on"
            if (checked) attributes["checked"] = "checked"
        }
        +" $lbl"
    }
}

/** The browser Settings page: live preference toggles plus rule / scope / goggle / history management. */
private fun HTML.renderSettingsPage(
    prefs: SettingsView,
    rules: RankingRules,
    history: List<HistoryEntry>?,
    historyClearable: Boolean,
    saved: Boolean,
) {
    head { pageHead("Settings · SearchMob") }
    body {
        attributes["data-page"] = "settings"
        div("topbar") {
            a(href = "/", classes = "logo") { +"SearchMob" }
            span("spacer") {}
            themeToggle()
        }
        div("settings") {
            h1 { +"Settings" }
            if (saved) p("saved") { +"Saved." }

            form(action = "/settings/prefs", method = FormMethod.post) {
                section("card") {
                    h2 { +"Search & ranking" }
                    div("field") {
                        label { +"Default sort" }
                        selectField(
                            "sort_mode",
                            listOf(
                                "fresh" to "Freshest + Relevant",
                                "date" to "Date (newest first)",
                                "relevance" to "Relevance",
                            ),
                            prefs.sortMode,
                        )
                    }
                    div("field") {
                        label { +"AI-slop / low-quality filter" }
                        selectField(
                            "ai_slop_mode",
                            listOf("downrank" to "Downrank (default)", "hide" to "Hide", "off" to "Off"),
                            prefs.aiSlopMode,
                        )
                        p("hint") { +"Applied on-device after your own domain rules, which always win." }
                    }
                }
                section("card") {
                    h2 { +"Suggestions" }
                    checkRow("summary_enabled", "Show the Wikipedia summary card", prefs.summaryEnabled)
                    checkRow(
                        "upstream_suggestions_enabled",
                        "Use upstream autocomplete suggestions",
                        prefs.upstreamSuggestionsEnabled,
                    )
                    p("hint") {
                        +"Upstream autocomplete sends what you type to a suggestions service; your "
                        +"on-device history suggestions are always private."
                    }
                }
                div("actions") { button(type = ButtonType.submit) { +"Save" } }
            }

            domainRulesCard(rules)
            scopesCard(rules)
            gogglesCard(rules)
            if (history != null) historyCard(history, historyClearable)
        }
        script { unsafe { +THEME_TOGGLE_JS } }
        script { unsafe { +GOGGLE_FILE_JS } }
    }
}

private fun FlowContent.domainRulesCard(rules: RankingRules) {
    section("card") {
        h2 { +"Domain rules" }
        if (rules.domainRules.isNotEmpty()) {
            ul("rulelist") {
                rules.domainRules.toSortedMap().forEach { (domain, rule) ->
                    li {
                        span("dom") { +domain }
                        form(action = "/rules/domain", method = FormMethod.post, classes = "rank") {
                            hiddenInput(name = "domain") { value = domain }
                            listOf(
                                RankRule.BLOCK to "Block",
                                RankRule.LOWER to "Lower",
                                RankRule.RAISE to "Raise",
                                RankRule.PIN to "Pin",
                            ).forEach { (r, lbl) ->
                                button(type = ButtonType.submit, classes = if (r == rule) "on" else "") {
                                    attributes["name"] = "action"
                                    attributes["value"] = r.name
                                    +lbl
                                }
                            }
                            button(type = ButtonType.submit) {
                                attributes["name"] = "action"
                                attributes["value"] = "NORMAL"
                                +"Reset"
                            }
                        }
                    }
                }
            }
        } else {
            p(
                "hint",
            ) { +"No domain rules yet. Add one below, or use the Block / Lower / Raise / Pin buttons on any result." }
        }
        form(action = "/rules/domain", method = FormMethod.post, classes = "addrule") {
            textInput(name = "domain") {
                placeholder = "example.com"
                attributes["autocomplete"] = "off"
                attributes["required"] = "required"
            }
            selectField(
                "action",
                listOf("RAISE" to "Raise", "LOWER" to "Lower", "BLOCK" to "Block", "PIN" to "Pin"),
                "RAISE",
            )
            button(type = ButtonType.submit) { +"Add rule" }
        }
    }
}

private fun FlowContent.lensForm(lens: Lens?) {
    form(action = "/settings/lens", method = FormMethod.post, classes = "lensform") {
        textInput(name = "name", classes = "lname") {
            value = lens?.name ?: ""
            placeholder = "Scope name"
            attributes["autocomplete"] = "off"
            attributes["required"] = "required"
        }
        listOf(
            "include_domains" to ("Only these domains" to (lens?.includeDomains ?: emptyList())),
            "exclude_domains" to ("Exclude these domains" to (lens?.excludeDomains ?: emptyList())),
            "include_keywords" to ("Require these keywords" to (lens?.includeKeywords ?: emptyList())),
            "exclude_keywords" to ("Exclude these keywords" to (lens?.excludeKeywords ?: emptyList())),
        ).forEach { (fieldName, labelAndValues) ->
            val (lbl, values) = labelAndValues
            label("lf") {
                +lbl
                textInput(name = fieldName) {
                    value = values.joinToString(", ")
                    placeholder = "comma separated"
                    attributes["autocomplete"] = "off"
                }
            }
        }
        button(type = ButtonType.submit) { +"Save scope" }
    }
}

private fun FlowContent.scopesCard(rules: RankingRules) {
    section("card") {
        h2 { +"Scopes (lenses)" }
        p("hint") {
            +"A scope filters results to the domains and keywords you choose. "
            +"Set the active scope here, or per-search from the results page."
        }
        if (rules.lenses.isNotEmpty()) {
            form(action = "/scope", method = FormMethod.post, classes = "scopebar") {
                label { +"Active scope" }
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
                button(type = ButtonType.submit) { +"Apply" }
            }
            rules.lenses.forEach { lens ->
                div("lensitem") {
                    lensForm(lens)
                    form(action = "/settings/lens/delete", method = FormMethod.post, classes = "lensdel") {
                        hiddenInput(name = "name") { value = lens.name }
                        button(type = ButtonType.submit) { +"Delete" }
                    }
                }
            }
        }
        h3("sub") { +"Create a scope" }
        lensForm(null)
    }
}

private fun FlowContent.gogglesCard(rules: RankingRules) {
    section("card") {
        h2 { +"Goggles" }
        p("hint") {
            +"Brave-style goggle rules, applied on-device. Example: "
            span("code") { +"\$discard,site=example.com" }
            +" or "
            span("code") { +"\$boost,site=dev.to" }
            +"."
        }
        if (rules.goggles.isNotEmpty()) {
            ul("gogglelist") {
                rules.goggles.forEach { g ->
                    li {
                        span("site") { +g.site }
                        span("act") { +goggleActionLabel(g.action) }
                    }
                }
            }
            form(action = "/settings/goggles/clear", method = FormMethod.post, classes = "goggleclear") {
                button(type = ButtonType.submit) { +"Clear all ${rules.goggles.size} rules" }
            }
        } else {
            p("hint") { +"No goggle rules imported yet." }
        }
        form(action = "/settings/goggles", method = FormMethod.post, classes = "goggleimport") {
            textArea {
                attributes["id"] = "sm-goggle-text"
                attributes["name"] = "goggles"
                attributes["rows"] = "4"
                attributes["placeholder"] = "Paste goggle rules, one per line"
            }
            div("grow") {
                fileInput {
                    attributes["accept"] = ".goggle,.txt,text/plain"
                    attributes["onchange"] = "smLoadGoggle(this)"
                }
                button(type = ButtonType.submit) { +"Import (append)" }
            }
        }
    }
}

private fun goggleActionLabel(action: RankRule): String =
    when (action) {
        RankRule.BLOCK -> "discard"
        RankRule.RAISE -> "boost"
        RankRule.LOWER -> "downrank"
        RankRule.PIN -> "pin"
        RankRule.NORMAL -> "normal"
    }

private fun FlowContent.historyCard(
    history: List<HistoryEntry>,
    clearable: Boolean,
) {
    section("card") {
        h2 { +"Search history" }
        if (history.isNotEmpty()) {
            ul("histlist") { history.forEach { li { +it.query } } }
            if (clearable) {
                form(action = "/settings/history/clear", method = FormMethod.post, classes = "histclear") {
                    button(type = ButtonType.submit) { +"Clear search history" }
                }
            }
        } else {
            p("hint") { +"No search history (history is off, or nothing recorded yet)." }
        }
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
    .summary{display:flex;gap:14px;border:1px solid var(--border);border-radius:12px;background:var(--card);padding:14px 16px;margin:0 0 22px;box-shadow:var(--shadow)}
    .summary .sbody{flex:1;min-width:0}
    .summary .stitle{font-size:17px;font-weight:600;margin:0}
    .summary .stitle a{color:var(--fg)}
    .summary .sdesc{color:var(--muted);font-size:12px;margin:1px 0 6px}
    .summary .sextract{font-size:14px;margin:0 0 6px;line-height:1.45}
    .summary .ssource{font-size:12px}
    .summary img{width:84px;height:84px;object-fit:cover;border-radius:8px;flex:none}
    @media (max-width:560px){.summary img{display:none}}
    .verticalbar{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 16px}
    .verticalbar .chip{font-size:13px;padding:5px 14px;border:1px solid var(--border);border-radius:16px;color:var(--fg);background:var(--card)}
    .verticalbar .chip.active{background:var(--accent);color:#fff;border-color:var(--accent)}
    .verticalbar .chip:hover{text-decoration:none;border-color:var(--accent)}
    .scopebar{display:flex;align-items:center;gap:8px;margin:0 0 18px;font-size:13px;color:var(--muted)}
    .scopebar select{font-size:13px;padding:3px 6px;border:1px solid var(--border);border-radius:6px;background:var(--card);color:var(--fg)}
    .rank{display:flex;flex-wrap:wrap;gap:6px;margin-top:5px;align-items:center}
    .rank .state{font-size:11px;color:var(--muted);margin-right:2px}
    .rank button{font-size:11px;padding:2px 9px;border:1px solid var(--border);border-radius:10px;background:var(--card);color:var(--muted);cursor:pointer}
    .rank button:hover{border-color:var(--accent);color:var(--fg)}
    .rank button.on{background:var(--accent);color:#fff;border-color:var(--accent)}
    .settings-link{margin-left:auto;border:1px solid var(--border);color:var(--fg);border-radius:20px;padding:6px 14px;font-size:13px;text-decoration:none;white-space:nowrap}
    .settings-link:hover{border-color:var(--accent);color:var(--accent)}
    .settings-link+.theme-toggle{margin-left:0}
    .topbar .spacer{margin-left:auto}
    .settings{max-width:680px;margin:0 auto;padding:24px 18px 60px}
    .settings h1{font-size:24px;margin:8px 0 18px}
    .settings .saved{color:#fff;background:var(--accent);display:inline-block;border-radius:6px;padding:4px 12px;font-size:13px;margin:0 0 16px}
    .settings .card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:16px 18px;margin:0 0 16px}
    .settings .card h2{font-size:15px;margin:0 0 14px;color:var(--accent)}
    .settings .card h3.sub{font-size:13px;margin:16px 0 8px;color:var(--muted)}
    .settings .field{margin:0 0 14px}
    .settings .field>label{display:block;font-size:13px;margin:0 0 6px;font-weight:600}
    .settings select{width:100%;padding:9px 12px;border:1px solid var(--border);border-radius:8px;background:var(--bg);color:var(--fg);font-size:14px}
    .settings .checkrow{display:flex;align-items:center;gap:9px;font-size:14px;margin:0 0 10px;cursor:pointer}
    .settings .hint{font-size:12px;color:var(--muted);margin:6px 0 0}
    .settings .hint .code{background:var(--chip-bg);color:var(--chip-fg);padding:1px 5px;border-radius:5px;font-size:12px}
    .settings .actions{margin-top:6px}
    .settings .actions button{background:var(--accent);color:#fff;border:0;border-radius:22px;padding:10px 26px;font-size:15px;font-weight:600;cursor:pointer}
    .settings .rulelist,.settings .gogglelist,.settings .histlist{list-style:none;margin:0 0 14px;padding:0;font-size:13px}
    .settings .rulelist li{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:8px 0;border-bottom:1px solid var(--border)}
    .settings .rulelist .dom{font-weight:600;word-break:break-all}
    .settings .rulelist .rank{margin-left:auto}
    .settings .addrule{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
    .settings .addrule input[type=text]{flex:1;min-width:140px;padding:8px 11px;border:1px solid var(--border);border-radius:8px;background:var(--bg);color:var(--fg);font-size:14px}
    .settings .addrule select{width:auto;min-width:110px}
    .settings .addrule button,.settings .lensform button,.settings .lensdel button,.settings .goggleimport button,.settings .goggleclear button,.settings .histclear button{background:var(--accent);color:#fff;border:0;border-radius:18px;padding:8px 18px;font-size:13px;font-weight:600;cursor:pointer}
    .settings .lensitem{display:flex;gap:10px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--border)}
    .settings .lensform{flex:1;display:flex;flex-direction:column;gap:8px}
    .settings .lensform .lname{font-weight:600}
    .settings .lensform input[type=text]{width:100%;padding:8px 11px;border:1px solid var(--border);border-radius:8px;background:var(--bg);color:var(--fg);font-size:14px}
    .settings .lensform .lf{display:flex;flex-direction:column;gap:3px;font-size:12px;color:var(--muted)}
    .settings .lensform button{align-self:flex-start}
    .settings .lensdel button,.settings .goggleclear button,.settings .histclear button{background:transparent;color:var(--muted);border:1px solid var(--border)}
    .settings .lensdel button:hover,.settings .goggleclear button:hover,.settings .histclear button:hover{border-color:#d33;color:#d33}
    .settings .gogglelist li{display:flex;gap:8px;align-items:center;padding:5px 0;border-bottom:1px solid var(--border)}
    .settings .gogglelist .site{font-weight:600;word-break:break-all}
    .settings .gogglelist .act{margin-left:auto;font-size:11px;color:var(--muted)}
    .settings .histlist li{padding:4px 0;border-bottom:1px solid var(--border);word-break:break-word}
    .settings .goggleimport{display:flex;flex-direction:column;gap:8px}
    .settings textarea{width:100%;padding:9px 11px;border:1px solid var(--border);border-radius:8px;background:var(--bg);color:var(--fg);font-size:13px;font-family:ui-monospace,monospace;resize:vertical}
    .settings .goggleimport .grow{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
    .settings .goggleclear,.settings .histclear{margin:0 0 8px}
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

// Reads a chosen .goggle file into the textarea so "upload" works without a multipart parser: the
// file never leaves the browser; its text just fills the field the normal urlencoded POST sends.
private val GOGGLE_FILE_JS =
    """
    function smLoadGoggle(input){var f=input.files&&input.files[0];if(!f)return;
      var r=new FileReader();r.onload=function(e){
        document.getElementById('sm-goggle-text').value=e.target.result;};r.readAsText(f);}
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
    private val userPreferences: PreferencesRepository? = null,
    private val historyStore: HistoryStore? = null,
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
                searchModule(
                    provider,
                    guard,
                    suggestionsProvider,
                    rankingPreferences,
                    userPreferences,
                    historyStore,
                ) { port }
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
