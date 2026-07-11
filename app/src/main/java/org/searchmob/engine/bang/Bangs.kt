package org.searchmob.engine.bang

import java.net.URLEncoder

/**
 * DuckDuckGo-style !bangs: a `!tag` anywhere at the start or end of a query jumps straight to that
 * site's own search for the rest of the query (`!w privacy` -> Wikipedia's search for "privacy").
 *
 * The table is a small curated set resolved entirely on-device: SearchMob itself never sees a bang
 * query's terms leave through the metasearch fan-out, and no bang-resolution service is consulted.
 * Only an exact, known tag triggers - a token like `!important` simply stays part of the query - so
 * ordinary searches can never be hijacked. The default map/search targets prefer privacy-respecting
 * services; explicit big-brand tags (e.g. `!g`, `!yt`) exist because a bang is the user *choosing*
 * that destination.
 */
object Bangs {
    /** One bang: its tag (without `!`), a human label, the search URL template, and the site home. */
    data class Bang(
        val tag: String,
        val label: String,
        /** Search URL with `{q}` where the URL-encoded query goes. */
        val searchUrl: String,
        /** Where to go when the bang is used with no terms. */
        val homeUrl: String,
    )

    /** A resolved bang redirect: the destination [url] plus the [bang] and remaining [terms]. */
    data class Redirect(
        val url: String,
        val bang: Bang,
        val terms: String,
    )

    @Suppress("ktlint:standard:max-line-length") // one bang per line beats wrapped URL templates
    val ALL: List<Bang> =
        listOf(
            Bang("w", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search={q}", "https://en.wikipedia.org"),
            Bang("wt", "Wiktionary", "https://en.wiktionary.org/wiki/Special:Search?search={q}", "https://en.wiktionary.org"),
            Bang("yt", "YouTube", "https://www.youtube.com/results?search_query={q}", "https://www.youtube.com"),
            Bang("gh", "GitHub", "https://github.com/search?q={q}", "https://github.com"),
            Bang("so", "Stack Overflow", "https://stackoverflow.com/search?q={q}", "https://stackoverflow.com"),
            Bang("r", "Reddit", "https://www.reddit.com/search/?q={q}", "https://www.reddit.com"),
            Bang("hn", "Hacker News", "https://hn.algolia.com/?q={q}", "https://news.ycombinator.com"),
            Bang("mdn", "MDN Web Docs", "https://developer.mozilla.org/en-US/search?q={q}", "https://developer.mozilla.org"),
            Bang("aw", "Arch Wiki", "https://wiki.archlinux.org/index.php?search={q}", "https://wiki.archlinux.org"),
            Bang(
                "osm",
                "OpenStreetMap",
                "https://www.openstreetmap.org/search?query={q}",
                "https://www.openstreetmap.org",
            ),
            Bang(
                "maps",
                "OpenStreetMap",
                "https://www.openstreetmap.org/search?query={q}",
                "https://www.openstreetmap.org",
            ),
            Bang("wa", "Wolfram Alpha", "https://www.wolframalpha.com/input?i={q}", "https://www.wolframalpha.com"),
            Bang("g", "Google", "https://www.google.com/search?q={q}", "https://www.google.com"),
            Bang("ddg", "DuckDuckGo", "https://duckduckgo.com/?q={q}", "https://duckduckgo.com"),
            Bang("b", "Bing", "https://www.bing.com/search?q={q}", "https://www.bing.com"),
            Bang("sp", "Startpage", "https://www.startpage.com/sp/search?query={q}", "https://www.startpage.com"),
            Bang("br", "Brave Search", "https://search.brave.com/search?q={q}", "https://search.brave.com"),
            Bang("mjk", "Mojeek", "https://www.mojeek.com/search?q={q}", "https://www.mojeek.com"),
            Bang("a", "Amazon", "https://www.amazon.com/s?k={q}", "https://www.amazon.com"),
            Bang("e", "eBay", "https://www.ebay.com/sch/i.html?_nkw={q}", "https://www.ebay.com"),
            Bang("imdb", "IMDb", "https://www.imdb.com/find/?q={q}", "https://www.imdb.com"),
            Bang("py", "Python docs", "https://docs.python.org/3/search.html?q={q}", "https://docs.python.org/3/"),
            Bang("npm", "npm", "https://www.npmjs.com/search?q={q}", "https://www.npmjs.com"),
            Bang("pypi", "PyPI", "https://pypi.org/search/?q={q}", "https://pypi.org"),
            Bang("crates", "crates.io", "https://crates.io/search?q={q}", "https://crates.io"),
            Bang("fdroid", "F-Droid", "https://search.f-droid.org/?q={q}", "https://f-droid.org"),
            Bang("cve", "NVD CVE search", "https://nvd.nist.gov/vuln/search/results?query={q}", "https://nvd.nist.gov"),
            Bang(
                "dict",
                "Merriam-Webster",
                "https://www.merriam-webster.com/dictionary/{q}",
                "https://www.merriam-webster.com",
            ),
            Bang("etym", "Etymonline", "https://www.etymonline.com/search?q={q}", "https://www.etymonline.com"),
            Bang("x", "X (Twitter)", "https://x.com/search?q={q}", "https://x.com"),
        )

    private val BY_TAG: Map<String, Bang> = ALL.associateBy { it.tag }

    /** Aliases so the most guessable spellings work too. */
    private val ALIASES =
        mapOf(
            "wiki" to "w",
            "wikipedia" to "w",
            "youtube" to "yt",
            "github" to "gh",
            "reddit" to "r",
            "stackoverflow" to "so",
            "arch" to "aw",
            "google" to "g",
            "amazon" to "a",
            "ebay" to "e",
            "bing" to "b",
            "startpage" to "sp",
            "brave" to "br",
            "mojeek" to "mjk",
            "twitter" to "x",
        )

    /**
     * Resolve a `!bang` in [query]: the first or last whitespace-separated token may be `!tag`
     * (case-insensitive). Returns null when there is no token of that shape or the tag is unknown -
     * the query then proceeds as a normal search, so `!important css` is never hijacked.
     */
    fun resolve(query: String): Redirect? {
        val trimmed = query.trim()
        if (!trimmed.contains('!')) return null
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        fun bangOf(token: String): Bang? {
            if (token.length < 2 || token[0] != '!') return null
            val tag = token.substring(1).lowercase()
            return BY_TAG[tag] ?: ALIASES[tag]?.let { BY_TAG[it] }
        }

        val first = bangOf(tokens.first())
        val last = if (tokens.size > 1) bangOf(tokens.last()) else null
        val (bang, terms) =
            when {
                first != null -> first to tokens.drop(1).joinToString(" ")
                last != null -> last to tokens.dropLast(1).joinToString(" ")
                else -> return null
            }
        val url =
            if (terms.isBlank()) {
                bang.homeUrl
            } else {
                // Encode into a path or query slot: %20 for spaces is valid in both ('+' is not in paths).
                bang.searchUrl.replace("{q}", URLEncoder.encode(terms, "UTF-8").replace("+", "%20"))
            }
        return Redirect(url, bang, terms)
    }
}
