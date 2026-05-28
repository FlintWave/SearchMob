package org.searchmob.ui.setup

/**
 * The loopback URLs a user needs to wire SearchMob into their browser as a search engine.
 *
 * - [visitUrl] is the SearchMob home/OpenSearch page to open in the browser.
 * - [searchTemplateUrl] is the `…/search?q=%s` template a browser uses for query substitution (`%s`
 *   is the literal placeholder browsers expect, it is NOT URL-encoded here).
 * - [suggestionsTemplateUrl] is the `…/suggest?q=%s` template that Firefox/Chromium custom-engine
 *   forms accept in their separate Suggestion URL field, so autocomplete works without relying on
 *   the browser auto-discovering the OpenSearch descriptor.
 *
 * All three are bound to `127.0.0.1` (loopback only) on the actually-bound [port].
 */
data class SetupUrls(
    val visitUrl: String,
    val searchTemplateUrl: String,
    val suggestionsTemplateUrl: String,
)

/**
 * Builds the loopback [SetupUrls] for the given bound [port]. Pure and side-effect-free so it can be
 * unit-tested without a running server.
 */
fun setupUrls(port: Int): SetupUrls =
    SetupUrls(
        visitUrl = "http://127.0.0.1:$port/",
        searchTemplateUrl = "http://127.0.0.1:$port/search?q=%s",
        suggestionsTemplateUrl = "http://127.0.0.1:$port/suggest?q=%s",
    )
