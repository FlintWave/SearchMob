package org.searchmob.engine.vertical

import org.searchmob.engine.sort.SortMode

/**
 * Search verticals (categories) implemented as scoped searches over the existing engines.
 *
 * A vertical never adds a new upstream endpoint or an API key: it reuses the same metasearch engines
 * and privacy proxy as the default web search, and simply scopes the query with `site:` operators the
 * engines already understand, plus a sensible default sort. This keeps the privacy guarantee intact
 * (no new third party sees the query) while giving the user category tabs (Web / News / Forums /
 * Academic). Image and video verticals are intentionally absent: they would require a dedicated media
 * API, i.e. a new third party, which the project does not accept.
 *
 * Mirrors the desktop app's `engines/verticals.py` so both apps scope identically.
 */
enum class Vertical(val value: String) {
    /** The default, unscoped metasearch. */
    WEB("web"),
    NEWS("news"),
    FORUMS("forums"),
    ACADEMIC("academic"),
    ;

    companion object {
        /** Parse a stored/query value, defaulting to [WEB] for null or anything unrecognized. */
        fun fromValue(value: String?): Vertical = entries.firstOrNull { it.value == value?.trim()?.lowercase() } ?: WEB
    }
}

/**
 * Curated, deliberately small site sets. They bias the engines that honor `site:` (DuckDuckGo,
 * Mojeek) toward on-topic sources; engines that ignore the operator still contribute normally.
 * `site:.edu` is a TLD filter the major engines accept inside an OR group.
 */
object Verticals {
    private val NEWS_SITES =
        listOf(
            "reuters.com",
            "apnews.com",
            "bbc.com",
            "bbc.co.uk",
            "npr.org",
            "theguardian.com",
            "aljazeera.com",
            "pbs.org",
        )
    private val FORUM_SITES =
        listOf(
            "reddit.com",
            "news.ycombinator.com",
            "stackexchange.com",
            "stackoverflow.com",
            "lemmy.world",
            "lobste.rs",
        )
    private val ACADEMIC_SITES =
        listOf(
            "arxiv.org",
            "semanticscholar.org",
            "ncbi.nlm.nih.gov",
            "jstor.org",
            "researchgate.net",
            "core.ac.uk",
            ".edu",
        )

    private val SITES =
        mapOf(
            Vertical.NEWS to NEWS_SITES,
            Vertical.FORUMS to FORUM_SITES,
            Vertical.ACADEMIC to ACADEMIC_SITES,
        )

    /**
     * Return [query] scoped for [vertical]: the original plus an OR group of `site:` operators.
     * [Vertical.WEB] and a blank query are returned unchanged. The scoping clause is appended (not
     * prepended) so the user's terms stay first, which the engines weight most heavily.
     */
    fun transformQuery(
        query: String,
        vertical: Vertical,
    ): String {
        val sites = SITES[vertical]
        if (sites == null || query.isBlank()) return query
        val clause = sites.joinToString(" OR ") { "site:$it" }
        return "$query ($clause)"
    }

    /**
     * Remove [vertical]'s scoping clause from [text] when present. An upstream engine's spelling
     * correction echoes the whole query it was sent - including the `(site:... OR ...)` clause
     * [transformQuery] appended - so without this the clause leaks into the "did you mean" line and
     * gets appended a second time when the correction is auto-searched.
     */
    fun stripScopeClause(
        text: String,
        vertical: Vertical,
    ): String {
        val sites = SITES[vertical] ?: return text
        val clause = "(" + sites.joinToString(" OR ") { "site:$it" } + ")"
        return text.replace(clause, "", ignoreCase = true).trim().replace(Regex("\\s{2,}"), " ")
    }

    /**
     * The sort to use when the user has not explicitly chosen one. News is time-sensitive, so it
     * defaults to the freshness+relevance blend; forums and academic favor relevance (an old but
     * highly relevant thread or paper is usually what is wanted). Web keeps the global default.
     */
    fun defaultSort(vertical: Vertical): SortMode =
        when (vertical) {
            Vertical.WEB, Vertical.NEWS -> SortMode.FRESH_RELEVANT
            else -> SortMode.RELEVANCE
        }
}
