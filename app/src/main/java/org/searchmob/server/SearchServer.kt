package org.searchmob.server

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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
import kotlinx.html.details
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
import kotlinx.html.nav
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.summary
import kotlinx.html.textArea
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import org.searchmob.R
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.prefs.PersonalizationPreferences
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.ActionsRow
import org.searchmob.engine.MediaCategory
import org.searchmob.engine.bang.Bangs
import org.searchmob.engine.instant.InstantAnswer
import org.searchmob.engine.instant.InstantAnswers
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.aggregate.EngineStatus
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.Goggles
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.Personalizer
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.rank.ScopeToken
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.engine.vertical.Vertical
import org.searchmob.engine.vertical.Verticals
import org.searchmob.i18n.SupportedLocales
import org.searchmob.server.suggest.NoSuggestionsProvider
import org.searchmob.server.suggest.SuggestionsProvider
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.theme.APP_THEMES
import org.searchmob.ui.theme.AppTheme
import org.searchmob.ui.theme.DEFAULT_DARK_ID
import org.searchmob.ui.theme.DEFAULT_FONT_POINT_SIZE
import org.searchmob.ui.theme.DEFAULT_LIGHT_ID
import org.searchmob.ui.theme.FONT_POINT_STEP
import org.searchmob.ui.theme.MAX_FONT_POINT_SIZE
import org.searchmob.ui.theme.MIN_FONT_POINT_SIZE
import org.searchmob.ui.theme.ThemePaletteMode
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

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

/** Cap on remembered owner renders for click-tracking; small since only recent pages need links. */
const val RENDER_CACHE_MAX = 64

/** One owner-rendered result page: the query and the displayed (url, host) order. Never persisted. */
data class RenderedResults(
    val query: String,
    val items: List<Pair<String, String?>>,
)

/** One engine, for the owner-only served Settings engine-enable toggles. */
data class EngineCatalogEntry(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean = false,
)

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
    // Lets owner clicks on the served results page train the learned model (loopback-only). When
    // null, or when `personalizationEnabled` is false, the served page renders plain result links
    // and the `/click` learning route records nothing.
    personalizationPreferences: PersonalizationPreferences? = null,
    personalizationEnabled: suspend () -> Boolean = { false },
    // Owner-only "update available" banner. Returns (version, releaseUrl) when a newer release is
    // pending, or null. Rendered only for the loopback owner (a network visitor can't install
    // anything and the owner's version is not leaked). Default null = no banner.
    updateBanner: suspend () -> Pair<String, String>? = { null },
    // Network-mode access control (mirrors the desktop `_SecurityHeadersMiddleware`). When the server
    // is bound to all interfaces, a non-loopback client hitting a query route must present this token,
    // and its Host header must be loopback / an IP literal / one of `allowedHosts` (DNS-rebind guard).
    // Loopback clients are always exempt. Empty token + empty allowedHosts = loopback-only behavior.
    accessToken: String? = null,
    allowedHosts: Set<String> = emptySet(),
    // Application context for localizing the served chrome. When null (tests / no context), the pages
    // render in English; in the app it is the application context, so each request renders in the
    // resolved UI language with the right text direction.
    appContext: Context? = null,
    // The engine catalog (id, display name, key requirement) backing the served Settings engine
    // toggles. Empty = no engine card (tests / callers that don't wire it).
    engineCatalog: List<EngineCatalogEntry> = emptyList(),
    // Server-side fetcher behind the `/img` thumbnail proxy (see ThumbnailProxy). Null (tests /
    // callers that don't wire it) disables the proxy and the summary card renders without an image,
    // never falling back to a direct third-party fetch from the user's browser.
    imageProxy: (suspend (String) -> ProxiedImage?)? = null,
    boundPort: () -> Int,
) {
    install(ContentNegotiation) { json() }

    // Resolve one request's UI language: the owner's saved language wins; absent that, the visitor's
    // Accept-Language (first supported entry); absent that, the OS language (else English). Mirrors
    // the desktop served-page precedence.
    suspend fun resolveLocale(call: ApplicationCall): String {
        val pinned = userPreferences?.language?.first().orEmpty()
        if (pinned.isNotBlank() && SupportedLocales.isSupported(pinned)) return SupportedLocales.normalizeTag(pinned)
        val header = call.request.headers["Accept-Language"].orEmpty()
        val fromHeader =
            header.split(",").map { it.substringBefore(";").trim() }.firstOrNull { SupportedLocales.isSupported(it) }
        if (fromHeader != null) return SupportedLocales.normalizeTag(fromHeader)
        return SupportedLocales.resolveSystemTag()
    }

    // Build the localized chrome-string bundle for a request. A null app context yields English.
    suspend fun servedText(call: ApplicationCall): ServedText {
        val tag = resolveLocale(call)
        val res =
            appContext?.let { ctx ->
                val config =
                    Configuration(
                        ctx.resources.configuration,
                    ).apply { setLocale(SupportedLocales.javaLocaleFor(tag)) }
                ctx.createConfigurationContext(config).resources
            }
        return ServedText(tag, SupportedLocales.isRtl(tag), res)
    }

    // Run before routing on every request: conservative security headers always, plus the Host
    // allowlist and the token gate for non-loopback clients.
    intercept(ApplicationCallPipeline.Plugins) {
        // `same-origin`, not `no-referrer`: a same-origin request (including our OWN served forms)
        // still carries its Referer/Origin to us, which `sameOrigin()` needs to tell a genuine
        // same-origin POST apart from a cross-site one that forces an opaque `Origin: null` (see
        // `sameOrigin()`'s doc comment for the attack `no-referrer` used to enable). A cross-origin
        // navigation away from us still sends nothing, same as before, and every result/summary/action
        // link additionally carries `rel="noreferrer"` as a second, redundant layer.
        call.response.headers.append("Referrer-Policy", "same-origin", safeOnly = false)
        call.response.headers.append("Content-Security-Policy", CSP, safeOnly = false)
        call.response.headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
        call.response.headers.append("X-Frame-Options", "DENY", safeOnly = false)
        // Queries, titles, snippets, and Settings values must never persist in a browser or
        // intermediary cache once the page is gone.
        call.response.headers.append("Cache-Control", "no-store", safeOnly = false)
        // We never touch the camera/location/microphone; deny every embedder (and ourselves) the asks.
        call.response.headers.append(
            "Permissions-Policy",
            "camera=(), geolocation=(), microphone=()",
            safeOnly = false,
        )
        if (!isLoopbackHost(call.request.origin.remoteHost)) {
            val host = hostnameOnly(call.request.headers["Host"].orEmpty())
            if (host.isNotEmpty() && !hostHeaderAllowed(host, allowedHosts)) {
                call.respondText("Bad Request: host not allowed", status = HttpStatusCode.BadRequest)
                return@intercept finish()
            }
            val presented =
                presentedToken(
                    call.request.queryParameters["token"],
                    call.request.headers["Authorization"],
                    call.request.headers["X-SearchMob-Token"],
                )
            if (call.request.path() in GATED_PATHS && !tokenMatches(presented, accessToken)) {
                call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                return@intercept finish()
            }
        }
    }

    // Resolve the sort: an explicit `?sort=` wins; otherwise the plain Web view honors the user's
    // saved default sort (mirroring the desktop `search_html`), and a non-default vertical keeps its
    // own sensible default. Without a prefs store wired, fall back to the vertical default.
    suspend fun resolveSort(
        sortParam: String?,
        vertical: Vertical,
    ): SortMode =
        when {
            sortParam != null -> SortMode.fromValue(sortParam)
            vertical == Vertical.WEB && userPreferences != null -> SortMode.fromValue(userPreferences.sortMode.first())
            else -> Verticals.defaultSort(vertical)
        }

    // The owner (loopback) can reach the Settings page only when both backing stores are wired.
    fun settingsAvailable(call: ApplicationCall): Boolean =
        rankingPreferences != null && userPreferences != null && isOwnerRequest(call)

    // Per-server, in-memory map of recent owner renders (render id -> the displayed (url, host)
    // order). Used only to resolve an owner click on the served page back to its result and its
    // skipped-above neighbors for `/click`, so the redirect target is server state and never a
    // caller-supplied URL. Bounded and never persisted. Mirrors the desktop `_render_cache`.
    val renderCache =
        object : LinkedHashMap<String, RenderedResults>() {
            override fun removeEldestEntry(eldest: Map.Entry<String, RenderedResults>): Boolean =
                size > RENDER_CACHE_MAX
        }
    val renderRng = SecureRandom()

    fun registerRender(
        query: String,
        results: List<SearchResult>,
    ): String {
        val bytes = ByteArray(9).also { renderRng.nextBytes(it) }
        val rid = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val items = results.map { it.url to DomainRanker.host(it.url) }
        synchronized(renderCache) { renderCache[rid] = RenderedResults(query, items) }
        return rid
    }

    routing {
        get("/") {
            val link = settingsAvailable(call)
            val owner = rankingPreferences != null && isOwnerRequest(call)
            val rules = if (owner) rankingPreferences.load() else null
            val banner = if (isOwnerRequest(call)) updateBanner() else null
            val text = servedText(call)
            call.respondHtml { renderHomePage(text, link, rules, owner, banner) }
        }
        get("/healthz") {
            call.respondText("ok")
        }
        get("/search") {
            val rawQuery = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            // DuckDuckGo-style !bangs jump straight to the named site's own search, resolved from the
            // on-device table before any engine is contacted (the terms never enter the fan-out).
            // Only an exact known tag triggers, so a query like `!important css` is never hijacked.
            Bangs.resolve(rawQuery)?.let { redirect ->
                if (isSafeHttpUrl(redirect.url)) {
                    call.respondRedirect(redirect.url, permanent = false)
                    return@get
                }
            }
            val vertical = Vertical.fromValue(call.request.queryParameters["vertical"])
            // An explicit `?sort=` wins; absent it, the vertical picks the sensible default sort.
            val sortParam = call.request.queryParameters["sort"]
            val sortMode = resolveSort(sortParam, vertical)
            val rules = rankingPreferences?.load() ?: RankingRules.EMPTY
            // An inline `+name` token applies a saved scope to this one search, additively and
            // without persisting it. The engines, summary, and correction run on the cleaned query;
            // the original text is echoed in the box so the token round-trips on a re-search.
            val (query, scope) = ScopeToken.parse(rawQuery, rules)
            val startedAtNanos = System.nanoTime()
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    // Personalize only for the loopback owner; a network visitor gets engine order.
                    val owner = isOwnerRequest(call)
                    guard.aroundRequest {
                        provider.searchWithCorrection(query, sortMode, vertical, owner, scope)
                    }
                }
            // Elapsed wall time for the meta line ("N results · 0.8 s"), computed locally and never
            // recorded anywhere. Instant answers (calculator/conversions) are pure on-device string
            // work over the query - no network, no storage.
            val tookMs = (System.nanoTime() - startedAtNanos) / 1_000_000
            val instantAnswer = if (query.isBlank()) null else InstantAnswers.answer(query)
            // Only the loopback owner gets the editing controls; a network visitor sees a read-only
            // page, because the mutation routes below are loopback-only.
            val editable = rankingPreferences != null && isOwnerRequest(call)
            val link = settingsAvailable(call)
            // Route the owner's result links through `/click` so a click can train the model, but
            // only when personalization is on; everyone else (and a disabled owner) gets plain links.
            val linkBuilder: ((Int, String) -> String)? =
                if (isOwnerRequest(call) && outcome.results.isNotEmpty() && personalizationEnabled()) {
                    val rid = registerRender(query, outcome.results)
                    val builder: (Int, String) -> String = { pos, _ -> "/click?rid=$rid&pos=$pos" }
                    builder
                } else {
                    null
                }
            val banner = if (isOwnerRequest(call)) updateBanner() else null
            val text = servedText(call)
            call.respondHtml {
                renderResultsPage(
                    text,
                    rawQuery,
                    outcome,
                    rules,
                    editable,
                    sortMode.value,
                    vertical.value,
                    link,
                    linkBuilder,
                    banner,
                    instantAnswer,
                    tookMs.takeIf { query.isNotBlank() },
                    proxyThumbnails = imageProxy != null,
                    explicitSort = sortParam?.let { sortMode.value },
                )
            }
        }
        // Loopback re-serve of the Wikipedia summary thumbnail (see ThumbnailProxy): the browser asks
        // US for the image, and the app fetches it through the privacy-proxied HTTP stack, so the
        // user's IP and the searched entity's name never reach Wikimedia from the browser.
        get("/img") {
            val target = call.request.queryParameters["u"].orEmpty()
            val proxy = imageProxy
            val image =
                if (proxy != null && ThumbnailProxy.isAllowed(target)) {
                    runCatching { guard.aroundRequest { proxy(target) } }.getOrNull()
                } else {
                    null
                }
            if (image == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
            } else {
                call.respondBytes(image.bytes, ContentType.parse(image.contentType))
            }
        }
        get("/favicon.ico") {
            call.respondBytes(FAVICON_SVG.toByteArray(Charsets.UTF_8), ContentType("image", "svg+xml"))
        }
        get("/api/search") {
            val rawQuery = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_LENGTH)
            val vertical = Vertical.fromValue(call.request.queryParameters["vertical"])
            val sortParam = call.request.queryParameters["sort"]
            val sortMode = resolveSort(sortParam, vertical)
            // Same inline `+name` scope token as the HTML route: applied to this request only, with
            // the engines/correction run on the cleaned query and the original echoed back as `query`.
            val rules = rankingPreferences?.load() ?: RankingRules.EMPTY
            val (query, scope) = ScopeToken.parse(rawQuery, rules)
            val outcome =
                if (query.isBlank()) {
                    SearchOutcome(
                        emptyList(),
                    )
                } else {
                    val owner = isOwnerRequest(call)
                    guard.aroundRequest {
                        provider.searchWithCorrection(query, sortMode, vertical, owner, scope)
                    }
                }
            call.respond(
                SearchResponse(
                    query = rawQuery,
                    results = outcome.results,
                    didYouMean = outcome.didYouMean,
                    showingResultsFor = outcome.showingResultsFor,
                ),
            )
        }
        // Owner-only click redirector that learns from a click on the served results page. It
        // resolves the destination from server-side render state (never a caller-supplied URL), so
        // it cannot be an open redirect, and it trains only the loopback owner's model. Mirrors the
        // desktop `/click` route.
        get("/click") {
            if (!isOwnerRequest(call)) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            val rid = call.request.queryParameters["rid"].orEmpty()
            val render = synchronized(renderCache) { renderCache[rid] }
            val pos = call.request.queryParameters["pos"]?.toIntOrNull()
            if (render == null || pos == null || pos < 0 || pos >= render.items.size) {
                call.respondRedirect("/", permanent = false)
                return@get
            }
            val destUrl = render.items[pos].first
            if (!isSafeHttpUrl(destUrl)) {
                call.respondRedirect("/", permanent = false)
                return@get
            }
            if (personalizationPreferences != null && personalizationEnabled()) {
                runCatching {
                    val model = personalizationPreferences.load()
                    val hosts = render.items.map { it.second }
                    Personalizer.updateFromClick(
                        model,
                        hosts,
                        pos,
                        Personalizer.queryTerms(render.query),
                        System.currentTimeMillis(),
                    )
                    personalizationPreferences.save(model)
                }
            }
            call.respondRedirect(destUrl, permanent = false)
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
            // A network-mode visitor's browser must template ITS route to us (the LAN/Tailscale host
            // it fetched this from), not our loopback address, or "add search engine" silently breaks
            // off-device. The Host header has already passed the DNS-rebind allowlist above. The
            // access token is deliberately NOT embedded: the descriptor is unauthenticated, so a
            // token in it would hand access to anyone on the network who can fetch this file.
            val requestHost = call.request.headers["Host"].orEmpty()
            val origin =
                if (!isLoopbackHost(call.request.origin.remoteHost) && requestHost.isNotBlank()) {
                    "http://$requestHost"
                } else {
                    "http://$LOOPBACK_HOST:${boundPort()}"
                }
            call.respondText(
                openSearchDescriptorForOrigin(origin),
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
            redirectToResults(call, params)
        }
        post("/scope") {
            if (!guardMutation(call, rankingPreferences)) return@post
            val params = call.receiveParameters()
            val lens = params["lens"].orEmpty().trim()
            rankingPreferences!!.setActiveLens(lens.ifEmpty { null })
            redirectToResults(call, params)
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
                    mediaActionsEnabled = userPreferences.mediaActionsEnabled(),
                    historyEnabled = userPreferences.preferences.first().historyEnabled,
                    updateCheckEnabled = userPreferences.updateCheckEnabled(),
                    personalizationEnabled = userPreferences.personalizationEnabled(),
                    language = userPreferences.language(),
                    engines =
                        userPreferences.preferences.first().let { up ->
                            engineCatalog.map {
                                EngineToggleView(it.id, it.displayName, it.requiresApiKey, up.isEngineEnabled(it.id))
                            }
                        },
                )
            val history = historyStore?.list(System.currentTimeMillis())?.take(HISTORY_VIEW_LIMIT)
            val saved = call.request.queryParameters["saved"] == "1"
            val text = servedText(call)
            call.respondHtml { renderSettingsPage(text, prefs, rules, history, historyStore != null, saved) }
        }
        post("/settings/prefs") {
            if (!guardPrefs(call, userPreferences)) return@post
            val params = call.receiveParameters()
            params["sort_mode"]?.trim()?.takeIf { it in VALID_SORTS }?.let { userPreferences!!.setSortMode(it) }
            params["ai_slop_mode"]?.trim()?.takeIf { it in VALID_SLOP }?.let { userPreferences!!.setAiSlopMode(it) }
            userPreferences!!.setSummaryEnabled(params["summary_enabled"].isFormOn())
            userPreferences.setUpstreamSuggestionsEnabled(params["upstream_suggestions_enabled"].isFormOn())
            userPreferences.setMediaActionsEnabled(params["media_actions_enabled"].isFormOn())
            userPreferences.setHistoryEnabled(params["history_enabled"].isFormOn())
            userPreferences.setUpdateCheckEnabled(params["update_check_enabled"].isFormOn())
            userPreferences.setPersonalizationEnabled(params["personalization_enabled"].isFormOn())
            // Per-engine enable toggles: an unchecked box is absent, so missing = disabled. Each write
            // is an atomic single-key update inside the store, so all toggles in one save stick.
            engineCatalog.forEach { engine ->
                userPreferences.setEngineEnabled(engine.id, params["engine_${engine.id}"].isFormOn())
            }
            // Language: "" means follow the device language; any other value must be a shipped locale.
            params["language"]?.trim()?.let { lang ->
                if (lang.isEmpty() || SupportedLocales.isSupported(lang)) userPreferences.setLanguage(lang)
            }
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
        // Owner-only personalization (learned click model) management, mirroring the in-app controls.
        post("/settings/personalization/reset") {
            if (!guardPrefs(call, userPreferences)) return@post
            personalizationPreferences?.reset()
            call.respondRedirect("/settings?saved=1", permanent = false)
        }
        get("/settings/personalization/export") {
            if (userPreferences == null || personalizationPreferences == null || !isOwnerRequest(call)) {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
                return@get
            }
            call.response.headers.append(
                "Content-Disposition",
                "attachment; filename=\"searchmob-personalization.json\"",
            )
            call.respondText(personalizationPreferences.exportJson(), ContentType.Application.Json)
        }
        post("/settings/personalization/import") {
            if (!guardPrefs(call, userPreferences)) return@post
            val text = call.receiveParameters()["model"].orEmpty().take(MAX_GOGGLE_CHARS)
            if (text.isNotBlank()) personalizationPreferences?.importJson(text)
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
    val mediaActionsEnabled: Boolean,
    val historyEnabled: Boolean,
    val updateCheckEnabled: Boolean,
    val personalizationEnabled: Boolean,
    // The owner's UI-language pref tag, or "" for follow-the-device-language.
    val language: String,
    val engines: List<EngineToggleView> = emptyList(),
)

/** One engine row on the served Settings page: its id, display label, key requirement, and state. */
private data class EngineToggleView(
    val id: String,
    val label: String,
    val needsKey: Boolean,
    val enabled: Boolean,
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

/**
 * Loopback names only: the served editing routes are for the machine's own browser, never the LAN.
 * Accepts `localhost`, the `127.0.0.0/8` range, and IPv6 loopback in any textual form: the compressed
 * `::1`, the expanded `0:0:0:0:0:0:0:1` (which our dual-stack `*` socket may report for a `::1`
 * client), the IPv4-mapped `::ffff:127.0.0.1` (likewise for a `127.x` client), and either wrapped in
 * `[...]` brackets or carrying a `%zone` suffix (as an `Origin`/Host value can be).
 */
internal fun isLoopbackHost(host: String): Boolean {
    var h = host.trim().lowercase()
    if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
    h = h.substringBefore('%')
    if (h == "localhost" || h == "::1" || h.startsWith("127.")) return true
    if (!h.contains(":")) return false
    // IPv4-mapped IPv6 loopback, e.g. `::ffff:127.0.0.1` (an embedded `127.x` address after the colons).
    val tail = h.substringAfterLast(':')
    if (tail.startsWith("127.") && tail.count { it == '.' } == 3) return true
    // Expanded / partially-collapsed pure IPv6 loopback: every hextet is zero except a trailing `1`.
    val parts = h.split(":")
    return parts.lastOrNull() == "1" && parts.dropLast(1).all { it.isEmpty() || it.all { c -> c == '0' } }
}

/** Query routes gated by the access token for non-loopback clients in network mode. */
private val GATED_PATHS = setOf("/search", "/api/search", "/suggest", "/img")

/**
 * Content-Security-Policy sent on every response. `kotlinx.html` HTML-escapes every piece of dynamic
 * content it renders (query text, titles, snippets, domains, settings values, ...), so the small
 * inline theme/reveal `<script>` and `<style>` blocks this server emits are always our own source
 * constants, never attacker- or user-controlled text - that is what makes `'unsafe-inline'` (required
 * because those blocks and a few inline `onclick`/`onchange` handlers have no external file to point a
 * nonce/hash-based policy at) safe to allow here. The policy's real job is defense-in-depth against
 * everything else: `default-src 'none'` plus the narrow allowances below block any EXTERNAL script,
 * style, object, or frame a future bug (ours or a library's) might try to load, `form-action 'self'`
 * stops a form from ever submitting to a foreign origin, and `frame-ancestors 'none'` blocks a foreign
 * page from framing us (belt-and-suspenders with `X-Frame-Options: DENY` for older browsers).
 */
private const val CSP =
    "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:; " +
        "connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"

/**
 * The access token presented by a caller for the network-mode gate, checked in priority order: the
 * `?token=` query parameter (kept because an OpenSearch URL template can only carry query parameters,
 * so a browser search-engine integration has no other way to send it), then an
 * `Authorization: Bearer <token>` header (case-insensitive scheme, tolerant of surrounding whitespace),
 * then a bare `X-SearchMob-Token` header. A header-carried token is preferable when the caller can
 * manage one: unlike a query parameter it never lands in browser history, a bookmarked URL, or a
 * `Referer` sent to some other site. Pure so the precedence is unit-testable without a running server.
 */
internal fun presentedToken(
    queryParam: String?,
    authorizationHeader: String?,
    tokenHeader: String?,
): String? {
    if (queryParam != null) return queryParam
    val header = authorizationHeader?.trim()
    if (header != null && header.length >= 6 && header.substring(0, 6).equals("Bearer", ignoreCase = true)) {
        val bearerToken = header.substring(6).trim()
        if (bearerToken.isNotEmpty()) return bearerToken
    }
    return tokenHeader
}

/**
 * Constant-time comparison of the [presented] token against the configured [expected] one, so a
 * network-mode attacker probing the token cannot use response-timing differences to recover it
 * byte-by-byte the way a naive `==` (which returns as soon as it finds a differing byte) would leak.
 * False whenever [expected] is null/empty (no token configured - the gate never opens on a fluke
 * empty-string match) or [presented] is null (nothing was offered).
 */
internal fun tokenMatches(
    presented: String?,
    expected: String?,
): Boolean {
    if (expected.isNullOrEmpty() || presented == null) return false
    return MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
}

/** Strip an optional `:port` and IPv6 brackets from a Host header value; returns a bare lowercase host. */
internal fun hostnameOnly(hostHeader: String): String {
    val value = hostHeader.trim().lowercase()
    if (value.isEmpty()) return ""
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        return if (end != -1) value.substring(1, end) else value.substring(1)
    }
    return if (value.count { it == ':' } == 1) value.substringBefore(":") else value
}

/** True when `name` looks like an IPv4 or IPv6 literal (not a DNS name). */
internal fun isIpLiteral(name: String): Boolean {
    if (name.contains(":")) return name.all { it.isDigit() || it in "abcdefABCDEF:." }
    val parts = name.split(".")
    return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
}

/**
 * DNS-rebind defense: accept a Host header that is loopback, an IP literal, or one of `allowedHosts`
 * (the device's own hostname(s)); reject a foreign DNS name. An empty Host is accepted (HTTP/1.0).
 * Mirrors the desktop `host_header_allowed` for the wildcard-bind (network-mode) case.
 */
internal fun hostHeaderAllowed(
    name: String,
    allowedHosts: Set<String>,
): Boolean {
    if (name.isEmpty()) return true
    if (isLoopbackHost(name)) return true
    if (name in allowedHosts) return true
    return isIpLiteral(name)
}

private fun isOwnerRequest(call: ApplicationCall): Boolean = isLoopbackHost(call.request.origin.remoteHost)

/**
 * CSRF guard: a cross-site POST carries an `Origin` header naming a foreign host, which we reject. An
 * absent Origin is same-origin - a non-browser caller (curl, our own tests, a legacy client) never
 * sends one on a same-origin POST, and there is nothing here for an attacker to forge in its place.
 *
 * A literal `Origin: null` is NOT trusted, unlike before. This used to be treated as same-origin on
 * the theory that our own `Referrer-Policy: no-referrer` made a browser serialize our OWN form posts'
 * origin as that opaque value - but an attacker page can produce the exact same `Origin: null` on a
 * genuinely cross-site POST, e.g. by setting `Referrer-Policy: no-referrer` on its own page, or by
 * posting from a `<iframe sandbox="allow-forms">` (whose origin is opaque by construction). Either way
 * the request reaches us indistinguishable from our own, which was a CSRF bypass: the attacker never
 * needs to know or send a real Origin at all. We now serve `Referrer-Policy: same-origin` instead (see
 * the header set in [searchModule]'s intercept), so OUR OWN same-origin form posts carry their real,
 * non-null origin; only an attacker still manufactures the opaque `null` value, so it is rejected here.
 * Anything else that fails to parse as a URI, or parses with no host at all, is rejected the same way.
 */
private fun sameOrigin(call: ApplicationCall): Boolean {
    val origin = call.request.headers["Origin"] ?: return true
    if (origin.trim().equals("null", ignoreCase = true)) return false
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

/**
 * Return to the results page a mutation POST came from, rebuilt from the hidden `q`/`sort`/`vertical`
 * fields the served scope/rule forms carry, so the new scope or rule is applied to the same search
 * instead of dumping the owner on the home page. Deliberately independent of the Referer header: even
 * though `Referrer-Policy: same-origin` now lets a same-origin POST carry one, a hardened browser
 * setting or extension can still strip it, so the Referer-based [redirectBack] is not reliable enough
 * for this, the primary path.
 * Falls back to [redirectBack] when there is no query - e.g. the home-page or settings scope selector,
 * which has no results page to return to.
 */
private suspend fun redirectToResults(
    call: ApplicationCall,
    params: Parameters,
) {
    val query = params["q"].orEmpty().trim()
    if (query.isEmpty()) {
        redirectBack(call)
        return
    }
    val sort = params["sort"].orEmpty().ifBlank { "fresh" }
    val vertical = params["vertical"].orEmpty().ifBlank { "web" }
    val target =
        "/search?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&vertical=${URLEncoder.encode(vertical, "UTF-8")}" +
            "&sort=${URLEncoder.encode(sort, "UTF-8")}"
    call.respondRedirect(target, permanent = false)
}

/** Return to the page the POST came from when it is one of our own origins; else home. */
private suspend fun redirectBack(call: ApplicationCall) {
    val target =
        call.request.headers["Referer"]
            ?.takeIf { runCatching { URI(it).host }.getOrNull()?.let(::isLoopbackHost) == true }
            ?: "/"
    call.respondRedirect(target, permanent = false)
}

/**
 * Localized chrome strings for the served pages. [res] is a per-request, locale-adjusted [Resources]
 * (or null in tests / when no app context is wired, where every string falls back to its English
 * default). [tag] drives the document `lang` attribute and [rtl] the `dir` attribute. Reuses the same
 * `R.string` resources the in-app UI uses, so a string is authored once for both surfaces.
 */
private class ServedText(
    val tag: String,
    val rtl: Boolean,
    private val res: Resources?,
) {
    private fun s(
        id: Int,
        en: String,
    ): String = res?.runCatching { getString(id) }?.getOrNull() ?: en

    private fun s(
        id: Int,
        en: String,
        vararg args: Any,
    ): String = res?.runCatching { getString(id, *args) }?.getOrNull() ?: en.format(*args)

    val searchHint get() = s(R.string.search_hint, "Search the web")
    val searchButton get() = s(R.string.search_submit, "Search")
    val settings get() = s(R.string.settings_title, "Settings")
    val sortLabel get() = s(R.string.search_sort_label, "Sort:")
    val apply get() = s(R.string.search_apply, "Apply")
    val didYouMean get() = s(R.string.search_did_you_mean, "Did you mean:")
    val fromWikipedia get() = s(R.string.search_summary_source, "From Wikipedia")
    val enterQuery get() = s(R.string.search_idle, "Enter a query to search.")
    val noResults get() = s(R.string.search_empty, "No results found.")
    val clearScope get() = s(R.string.search_clear_scope, "Clear scope")

    fun noResultsForScope(
        scope: String,
        query: String,
    ) = s(R.string.search_no_results_scope, "No results match the %1\$s scope for %2\$s.", "“$scope”", "“$query”")

    val tagline get() = s(R.string.home_tagline, "Private, battery-friendly, on-device search.")
    val updateAction get() = s(R.string.update_banner_action, "Update")

    fun resultsFor(query: String) = s(R.string.search_results_for, "Results for %1\$s", "“$query”")

    fun showingResultsFor(query: String) = s(R.string.search_showing_results_for, "Showing results for") + " “$query”"

    fun updateAvailable(version: String) = s(R.string.update_banner_available, "SearchMob %1\$s is available.", version)

    val verticals
        get() =
            listOf(
                "web" to s(R.string.vertical_web, "Web"),
                "news" to s(R.string.vertical_news, "News"),
                "forums" to s(R.string.vertical_forums, "Forums"),
                "academic" to s(R.string.vertical_academic, "Academic"),
            )

    val sortOptions
        get() =
            listOf(
                "fresh" to s(R.string.sort_fresh, "Freshest + Relevant"),
                "date" to s(R.string.sort_date, "Date"),
                "relevance" to s(R.string.sort_relevance, "Relevance"),
            )

    val engineNoResults get() = s(R.string.engine_status_no_results, "no results")
    val engineFailed get() = s(R.string.engine_status_failed, "failed")

    fun enginesResponded(
        responded: Int,
        total: Int,
    ) = s(R.string.search_engines_responded, "%1\$d of %2\$d engines responded", responded, total)

    fun engineResultCount(count: Int) = s(R.string.search_engine_result_count, "%1\$d results", count)

    fun mediaLabel(category: MediaCategory) =
        when (category) {
            MediaCategory.MUSIC -> s(R.string.media_listen_on, "Listen on")
            MediaCategory.FILM_TV -> s(R.string.media_watch_on, "Watch on")
            MediaCategory.BOOKS -> s(R.string.media_read_on, "Read on")
            MediaCategory.GAMES -> s(R.string.media_play_on, "Play on")
        }
}

private fun HTML.renderResultsPage(
    text: ServedText,
    query: String,
    outcome: SearchOutcome,
    rules: RankingRules = RankingRules.EMPTY,
    editable: Boolean = false,
    sortMode: String = "fresh",
    vertical: String = "web",
    settingsLink: Boolean = false,
    // When set (owner + personalization on), maps a result's (position, url) to the `/click` link
    // that trains the model; null means plain destination links (network visitors, disabled owner).
    linkBuilder: ((Int, String) -> String)? = null,
    // Owner-only "update available" banner (version, releaseUrl), or null.
    updateBanner: Pair<String, String>? = null,
    // On-device instant answer (calculator / unit / base conversion), or null for most queries.
    instantAnswer: InstantAnswer? = null,
    // Wall time the search took, for the meta line; null hides the timing (blank query).
    tookMs: Long? = null,
    // Whether the `/img` proxy is wired; when false the summary card renders without a thumbnail
    // rather than ever pointing the browser at a third-party image host.
    proxyThumbnails: Boolean = false,
    // The sort the user explicitly chose via `?sort=` (already validated), or null when the page is
    // on the saved/vertical default. Carried across the vertical tabs only when explicit.
    explicitSort: String? = null,
) {
    attributes["lang"] = text.tag
    if (text.rtl) attributes["dir"] = "rtl"
    val results = outcome.results
    head { pageHead(if (query.isBlank()) "SearchMob" else "$query · SearchMob") }
    body {
        attributes["data-page"] = "results"
        updateBanner(text, updateBanner)
        div("topbar") {
            a(href = "/", classes = "logo") { +"SearchMob" }
            form(action = "/search", method = FormMethod.get, classes = "searchbox") {
                textInput(name = "q") {
                    value = query
                    placeholder = text.searchHint
                    attributes["aria-label"] = text.searchButton
                    attributes["autocomplete"] = "off"
                    attributes["spellcheck"] = "false"
                }
                // A new query typed here stays in the current category (News/Forums/...), matching
                // the tabbed behavior every commercial engine has; Web omits it as the default.
                if (vertical != "web") hiddenInput(name = "vertical") { value = vertical }
                submitInput { value = text.searchButton }
            }
            settingsLink(text, settingsLink)
            themeToggle()
        }
        div("results") {
            // Category tabs render whenever there is a query, so the user can switch verticals even
            // from a vertical that returned nothing.
            if (query.isNotBlank()) verticalBar(text, query, vertical, explicitSort)
            if (query.isNotBlank()) instantAnswer?.let { instantAnswerCard(it) }
            if (query.isNotBlank()) outcome.summary?.let { summaryBox(text, it, proxyThumbnails) }
            if (query.isNotBlank()) outcome.actionsRow?.let { actionsRowCard(text, it) }
            when {
                query.isBlank() -> p("empty") { +text.enterQuery }
                results.isEmpty() -> {
                    outcome.didYouMean?.let { didYouMeanLine(text, it, sortMode, vertical) }
                    // The diagnostic that matters MOST when a page comes back empty: whether the
                    // engines failed (network trouble) or genuinely found nothing. Owner-only.
                    if (editable) engineStatusLine(text, outcome.engineStatus)
                    val activeLens = rules.activeLens
                    if (editable && activeLens != null) {
                        // An active scope filtered every result out. The scope bar only rendered when
                        // there WERE results, so the owner could neither see nor clear the scope that
                        // hid them and the page looked like a blank fresh search. Show both here.
                        scopeBar(rules, query, sortMode, vertical)
                        p("empty") { +text.noResultsForScope(activeLens, query) }
                        clearScope(text, query, sortMode, vertical)
                    } else {
                        p("empty") { +text.noResults }
                    }
                }
                else -> {
                    val heading =
                        if (outcome.showingResultsFor != null) {
                            text.showingResultsFor(outcome.showingResultsFor)
                        } else {
                            text.resultsFor(query)
                        }
                    p("meta") { +metaLine(heading, text, results.size, tookMs) }
                    outcome.didYouMean?.let { didYouMeanLine(text, it, sortMode, vertical) }
                    // Per-engine status is diagnostic and owner-only (`editable` is the loopback-owner
                    // gate the editing controls use); never shown to a LAN visitor.
                    if (editable) engineStatusLine(text, outcome.engineStatus)
                    sortBar(text, query, sortMode, vertical)
                    if (editable) scopeBar(rules, query, sortMode, vertical)
                    results.forEachIndexed { index, result ->
                        // Results past the first reveal window start collapsed; the reveal script
                        // unhides them in batches on scroll. The full list is still in the DOM, so
                        // click positions (and the owner's /click links) stay aligned with the order.
                        div(if (index >= REVEAL_SIZE) "result is-collapsed" else "result") {
                            div("url") { +displayUrl(result.url) }
                            // Only emit a clickable link for http(s) URLs. A javascript:/data:/file: URL
                            // would survive HTML escaping in href and could execute in the loopback origin,
                            // so render its title as plain text instead.
                            if (isSafeHttpUrl(result.url)) {
                                // rel=noreferrer backs up the Referrer-Policy header so the query (in
                                // the loopback URL) never leaks; noopener severs window.opener. The href
                                // is the `/click` redirector for the owner (so a click trains the model),
                                // or the plain destination otherwise.
                                a(href = linkBuilder?.invoke(index, result.url) ?: result.url, classes = "title") {
                                    attributes["rel"] = "noopener noreferrer"
                                    +result.title
                                }
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
                            if (editable) rankControls(result.url, rules, query, sortMode, vertical)
                        }
                    }
                    if (results.size > REVEAL_SIZE) {
                        div("reveal-sentinel") { attributes["aria-hidden"] = "true" }
                    }
                }
            }
        }
        // Infinite scroll: when the pool is larger than the first window, this unhides the next
        // batch as the sentinel scrolls into view. No new request, nothing stored, JS-off shows all.
        if (results.size > REVEAL_SIZE) script { unsafe { +REVEAL_JS } }
        script { unsafe { +THEME_TOGGLE_JS } }
        script { unsafe { +SUGGEST_JS } }
        script { unsafe { +SHORTCUT_JS } }
    }
}

/**
 * The results-page meta line: the localized "Results for X" heading plus the merged result count and
 * elapsed seconds ("Results for “x” · 12 results · 0.8 s"), the at-a-glance breadth/speed feedback
 * every commercial engine shows. Locale.ROOT keeps the decimal point stable in any UI language.
 */
private fun metaLine(
    heading: String,
    text: ServedText,
    count: Int,
    tookMs: Long?,
): String =
    buildString {
        append(heading)
        append(" · ").append(text.engineResultCount(count))
        if (tookMs != null) append(" · ").append(String.format(Locale.ROOT, "%.1f s", tookMs / 1000.0))
    }

/**
 * The on-device instant answer (calculator / unit / base conversion / percentage): the computed
 * result large, the normalized input above it, styled like a knowledge card. Pure local computation;
 * rendering it never adds a request anywhere.
 */
private fun FlowContent.instantAnswerCard(answer: InstantAnswer) {
    div("instant") {
        p("iexpr") { +answer.expression }
        p("ival") { +answer.result }
    }
}

/**
 * Owner-only "N of M engines responded" disclosure with per-engine detail. A native `<details>`
 * element so it is keyboard-accessible with no JavaScript and not color-only. Renders nothing when
 * no status is supplied. The route gates this on the loopback owner; never shown to a LAN visitor.
 */
private fun FlowContent.engineStatusLine(
    text: ServedText,
    engineStatus: List<EngineOutcome>,
) {
    if (engineStatus.isEmpty()) return
    val responded = engineStatus.count { it.status != EngineStatus.FAILED }
    details(classes = "engine-status meta") {
        summary { +text.enginesResponded(responded, engineStatus.size) }
        ul {
            engineStatus.forEach { outcome ->
                val detail =
                    when (outcome.status) {
                        EngineStatus.CONTRIBUTED -> text.engineResultCount(outcome.count)
                        EngineStatus.EMPTY -> text.engineNoResults
                        EngineStatus.FAILED -> text.engineFailed
                    }
                li("engine engine-${outcome.status.name.lowercase()}") { +"${outcome.name} — $detail" }
            }
        }
    }
}

/**
 * The "Listen/Watch/Read/Play on" actions row for a resolved media entity: the localized verb plus
 * brand links (free/open first, Wikipedia leading). Every link is a locally-built search URL and
 * carries `rel=noopener noreferrer` like all result links.
 */
private fun FlowContent.actionsRowCard(
    text: ServedText,
    row: ActionsRow,
) {
    div(classes = "actions-row") {
        span("alabel") { +text.mediaLabel(row.category) }
        row.links.forEach { link ->
            a(href = link.url, classes = "achip") {
                attributes["rel"] = "noopener noreferrer"
                +link.label
            }
        }
    }
}

/**
 * A "Did you mean: <correction>" line linking to a fresh search for the correction. The link keeps
 * the current sort and vertical so accepting a correction does not silently reset the user's view.
 */
private fun FlowContent.didYouMeanLine(
    text: ServedText,
    correction: String,
    sortMode: String,
    vertical: String,
) {
    p("didyoumean") {
        +"${text.didYouMean} "
        val href =
            "/search?q=${URLEncoder.encode(correction, "UTF-8")}" +
                "&vertical=${URLEncoder.encode(vertical, "UTF-8")}&sort=${URLEncoder.encode(sortMode, "UTF-8")}"
        a(href = href) { +correction }
    }
}

/**
 * A knowledge-panel-style Wikipedia summary card shown above the results. The thumbnail is served
 * through the loopback `/img` proxy (never a direct third-party fetch from the browser); when the
 * proxy is not wired the card renders text-only.
 */
private fun FlowContent.summaryBox(
    text: ServedText,
    summary: WikiSummary,
    proxyThumbnails: Boolean = false,
) {
    div("summary") {
        if (proxyThumbnails && summary.thumbnailUrl != null && ThumbnailProxy.isAllowed(summary.thumbnailUrl)) {
            img(src = "/img?u=${URLEncoder.encode(summary.thumbnailUrl, "UTF-8")}", alt = "") {
                attributes["loading"] = "lazy"
            }
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
            p("ssource meta") { +text.fromWikipedia }
        }
    }
}

/**
 * Result sort selector. GET so the choice is bookmarkable; carries the query AND the current vertical
 * in hidden fields - without the vertical, changing the sort on the News tab silently dropped the
 * user back to the Web results.
 */
private fun FlowContent.sortBar(
    text: ServedText,
    query: String,
    sortMode: String,
    vertical: String,
) {
    form(action = "/search", method = FormMethod.get, classes = "scopebar") {
        hiddenInput(name = "q") { value = query }
        if (vertical != "web") hiddenInput(name = "vertical") { value = vertical }
        label {
            attributes["for"] = "sm-sort"
            +text.sortLabel
        }
        select {
            attributes["id"] = "sm-sort"
            attributes["name"] = "sort"
            attributes["onchange"] = "this.form.submit()"
            text.sortOptions.forEach { (value, lbl) ->
                option {
                    attributes["value"] = value
                    if (value == sortMode) attributes["selected"] = "selected"
                    +lbl
                }
            }
        }
        button(type = ButtonType.submit) { +text.apply }
    }
}

/**
 * Category tabs (Web / News / Forums / Academic) as GET links carrying the current query. A sort the
 * user explicitly chose ([explicitSort] non-null) is carried across tabs so it is not silently reset;
 * otherwise each vertical keeps its own sensible default sort.
 */
private fun FlowContent.verticalBar(
    text: ServedText,
    query: String,
    vertical: String,
    explicitSort: String?,
) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val sortSuffix = explicitSort?.let { "&sort=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
    nav("verticalbar") {
        attributes["aria-label"] = "Search categories"
        text.verticals.forEach { (value, lbl) ->
            val isActive = value == vertical
            val classes = if (isActive) "chip active" else "chip"
            a(href = "/search?q=$encoded&vertical=$value$sortSuffix", classes = classes) {
                // aria-current marks the active category for assistive tech (not by color alone).
                if (isActive) attributes["aria-current"] = "page"
                +lbl
            }
        }
    }
}

/**
 * Drop a trailing parenthetical from a lens name for the compact nested home-page scope selector.
 * "Less clutter (no Pinterest/Quora)" -> "Less clutter"; the full name stays in a hover title.
 */
private fun shortLensLabel(name: String): String {
    val idx = name.indexOf(" (")
    val short = if (idx > 0) name.substring(0, idx).trimEnd() else name
    return short.ifEmpty { name }
}

/**
 * The home-page scope (lens) select, nested inside the search box just left of the Search button. It
 * submits to the hidden `/scope` form via the HTML `form=` attribute. The visible label drops any
 * trailing parenthetical to stay compact; the full lens name is the option value and a hover title.
 * Mirrors the desktop `_home_scope`.
 */
private fun FlowContent.homeScopeSelect(rules: RankingRules) {
    val activeFull = rules.lenses.firstOrNull { it.name == rules.activeLens }?.name
    select {
        attributes["id"] = "sm-scope"
        attributes["name"] = "lens"
        attributes["form"] = "sm-scope-form"
        attributes["aria-label"] = "Search scope"
        if (activeFull != null) attributes["title"] = activeFull
        attributes["onchange"] = "this.form.submit()"
        option {
            attributes["value"] = ""
            +"No scope"
        }
        rules.lenses.forEach { lens ->
            option {
                attributes["value"] = lens.name
                attributes["title"] = lens.name
                if (lens.name == rules.activeLens) attributes["selected"] = "selected"
                +shortLensLabel(lens.name)
            }
        }
    }
}

/** Scope (lens) selector; rendered only when the profile has at least one lens defined. */
private fun FlowContent.scopeBar(
    rules: RankingRules,
    query: String,
    sortMode: String,
    vertical: String,
) {
    if (rules.lenses.isEmpty()) return
    form(action = "/scope", method = FormMethod.post, classes = "scopebar") {
        // Carry the current search so the POST can land back on these results with the new scope
        // applied, instead of the home page (kept independent of the Referer header - see
        // redirectToResults).
        hiddenInput(name = "q") { value = query }
        hiddenInput(name = "sort") { value = sortMode }
        hiddenInput(name = "vertical") { value = vertical }
        label {
            attributes["for"] = "sm-scope"
            +"Scope:"
        }
        select {
            attributes["id"] = "sm-scope"
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

/**
 * A one-click "Clear scope" control: POST /scope with an empty lens, carrying the search context.
 *
 * Clearing the active lens and redirecting back to the same query re-runs it unfiltered, so the
 * owner recovers the results an over-filtering scope hid without retyping anything.
 */
private fun FlowContent.clearScope(
    text: ServedText,
    query: String,
    sortMode: String,
    vertical: String,
) {
    form(action = "/scope", method = FormMethod.post, classes = "clearscope") {
        hiddenInput(name = "q") { value = query }
        hiddenInput(name = "sort") { value = sortMode }
        hiddenInput(name = "vertical") { value = vertical }
        hiddenInput(name = "lens") { value = "" }
        button(type = ButtonType.submit) { +text.clearScope }
    }
}

/** Per-result domain controls (block / lower / raise / pin / reset) as a single POST form. */
private fun FlowContent.rankControls(
    url: String,
    rules: RankingRules,
    query: String,
    sortMode: String,
    vertical: String,
) {
    val domain = DomainRanker.host(url) ?: return
    val current = rules.domainRules[domain]
    form(action = "/rules/domain", method = FormMethod.post, classes = "rank") {
        // Carry the current search so applying the rule returns to these results, not the home page
        // (kept independent of the Referer header, which not every client sends - see redirectToResults).
        hiddenInput(name = "q") { value = query }
        hiddenInput(name = "sort") { value = sortMode }
        hiddenInput(name = "vertical") { value = vertical }
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
private fun HTML.renderHomePage(
    text: ServedText,
    settingsLink: Boolean = false,
    rules: RankingRules? = null,
    editable: Boolean = false,
    updateBanner: Pair<String, String>? = null,
) {
    attributes["lang"] = text.tag
    if (text.rtl) attributes["dir"] = "rtl"
    head { pageHead("SearchMob") }
    body {
        attributes["data-page"] = "home"
        updateBanner(text, updateBanner)
        div("topbar") {
            span("logo") { +"SearchMob" }
            settingsLink(text, settingsLink)
            themeToggle()
        }
        // The scope (lens) selector nests inside the search box for the owner; it belongs to a
        // separate hidden /scope form via the HTML `form=` attribute, so changing it persists exactly
        // as the standalone scope bar does without nesting two forms. Empty when no lens is defined.
        val hasScope = editable && rules != null && rules.lenses.isNotEmpty()
        div("home") {
            div("brand") { +"SearchMob" }
            p("tagline") { +text.tagline }
            form(action = "/search", method = FormMethod.get, classes = "searchbox") {
                textInput(name = "q") {
                    placeholder = text.searchHint
                    attributes["aria-label"] = text.searchButton
                    attributes["autocomplete"] = "off"
                    attributes["autofocus"] = "autofocus"
                }
                if (hasScope) homeScopeSelect(rules)
                submitInput { value = text.searchButton }
            }
            if (hasScope) {
                form(action = "/scope", method = FormMethod.post) {
                    attributes["id"] = "sm-scope-form"
                    attributes["hidden"] = "hidden"
                }
            }
            searchOperatorsHelp()
        }
        script { unsafe { +THEME_TOGGLE_JS } }
        script { unsafe { +SUGGEST_JS } }
        script { unsafe { +SHORTCUT_JS } }
    }
}

/**
 * One (operator example, short description) row for [searchOperatorsHelp], in the order shown. Mirrors
 * the Google-style query operators the search engine layer understands: `"exact phrase"`, `-term`,
 * `site:`/`-site:`, `intitle:`, `inurl:`, `filetype:`/`ext:`, `before:`/`after:` (YYYY[-MM[-DD]]), and
 * `OR`/`|`.
 */
private val OPERATOR_HELP =
    listOf(
        "\"exact phrase\"" to "match this exact phrase",
        "-term" to "exclude results containing term",
        "site:example.com" to "only results from this site",
        "-site:example.com" to "exclude results from this site",
        "intitle:word" to "word must appear in the title",
        "inurl:word" to "word must appear in the URL",
        "filetype:pdf" to "only this file type (also ext:)",
        "before:2023-01-31" to "published before this date",
        "after:2022" to "published on or after this date (year, year-month, or full date)",
        "a OR b" to "match either term (also a | b)",
        "!w query" to "jump to a site's own search (!w Wikipedia, !gh GitHub, !yt YouTube, ...)",
        "2+2, 10 km to mi" to "instant answers: calculator, unit and number-base conversion",
    )

/**
 * Collapsed "Search operators" help card under the home search box, listing the operators above with a
 * short example each. A native `<details>` needs no JavaScript to expand/collapse and stays reachable
 * and operable from the keyboard. Starts collapsed so it never competes with the search box for
 * attention. Hardcoded English, matching the existing "Scope:"/"Apply" precedent elsewhere on the
 * served pages.
 */
private fun FlowContent.searchOperatorsHelp() {
    details(classes = "ophelp") {
        summary { +"Search operators" }
        OPERATOR_HELP.forEach { (op, description) ->
            div("oprow") {
                span("code") { +op }
                +" $description"
            }
        }
    }
}

/** A Settings-page link for the loopback owner (the route itself is owner-only). */
private fun FlowContent.settingsLink(
    text: ServedText,
    show: Boolean,
) {
    if (show) a(href = "/settings", classes = "settings-link") { +text.settings }
}

/**
 * Owner-only "update available" banner pinned above the top bar. [banner] is (version, releaseUrl);
 * null renders nothing. The link opens the release page (the in-app updater offers the one-click
 * install). The kotlinx.html builder escapes the version and href, so no manual escaping is needed.
 */
private fun FlowContent.updateBanner(
    text: ServedText,
    banner: Pair<String, String>?,
) {
    if (banner == null) return
    val (version, url) = banner
    div("updatebar") {
        attributes["role"] = "status"
        span("msg") { +text.updateAvailable(version) }
        a(href = url, classes = "btn") {
            attributes["rel"] = "noopener noreferrer"
            +text.updateAction
        }
    }
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
    text: ServedText,
    prefs: SettingsView,
    rules: RankingRules,
    history: List<HistoryEntry>?,
    historyClearable: Boolean,
    saved: Boolean,
) {
    attributes["lang"] = text.tag
    if (text.rtl) attributes["dir"] = "rtl"
    head { pageHead("${text.settings} · SearchMob") }
    body {
        attributes["data-page"] = "settings"
        div("topbar") {
            a(href = "/", classes = "logo") { +"SearchMob" }
            span("spacer") {}
            themeToggle()
        }
        div("settings") {
            h1 { +text.settings }
            if (saved) p("saved") { +"Saved." }

            // The Appearance card is client-side only (localStorage); it sits above the prefs form and
            // nothing here posts to the server. The mode select, the two theme-slot selects, and the
            // A-/A+ stepper are wired by THEME_CONTROLS_JS at the bottom of the page.
            appearanceCard()

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
                    checkRow(
                        "media_actions_enabled",
                        "Show quick links for films, music, books, and games",
                        prefs.mediaActionsEnabled,
                    )
                    div("field") {
                        label { +"Language" }
                        selectField(
                            "language",
                            listOf("" to "Follow device language") +
                                SupportedLocales.SUPPORTED.map { it.tag to it.nativeName },
                            prefs.language,
                        )
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
                section("card") {
                    h2 { +"Privacy & updates" }
                    checkRow("history_enabled", "Save my search history (on-device, encrypted)", prefs.historyEnabled)
                    checkRow(
                        "update_check_enabled",
                        "Check for SearchMob updates (about once a day, via the privacy proxy)",
                        prefs.updateCheckEnabled,
                    )
                    checkRow(
                        "personalization_enabled",
                        "Personalize ranking from my clicks (learned on-device, never shared)",
                        prefs.personalizationEnabled,
                    )
                }
                if (prefs.engines.isNotEmpty()) {
                    section("card") {
                        h2 { +"Search engines" }
                        prefs.engines.forEach { engine ->
                            val note = if (engine.needsKey) " (needs an API key, set in the app)" else ""
                            checkRow("engine_${engine.id}", engine.label + note, engine.enabled)
                        }
                    }
                }
                div("actions") { button(type = ButtonType.submit) { +"Save" } }
            }

            domainRulesCard(rules)
            scopesCard(rules)
            gogglesCard(rules)
            personalizationCard()
            if (history != null) historyCard(history, historyClearable)
        }
        script { unsafe { +THEME_TOGGLE_JS } }
        script { unsafe { +THEME_CONTROLS_JS } }
        script { unsafe { +GOGGLE_FILE_JS } }
    }
}

/**
 * The Appearance card: the mode select, the two theme-slot selects (filled from the registry,
 * partitioned by mode), and an A-/A+ text-size stepper. Browser-local; THEME_CONTROLS_JS wires it.
 */
private fun FlowContent.appearanceCard() {
    section("card") {
        h2 { +"Appearance" }
        div("field") {
            label {
                attributes["for"] = "sm-mode"
                +"Mode"
            }
            select {
                attributes["id"] = "sm-mode"
                listOf("light" to "Light", "dark" to "Dark", "system" to "Follow system").forEach { (v, lbl) ->
                    option {
                        attributes["value"] = v
                        +lbl
                    }
                }
            }
        }
        themeSlotField("sm-light-theme", "Light theme", ThemePaletteMode.LIGHT)
        themeSlotField("sm-dark-theme", "Dark theme", ThemePaletteMode.DARK)
        div("field") {
            label { +"Text size" }
            div("sizerow") {
                button(type = ButtonType.button) {
                    attributes["id"] = "sm-font-dec"
                    attributes["aria-label"] = "Smaller text"
                    +"A-"
                }
                span("sizeval") { attributes["id"] = "sm-font-val" }
                button(type = ButtonType.button) {
                    attributes["id"] = "sm-font-inc"
                    attributes["aria-label"] = "Larger text"
                    +"A+"
                }
            }
        }
    }
}

/** One theme-slot select (light or dark), listing only that mode's themes from the registry. */
private fun FlowContent.themeSlotField(
    selectId: String,
    labelText: String,
    mode: ThemePaletteMode,
) {
    div("field") {
        label {
            attributes["for"] = selectId
            +labelText
        }
        select {
            attributes["id"] = selectId
            APP_THEMES.filter { it.mode == mode }.forEach { theme ->
                option {
                    attributes["value"] = theme.id
                    +theme.displayName
                }
            }
        }
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

/** Owner-only personalization (learned click model): export, reset, and import the model JSON. */
private fun FlowContent.personalizationCard() {
    section("card") {
        h2 { +"Result personalization" }
        p("hint") {
            +"The ranking model SearchMob learns from your clicks. It is stored encrypted on this "
            +"device and never leaves it; the format is shared with SearchMob Desktop, so you can move "
            +"it between devices. The on/off switch is above, under Privacy & updates."
        }
        div("grow") {
            a(href = "/settings/personalization/export", classes = "btn") {
                attributes["download"] = "searchmob-personalization.json"
                +"Export model"
            }
            form(action = "/settings/personalization/reset", method = FormMethod.post, classes = "personalreset") {
                button(type = ButtonType.submit) { +"Reset model" }
            }
        }
        form(action = "/settings/personalization/import", method = FormMethod.post, classes = "personalimport") {
            textArea {
                attributes["name"] = "model"
                attributes["rows"] = "4"
                attributes["placeholder"] = "Paste an exported personalization JSON to import"
            }
            div("grow") { button(type = ButtonType.submit) { +"Import (replace)" } }
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

/** Shared <head>: meta, title, icon, OpenSearch link, styles, and the pre-paint theme restore. */
private fun HEAD.pageHead(titleText: String) {
    meta(charset = "utf-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    title { +titleText }
    link(rel = "icon", href = FAVICON_DATA_URI) { attributes["type"] = "image/svg+xml" }
    openSearchLink()
    style {
        unsafe {
            +THEME_CSS
            +PAGE_CSS
        }
    }
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
fun openSearchDescriptor(port: Int): String = openSearchDescriptorForOrigin("http://$LOOPBACK_HOST:$port")

/** [openSearchDescriptor] against an explicit origin (a network-mode visitor's route to this server). */
fun openSearchDescriptorForOrigin(origin: String): String {
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

// Blend a "#rrggbb" toward white by `t` (0..1), for the chip hover tint of a theme's surface. Matches
// the desktop `_from_roles` card-hover derivation closely enough for the chip background.
private fun mixToWhite(
    hex: String,
    t: Double,
): String {
    val h = hex.trim().removePrefix("#")
    val r = h.substring(0, 2).toInt(16)
    val g = h.substring(2, 4).toInt(16)
    val b = h.substring(4, 6).toInt(16)

    fun blend(c: Int) = (c + (255 - c) * t).toInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(blend(r), blend(g), blend(b))
}

/**
 * The `--bg`/`--fg`/... CSS custom properties for one theme, derived from its six roles exactly like
 * the desktop `_theme_vars`: `--card` is the surface, `--chip-bg` a hover tint of the surface, link /
 * accent / url all the accent, snippet the muted role, the shadow tracks the mode, and `--topbar` is
 * the background with an alpha byte so the sticky bar reads through its blur.
 */
fun themeVars(theme: AppTheme): String {
    val shadow =
        if (theme.mode == ThemePaletteMode.LIGHT) "0 1px 6px rgba(32,33,36,.12)" else "0 1px 6px rgba(0,0,0,.5)"
    val chipBg = mixToWhite(theme.surface, 0.07)
    return "--bg:${theme.background};--fg:${theme.text};--muted:${theme.muted};--border:${theme.border};" +
        "--card:${theme.surface};--link:${theme.accent};--url:${theme.accent};--snippet:${theme.muted};" +
        "--chip-bg:$chipBg;--chip-fg:${theme.muted};--accent:${theme.accent};--shadow:$shadow;" +
        "--topbar:${theme.background}ee;"
}

// The generated theme blocks: `:root` (default light), the prefers-color-scheme dark query, and one
// `[data-theme="<id>"]` override per theme so the JS picker is authoritative. Built once from the
// constant registry. Prepended to PAGE_CSS at render time.
private val THEME_CSS: String =
    buildString {
        append(":root{").append(themeVars(APP_THEMES.first { it.id == DEFAULT_LIGHT_ID })).append("}")
        append("@media (prefers-color-scheme:dark){:root{")
            .append(themeVars(APP_THEMES.first { it.id == DEFAULT_DARK_ID })).append("}}")
        APP_THEMES.forEach { theme ->
            append("[data-theme=\"${theme.id}\"]{").append(themeVars(theme)).append("}")
        }
    }

// Self-contained stylesheet (no external fonts/CDNs). Theme via CSS variables (the per-theme blocks
// in THEME_CSS are prepended at render time): light by default, dark via prefers-color-scheme, and an
// explicit [data-theme] override (set by the picker/toggle) wins. The root font size is in points and
// content sizes are in rem, so the served-page font-size preference (sm-font) scales everything.
@Suppress("ktlint:standard:max-line-length")
private val PAGE_CSS =
    """
    *{box-sizing:border-box}
    html,body{margin:0;padding:0}
    html{font-size:${DEFAULT_FONT_POINT_SIZE}pt}
    body{background:var(--bg);color:var(--fg);line-height:1.55;font-size:1rem;
      font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;}
    a{color:var(--link);text-decoration:none}
    a:hover{text-decoration:underline}
    .topbar{display:flex;align-items:center;gap:14px;padding:10px 18px;border-bottom:1px solid var(--border);
      position:sticky;top:0;background:var(--topbar);backdrop-filter:saturate(1.4) blur(8px);z-index:10}
    .topbar .logo{font-weight:800;font-size:20px;color:var(--accent);letter-spacing:-.5px;white-space:nowrap}
    .updatebar{display:flex;align-items:center;gap:12px;padding:9px 18px;background:var(--accent);
      color:#fff;font-size:13px}
    .updatebar .msg{font-weight:600}
    .updatebar .btn{margin-left:auto;background:#fff;color:var(--accent);border-radius:20px;padding:6px 16px;
      font-weight:700;text-decoration:none;white-space:nowrap;transition:box-shadow 150ms,filter 150ms}
    .updatebar .btn:hover{text-decoration:none;filter:brightness(.96);box-shadow:0 1px 3px rgba(0,0,0,.25)}
    .theme-toggle{margin-left:auto;background:transparent;border:1px solid var(--border);color:var(--fg);
      border-radius:20px;padding:6px 14px;cursor:pointer;font-size:13px;white-space:nowrap;
      transition:background-color 150ms,border-color 150ms,color 150ms}
    .theme-toggle:hover{border-color:var(--accent);color:var(--accent);background-color:rgba(127,127,127,.08)}
    .theme-toggle:hover{background-color:color-mix(in srgb, var(--accent) 8%, transparent)}
    .searchbox{display:flex;align-items:stretch;background:var(--card);border:1px solid var(--border);
      border-radius:28px;box-shadow:var(--shadow);transition:box-shadow 150ms,border-color 150ms;
      position:relative}
    .searchbox>input[type=text]{border-radius:28px 0 0 28px}
    .searchbox>input[type=submit]{border-radius:0 28px 28px 0}
    .suggest{position:absolute;top:calc(100% + 6px);left:0;right:0;margin:0;padding:6px 0;list-style:none;
      background:var(--card);border:1px solid var(--border);border-radius:16px;box-shadow:var(--shadow);
      z-index:20;max-height:60vh;overflow-y:auto;text-align:left}
    .suggest li{padding:8px 18px;cursor:pointer;font-size:.9375rem;color:var(--fg)}
    .suggest li.on,.suggest li:hover{background:color-mix(in srgb, var(--accent) 10%, transparent)}
    .instant{border:1px solid var(--border);border-radius:16px;background:var(--card);box-shadow:var(--shadow);
      padding:16px 20px;margin:0 0 22px}
    .instant .iexpr{margin:0;color:var(--muted);font-size:.8125rem}
    .instant .ival{margin:4px 0 0;font-size:1.75rem;font-weight:600;overflow-wrap:anywhere}
    .searchbox:hover{box-shadow:0 1px 3px rgba(0,0,0,.15),0 4px 8px rgba(0,0,0,.1)}
    .searchbox:focus-within{border-color:var(--accent);box-shadow:0 2px 6px rgba(0,0,0,.18),0 6px 14px rgba(0,0,0,.12)}
    .searchbox input[type=text]{flex:1;min-width:0;border:0;outline:0;background:transparent;color:var(--fg);
      font-size:1rem;padding:13px 18px}
    .searchbox input[type=submit]{border:0;background:var(--accent);color:#fff;padding:0 22px;cursor:pointer;
      font-size:.9375rem;font-weight:600;transition:filter 150ms}
    .searchbox input[type=submit]:hover{filter:brightness(1.07)}
    .searchbox select{border:0;border-left:1px solid var(--border);background:transparent;color:var(--fg);
      font-size:.875rem;padding:0 12px;outline:0;max-width:190px;cursor:pointer;transition:background-color 150ms}
    .searchbox select:hover{background-color:rgba(127,127,127,.06)}
    .searchbox select:hover{background-color:color-mix(in srgb, var(--fg) 6%, transparent)}
    .home{max-width:600px;margin:0 auto;padding:13vh 20px 0;text-align:center}
    .home .brand{font-size:3rem;font-weight:800;color:var(--accent);letter-spacing:-1.5px}
    .home .tagline{color:var(--muted);margin:8px 0 28px;font-size:.9375rem}
    .home .searchbox{max-width:560px;margin:0 auto;text-align:left}
    .topbar .searchbox{flex:1;max-width:620px}
    .topbar .searchbox input[type=text]{padding:9px 16px}
    .topbar .searchbox input[type=submit]{padding:0 16px}
    .ophelp{max-width:560px;margin:14px auto 0;text-align:left;border:1px solid var(--border);
      border-radius:16px;background:var(--card);padding:0 16px}
    .ophelp summary{cursor:pointer;padding:10px 0;font-size:.8125rem;font-weight:600;color:var(--muted);
      list-style:none}
    .ophelp summary::-webkit-details-marker{display:none}
    .ophelp summary::before{content:'▸ ';display:inline-block;transition:transform 150ms}
    .ophelp[open] summary::before{transform:rotate(90deg)}
    .ophelp .oprow{display:flex;flex-wrap:wrap;align-items:baseline;gap:8px;padding:6px 0;
      border-top:1px solid var(--border);font-size:.8125rem;color:var(--muted)}
    .ophelp .oprow:first-of-type{border-top:0}
    .ophelp .code{font-family:ui-monospace,monospace;background:var(--chip-bg);color:var(--chip-fg);
      padding:2px 7px;border-radius:8px;font-size:.75rem;white-space:nowrap}
    .results{max-width:660px;margin:0 auto;padding:18px 20px 64px}
    .results .meta{color:var(--muted);font-size:.8125rem;margin:2px 0 20px}
    .engine-status{margin:-12px 0 16px}
    .engine-status summary{cursor:pointer;color:var(--muted)}
    .engine-status ul{list-style:none;margin:6px 0 0;padding:0;color:var(--muted)}
    .engine-status .engine-failed{font-weight:600}
    .actions-row{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:0 0 18px}
    .actions-row .alabel{color:var(--muted);font-size:.8125rem;font-weight:600}
    .actions-row .achip{font-size:.8125rem;padding:5px 12px;border:1px solid var(--border);border-radius:8px;
      text-decoration:none;color:var(--link);transition:background-color 150ms,border-color 150ms}
    .actions-row .achip:hover{text-decoration:none;border-color:var(--accent);background-color:rgba(127,127,127,.06)}
    .actions-row .achip:hover{background-color:color-mix(in srgb, var(--accent) 8%, transparent)}
    .result{margin:0 -14px 4px;padding:12px 14px;border-radius:16px;transition:background-color 150ms}
    .result:hover{background-color:rgba(127,127,127,.05)}
    .result:hover{background-color:color-mix(in srgb, var(--accent) 4%, transparent)}
    .result.is-collapsed{display:none}
    .reveal-sentinel{height:1px}
    .result .url{color:var(--url);font-size:.8125rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .result .title{display:block;font-size:1.25rem;line-height:1.35;margin:2px 0 4px;font-weight:500}
    .result .snippet{margin:2px 0 8px;color:var(--snippet);font-size:.875rem;line-height:1.5}
    .engines{display:flex;flex-wrap:wrap;gap:6px}
    .chip{background:var(--chip-bg);color:var(--chip-fg);font-size:.6875rem;padding:3px 10px;border-radius:8px;
      font-weight:500;letter-spacing:.01em}
    .empty{color:var(--muted);text-align:center;padding:48px 0}
    .summary{display:flex;gap:14px;border:1px solid var(--border);border-radius:16px;background:var(--card);
      padding:16px 18px;margin:0 0 22px;box-shadow:var(--shadow);transition:box-shadow 150ms}
    .summary:hover{box-shadow:0 2px 6px rgba(0,0,0,.14)}
    .summary .sbody{flex:1;min-width:0}
    .summary .stitle{font-size:1.0625rem;font-weight:600;margin:0;line-height:1.35}
    .summary .stitle a{color:var(--fg)}
    .summary .sdesc{color:var(--muted);font-size:.75rem;margin:2px 0 6px}
    .summary .sextract{font-size:.875rem;margin:0 0 6px;line-height:1.5}
    .summary .ssource{font-size:.75rem}
    .summary img{width:84px;height:84px;object-fit:cover;border-radius:12px;flex:none}
    @media (max-width:560px){.summary img{display:none}}
    .verticalbar{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 16px}
    .verticalbar .chip{font-size:.8125rem;padding:6px 16px;border:1px solid var(--border);border-radius:16px;
      color:var(--fg);background:var(--card);transition:background-color 150ms,border-color 150ms}
    .verticalbar .chip:hover{text-decoration:none;border-color:var(--accent)}
    .verticalbar .chip:hover{background-color:color-mix(in srgb, var(--accent) 8%, transparent)}
    .verticalbar .chip.active{background:var(--chip-bg);color:var(--fg);border-color:var(--accent);font-weight:600}
    .verticalbar .chip.active{background:color-mix(in srgb, var(--accent) 22%, var(--card));color:var(--fg);
      border-color:var(--accent);font-weight:600}
    a:focus-visible,button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,
    summary:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
    @media(prefers-reduced-motion:reduce){.topbar{backdrop-filter:none}*{animation-duration:.01ms!important;transition-duration:.01ms!important}}
    .scopebar{display:flex;align-items:center;gap:8px;margin:0 0 18px;font-size:13px;color:var(--muted)}
    .scopebar select{font-size:13px;padding:5px 10px;border:1px solid var(--border);border-radius:10px;
      background:var(--card);color:var(--fg);transition:border-color 150ms}
    .scopebar select:hover{border-color:var(--accent)}
    .rank{display:flex;flex-wrap:wrap;gap:6px;margin-top:5px;align-items:center}
    .rank .state{font-size:11px;color:var(--muted);margin-right:2px}
    .rank button{font-size:11px;padding:3px 11px;border:1px solid var(--border);border-radius:10px;
      background:var(--card);color:var(--muted);cursor:pointer;transition:background-color 150ms,border-color 150ms,color 150ms}
    .rank button:hover{border-color:var(--accent);color:var(--fg)}
    .rank button:hover{background-color:color-mix(in srgb, var(--accent) 8%, transparent)}
    .rank button.on{background:var(--accent);color:#fff;border-color:var(--accent)}
    .settings-link{margin-left:auto;border:1px solid var(--border);color:var(--fg);border-radius:20px;
      padding:6px 14px;font-size:13px;text-decoration:none;white-space:nowrap;transition:border-color 150ms,color 150ms}
    .settings-link:hover{border-color:var(--accent);color:var(--accent)}
    .settings-link+.theme-toggle{margin-left:0}
    .topbar .spacer{margin-left:auto}
    .settings{max-width:680px;margin:0 auto;padding:24px 18px 60px}
    .settings h1{font-size:1.5rem;margin:8px 0 18px}
    .settings .saved{color:#fff;background:var(--accent);display:inline-block;border-radius:8px;padding:5px 14px;
      font-size:13px;margin:0 0 16px}
    .settings .card{background:var(--card);border:1px solid var(--border);border-radius:16px;padding:18px 20px;
      margin:0 0 16px;box-shadow:var(--shadow)}
    .settings .card h2{font-size:.9375rem;margin:0 0 14px;color:var(--accent)}
    .settings .card h3.sub{font-size:13px;margin:16px 0 8px;color:var(--muted)}
    .settings .field{margin:0 0 14px}
    .settings .field>label{display:block;font-size:.8125rem;margin:0 0 6px;font-weight:600}
    .settings select{width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:12px;
      background:var(--bg);color:var(--fg);font-size:.875rem;transition:border-color 150ms}
    .settings select:hover{border-color:var(--accent)}
    .settings .checkrow{display:flex;align-items:center;gap:9px;font-size:.875rem;margin:0 0 10px;cursor:pointer}
    .settings .hint{font-size:.75rem;color:var(--muted);margin:6px 0 0}
    .settings .sizerow{display:flex;align-items:center;gap:10px}
    .settings .sizerow button{width:40px;height:40px;border:1px solid var(--border);border-radius:12px;
      background:var(--bg);color:var(--fg);font-size:1rem;cursor:pointer;transition:border-color 150ms,color 150ms,box-shadow 150ms}
    .settings .sizerow button:hover{border-color:var(--accent);color:var(--accent)}
    .settings .sizerow button:hover{box-shadow:0 0 0 4px color-mix(in srgb, var(--accent) 10%, transparent)}
    .settings .sizerow .sizeval{font-size:.875rem;color:var(--muted);min-width:54px}
    .settings .hint .code{background:var(--chip-bg);color:var(--chip-fg);padding:2px 6px;border-radius:6px;font-size:12px}
    .settings .actions{margin-top:6px}
    .settings .actions button{background:var(--accent);color:#fff;border:0;border-radius:24px;padding:11px 28px;
      font-size:15px;font-weight:600;cursor:pointer;transition:box-shadow 150ms,filter 150ms}
    .settings .actions button:hover{box-shadow:0 1px 3px rgba(0,0,0,.2),0 4px 10px rgba(0,0,0,.12)}
    .settings .rulelist,.settings .gogglelist,.settings .histlist{list-style:none;margin:0 0 14px;padding:0;font-size:13px}
    .settings .rulelist li{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:8px 0;border-bottom:1px solid var(--border)}
    .settings .rulelist .dom{font-weight:600;word-break:break-all}
    .settings .rulelist .rank{margin-left:auto}
    .settings .addrule{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
    .settings .addrule input[type=text]{flex:1;min-width:140px;padding:9px 12px;border:1px solid var(--border);
      border-radius:12px;background:var(--bg);color:var(--fg);font-size:14px;transition:border-color 150ms}
    .settings .addrule input[type=text]:hover{border-color:var(--accent)}
    .settings .addrule select{width:auto;min-width:110px}
    .settings .addrule button,.settings .lensform button,.settings .lensdel button,.settings .goggleimport button,.settings .goggleclear button,.settings .histclear button{background:var(--accent);color:#fff;border:0;border-radius:20px;padding:9px 20px;font-size:13px;font-weight:600;cursor:pointer;transition:filter 150ms}
    .settings .addrule button:hover,.settings .lensform button:hover,.settings .goggleimport button:hover{filter:brightness(1.06)}
    .settings .lensitem{display:flex;gap:10px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--border)}
    .settings .lensform{flex:1;display:flex;flex-direction:column;gap:8px}
    .settings .lensform .lname{font-weight:600}
    .settings .lensform input[type=text]{width:100%;padding:9px 12px;border:1px solid var(--border);border-radius:12px;background:var(--bg);color:var(--fg);font-size:14px}
    .settings .lensform .lf{display:flex;flex-direction:column;gap:3px;font-size:12px;color:var(--muted)}
    .settings .lensform button{align-self:flex-start}
    .settings .lensdel button,.settings .goggleclear button,.settings .histclear button{background:transparent;color:var(--muted);border:1px solid var(--border);transition:background-color 150ms,border-color 150ms,color 150ms}
    .settings .lensdel button:hover,.settings .goggleclear button:hover,.settings .histclear button:hover{border-color:#d33;color:#d33}
    .settings .lensdel button:hover,.settings .goggleclear button:hover,.settings .histclear button:hover{background-color:color-mix(in srgb, #d33 8%, transparent)}
    .settings .gogglelist li{display:flex;gap:8px;align-items:center;padding:5px 0;border-bottom:1px solid var(--border)}
    .settings .gogglelist .site{font-weight:600;word-break:break-all}
    .settings .gogglelist .act{margin-left:auto;font-size:11px;color:var(--muted)}
    .settings .histlist li{padding:4px 0;border-bottom:1px solid var(--border);word-break:break-word}
    .settings .goggleimport{display:flex;flex-direction:column;gap:8px}
    .settings textarea{width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:12px;background:var(--bg);color:var(--fg);font-size:13px;font-family:ui-monospace,monospace;resize:vertical;transition:border-color 150ms}
    .settings textarea:hover{border-color:var(--accent)}
    .settings .goggleimport .grow{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
    .settings .goggleclear,.settings .histclear{margin:0 0 8px}
    @media (max-width:560px){.topbar .logo{display:none}}
    """.trimIndent()

// Shared client-side resolve helpers, inlined into every page's theme scripts (mirrors the desktop
// `_THEME_RESOLVE_JS`). `smOsDark` reads the OS scheme; `smSlots` reads the two slot ids (each
// defaulting to its SearchMob default); `smResolve` turns the stored mode (light/dark/system/absent)
// plus the slots into the active theme id (null when no mode is stored, so the CSS :root/@media
// defaults stand); `smApply` sets data-theme + the root font size.
@Suppress("ktlint:standard:max-line-length")
private val THEME_RESOLVE_JS =
    "function smGet(k){try{return localStorage.getItem(k);}catch(e){return null;}}" +
        "function smOsDark(){return !!(window.matchMedia&&matchMedia('(prefers-color-scheme: dark)').matches);}" +
        "function smSlots(){return {light:smGet('sm-light-theme')||'$DEFAULT_LIGHT_ID'," +
        "dark:smGet('sm-dark-theme')||'$DEFAULT_DARK_ID'};}" +
        "function smResolve(){var m=smGet('sm-theme');var s=smSlots();" +
        "if(m==='light')return s.light;if(m==='dark')return s.dark;" +
        "if(m==='system')return smOsDark()?s.dark:s.light;return null;}" +
        "function smApply(){var id=smResolve();var r=document.documentElement;" +
        "if(id)r.setAttribute('data-theme',id);else r.removeAttribute('data-theme');" +
        "var f=smGet('sm-font');if(f)r.style.fontSize=f+'pt';}"

// Runs in <head> before first paint to restore the saved theme + font (avoids a flash of the wrong
// theme/size). Resolves the active slot id from the mode and applies it, plus any saved font size.
private val THEME_INIT_JS = "(function(){try{$THEME_RESOLVE_JS smApply();}catch(e){}})();"

// Defines smToggle() (flips the effective mode light<->dark, persists it, re-applies the resolved
// slot) and labels the quick toggle button with the alternative theme.
@Suppress("ktlint:standard:max-line-length")
private val THEME_TOGGLE_JS =
    "(function(){$THEME_RESOLVE_JS" +
        "function eff(){var m=smGet('sm-theme');" +
        "if(m==='light')return 'light';if(m==='dark')return 'dark';return smOsDark()?'dark':'light';}" +
        "function label(){var b=document.getElementById('sm-theme-btn');" +
        "if(b)b.textContent=eff()==='dark'?'☀ Light':'☾ Dark';}" +
        "window.smToggle=function(){var n=eff()==='dark'?'light':'dark';" +
        "try{localStorage.setItem('sm-theme',n);}catch(e){}smApply();label();};" +
        "label();" +
        "})();"

// Wires the Appearance card (settings page only) to localStorage: on load it sets each control to its
// stored value; on change it persists and live-applies via the shared resolve helpers. Font is clamped
// to the supported bounds/step. No server round-trip; served prefs have always been browser-local.
@Suppress("ktlint:standard:max-line-length")
private val THEME_CONTROLS_JS =
    "(function(){$THEME_RESOLVE_JS" +
        "var MIN=$MIN_FONT_POINT_SIZE,MAX=$MAX_FONT_POINT_SIZE,STEP=$FONT_POINT_STEP,DEF=$DEFAULT_FONT_POINT_SIZE;" +
        "function font(){var f=parseInt(smGet('sm-font'),10);return isNaN(f)?DEF:Math.max(MIN,Math.min(MAX,f));}" +
        "function set(k,v){try{localStorage.setItem(k,v);}catch(e){}}" +
        "var mode=document.getElementById('sm-mode');" +
        "var li=document.getElementById('sm-light-theme');" +
        "var di=document.getElementById('sm-dark-theme');" +
        "var val=document.getElementById('sm-font-val');" +
        "var dec=document.getElementById('sm-font-dec');" +
        "var inc=document.getElementById('sm-font-inc');" +
        "function showFont(){if(val)val.textContent=font()+' pt';}" +
        "var s=smSlots();" +
        "if(mode)mode.value=smGet('sm-theme')||'system';" +
        "if(li)li.value=s.light;if(di)di.value=s.dark;" +
        "showFont();" +
        "if(mode)mode.addEventListener('change',function(){set('sm-theme',mode.value);smApply();});" +
        "if(li)li.addEventListener('change',function(){set('sm-light-theme',li.value);smApply();});" +
        "if(di)di.addEventListener('change',function(){set('sm-dark-theme',di.value);smApply();});" +
        "function step(d){var n=Math.max(MIN,Math.min(MAX,font()+d*STEP));set('sm-font',n);smApply();showFont();}" +
        "if(dec)dec.addEventListener('click',function(){step(-1);});" +
        "if(inc)inc.addEventListener('click',function(){step(1);});" +
        "})();"

// The tab icon: a simple magnifier on the accent blue, self-contained (no asset pipeline, no extra
// request beyond the cached data URI / the tiny /favicon.ico route). Kept minimal so the data URI
// stays short.
private const val FAVICON_SVG =
    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">""" +
        """<rect width="32" height="32" rx="7" fill="#1a73e8"/>""" +
        """<circle cx="14" cy="14" r="6.5" fill="none" stroke="#fff" stroke-width="3"/>""" +
        """<line x1="19" y1="19" x2="25" y2="25" stroke="#fff" stroke-width="3" stroke-linecap="round"/>""" +
        """</svg>"""

// The same SVG as a data URI for <link rel=icon> (CSP allows img-src data:). '#' must be escaped.
private val FAVICON_DATA_URI = "data:image/svg+xml," + FAVICON_SVG.replace("#", "%23").replace("\"", "'")

// Search-as-you-type: a dropdown under the page's own search box, fed by the local /suggest endpoint
// (history + the opt-in upstream source, both already gated server-side). Debounced, aborts stale
// fetches, full keyboard support (arrows/Enter/Escape) with ARIA listbox semantics. Same-origin only
// (CSP connect-src 'self'), so nothing new ever leaves the device from the page itself.
@Suppress("ktlint:standard:max-line-length")
private val SUGGEST_JS =
    """
    (function(){
      var input=document.querySelector('.searchbox input[name=q]');
      if(!input||!window.fetch)return;
      var box=input.closest('.searchbox');if(!box)return;
      var list=document.createElement('ul');
      list.className='suggest';list.id='sm-suggest';list.setAttribute('role','listbox');list.hidden=true;
      box.appendChild(list);
      input.setAttribute('role','combobox');input.setAttribute('aria-expanded','false');
      input.setAttribute('aria-autocomplete','list');input.setAttribute('aria-controls','sm-suggest');
      var items=[],active=-1,timer=null,ctrl=null,lastShown='';
      function close(){list.hidden=true;input.setAttribute('aria-expanded','false');items=[];active=-1;list.innerHTML='';}
      function pick(i){if(i<0||i>=items.length)return;input.value=items[i];close();input.form.submit();}
      function render(sugs){
        list.innerHTML='';items=sugs;active=-1;
        if(!sugs.length){close();return;}
        sugs.forEach(function(s,i){
          var li=document.createElement('li');
          li.textContent=s;li.setAttribute('role','option');li.id='sm-sg-'+i;
          li.addEventListener('mousedown',function(e){e.preventDefault();pick(i);});
          list.appendChild(li);
        });
        list.hidden=false;input.setAttribute('aria-expanded','true');
      }
      function mark(){
        for(var i=0;i<list.children.length;i++){list.children[i].classList.toggle('on',i===active);}
        input.setAttribute('aria-activedescendant',active>=0?'sm-sg-'+active:'');
      }
      function ask(){
        var q=input.value.trim();
        if(!q){close();return;}
        if(ctrl)ctrl.abort();
        ctrl=new AbortController();
        fetch('/suggest?q='+encodeURIComponent(q),{signal:ctrl.signal})
          .then(function(r){return r.json();})
          .then(function(d){
            if(input.value.trim()!==q)return;
            lastShown=q;render((d&&d[1])||[]);
          }).catch(function(){});
      }
      input.addEventListener('input',function(){
        if(timer)clearTimeout(timer);
        timer=setTimeout(ask,150);
      });
      input.addEventListener('keydown',function(e){
        if(list.hidden){
          if(e.key==='ArrowDown'&&input.value.trim()&&lastShown===input.value.trim()){ask();}
          return;
        }
        if(e.key==='ArrowDown'){e.preventDefault();active=(active+1)%items.length;mark();}
        else if(e.key==='ArrowUp'){e.preventDefault();active=(active-1+items.length)%items.length;mark();}
        else if(e.key==='Enter'){if(active>=0){e.preventDefault();pick(active);}else{close();}}
        else if(e.key==='Escape'){close();}
      });
      input.addEventListener('blur',function(){setTimeout(close,120);});
    })();
    """.trimIndent()

// "/" focuses the search box from anywhere on the page (unless already typing somewhere), the
// muscle-memory shortcut every major engine supports.
private val SHORTCUT_JS =
    """
    (function(){
      document.addEventListener('keydown',function(e){
        if(e.key!=='/'||e.ctrlKey||e.metaKey||e.altKey)return;
        var t=e.target;var tag=t&&t.tagName;
        if(tag==='INPUT'||tag==='TEXTAREA'||tag==='SELECT'||(t&&t.isContentEditable))return;
        var input=document.querySelector('.searchbox input[name=q]');
        if(input){e.preventDefault();input.focus();input.select();}
      });
    })();
    """.trimIndent()

// How many results the served page shows before infinite scroll reveals the rest. Matches the GUI
// reveal window and the desktop served page.
private const val REVEAL_SIZE = 10

// Infinite scroll: the page renders the whole ranked pool but hides results past the first window;
// this watches a sentinel at the bottom and unhides the next batch as it scrolls into view. No new
// request (the pool is already in the page), nothing stored, and with JS off every result still shows.
@Suppress("ktlint:standard:max-line-length")
private val REVEAL_JS =
    """
    (function(){
      var step=$REVEAL_SIZE;
      var sentinel=document.querySelector('.reveal-sentinel');
      if(!sentinel)return;
      function more(){
        var h=document.querySelectorAll('.result.is-collapsed');
        for(var i=0;i<step&&i<h.length;i++){h[i].classList.remove('is-collapsed');}
        if(document.querySelectorAll('.result.is-collapsed').length===0){o.disconnect();sentinel.remove();}
      }
      var o=new IntersectionObserver(function(es){
        es.forEach(function(e){if(e.isIntersecting){more();}});
      },{rootMargin:'300px'});
      o.observe(sentinel);
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
    // Lets owner clicks on the served page train the learned model (loopback-only); gated by the
    // owner's personalization toggle, read fresh each request.
    private val personalizationPreferences: PersonalizationPreferences? = null,
    private val personalizationEnabled: suspend () -> Boolean = { false },
    // Owner-only "update available" banner: returns (version, releaseUrl) when a newer release is
    // pending, else null. Read fresh each request so it appears/clears without a server restart.
    private val updateBanner: suspend () -> Pair<String, String>? = { null },
    // Lazily resolved each (re)start so a token minted after the server started still takes effect.
    private val accessToken: () -> String? = { null },
    // Application context for localizing the served chrome to the chosen UI language; null = English.
    private val appContext: Context? = null,
    // Engine catalog backing the served Settings engine-enable toggles. Empty = no engine card.
    private val engineCatalog: List<EngineCatalogEntry> = emptyList(),
    // Server-side thumbnail fetcher for the `/img` proxy; null renders summary cards text-only.
    private val imageProxy: (suspend (String) -> ProxiedImage?)? = null,
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
        // Token gate only matters when bound off-loopback; loopback clients are always exempt.
        val token = if (networkAccessEnabled) accessToken() else null
        val engine =
            embeddedServer(CIO, host = host, port = port) {
                searchModule(
                    provider,
                    guard,
                    suggestionsProvider,
                    rankingPreferences,
                    userPreferences,
                    historyStore,
                    personalizationPreferences,
                    personalizationEnabled,
                    updateBanner,
                    token,
                    appContext = appContext,
                    engineCatalog = engineCatalog,
                    imageProxy = imageProxy,
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
