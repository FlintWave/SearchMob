package org.searchmob.engine.query

import org.searchmob.engine.rank.DomainRanker
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A query parsed into its Google-fu style operators plus the leftover free text.
 *
 * [engineQuery] is what actually goes upstream: operators the engines themselves understand
 * (`site:`, quoted phrases, `-exclusions`, `OR`/`|`) are forwarded verbatim so the engine's own index
 * does the heavy lifting; operators no public engine implements consistently (`intitle:`, `inurl:`,
 * `before:`, `after:`) are either turned into a plain recall hint or dropped, and the actual constraint
 * is enforced locally by [matches] over the aggregated results. [cleanText] is the query with every
 * operator stripped to plain words, for anything that reasons about "what is the user asking about"
 * rather than "how do I fetch it" (on-device relevance, spelling correction, the contextual-summary
 * lookup).
 */
data class ParsedQuery(
    val raw: String,
    val cleanText: String,
    val engineQuery: String,
    val phrases: List<String>,
    val excludedTerms: List<String>,
    val excludedPhrases: List<String>,
    val includeSites: List<String>,
    val excludeSites: List<String>,
    val inTitle: List<String>,
    val notInTitle: List<String>,
    val inUrl: List<String>,
    val notInUrl: List<String>,
    val fileTypes: List<String>,
    val afterMillis: Long?,
    val beforeMillis: Long?,
) {
    /** True when any operator is enforced locally by [matches] (everything but plain terms/phrases/OR). */
    val hasFilters: Boolean
        get() =
            excludedTerms.isNotEmpty() || excludedPhrases.isNotEmpty() ||
                includeSites.isNotEmpty() || excludeSites.isNotEmpty() ||
                inTitle.isNotEmpty() || notInTitle.isNotEmpty() ||
                inUrl.isNotEmpty() || notInUrl.isNotEmpty() ||
                fileTypes.isNotEmpty() || afterMillis != null || beforeMillis != null

    /**
     * True when an aggregated result survives every locally-enforced operator in this query.
     *
     * Positive [phrases] and plain terms are deliberately NOT checked here: they were already sent
     * upstream in [engineQuery] and drive lexical relevance ([org.searchmob.engine.aggregate.Relevance]).
     * A snippet is a partial excerpt of the page, so demanding the phrase appear in the title/snippet
     * would reject results whose full body matches but whose short excerpt happens not to quote it;
     * the engines and the relevance ranker are trusted to have already done that job.
     *
     * A date-window bound ([afterMillis]/[beforeMillis]) excludes a result with no known
     * [publishedMillis], deliberately: the user explicitly asked for a window, so an undated result
     * cannot be confirmed to be in it and is treated as a miss rather than let through.
     */
    fun matches(
        title: String,
        url: String,
        snippet: String,
        publishedMillis: Long?,
    ): Boolean {
        val host = DomainRanker.host(url)
        if (includeSites.isNotEmpty() && (host == null || includeSites.none { siteMatches(it, host) })) return false
        if (excludeSites.isNotEmpty() && host != null && excludeSites.any { siteMatches(it, host) }) return false
        if (inTitle.any { !title.contains(it, ignoreCase = true) }) return false
        if (notInTitle.any { title.contains(it, ignoreCase = true) }) return false
        val lowerUrl = url.lowercase()
        if (inUrl.any { !lowerUrl.contains(it.lowercase()) }) return false
        if (notInUrl.any { lowerUrl.contains(it.lowercase()) }) return false
        if (fileTypes.isNotEmpty()) {
            val extension = extensionOf(url)
            if (extension == null || extension !in fileTypes) return false
        }
        if (afterMillis != null || beforeMillis != null) {
            if (publishedMillis == null) return false
            if (afterMillis != null && publishedMillis < afterMillis) return false
            if (beforeMillis != null && publishedMillis >= beforeMillis) return false
        }
        if (excludedTerms.isNotEmpty()) {
            val words = wordsOf("$title $snippet ${host.orEmpty()}")
            if (excludedTerms.any { it.lowercase() in words }) return false
        }
        val haystack = "$title $snippet"
        if (excludedPhrases.any { haystack.contains(it, ignoreCase = true) }) return false
        return true
    }

    /**
     * Whether [entry] (a normalized `site:`/`-site:` value) covers [host]. An entry starting with `.`
     * (a bare TLD like `.edu`) matches any host ending in it; otherwise the entry must equal the host or
     * be one of its parent domains (`example.com` covers `docs.example.com` but not `notexample.com`).
     */
    private fun siteMatches(
        entry: String,
        host: String,
    ): Boolean = if (entry.startsWith(".")) host.endsWith(entry) else host == entry || host.endsWith(".$entry")

    /**
     * The lowercased extension of [url]'s last path segment (query string and fragment ignored), or
     * null when the path has no segment or that segment has no dot. Parsed via [URI] rather than naive
     * string-splitting so a bare `https://example.com` never misreads its TLD (the `.com` in the host)
     * as a file extension.
     */
    private fun extensionOf(url: String): String? {
        val path = runCatching { URI(url.trim()).path }.getOrNull() ?: return null
        val lastSegment = path.substringAfterLast('/')
        if (!lastSegment.contains('.')) return null
        return lastSegment.substringAfterLast('.').lowercase().takeIf { it.isNotEmpty() }
    }

    /** Lowercased maximal letter/digit runs in [text], for whole-word exclusion matching. */
    private fun wordsOf(text: String): Set<String> {
        val out = HashSet<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                current.append(ch.lowercaseChar())
            } else if (current.isNotEmpty()) {
                out.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }
}

/**
 * Parses Google-fu style search operators (`site:`, `-exclude`, `"exact phrase"`, `intitle:`, `inurl:`,
 * `filetype:`/`ext:`, `before:`/`after:`, `OR`/`|`) out of a raw query into a [ParsedQuery].
 *
 * Pure and deterministic: no I/O, no locale/clock dependence beyond UTC date math, so the same input
 * always parses the same way. The server truncates queries at 512 characters before they ever reach
 * here, so this has to tolerate garbage (an unterminated quote, a bare `-`, an empty `site:`) without
 * throwing, never silently dropping a token it does not understand — anything it cannot make sense of
 * falls back to being treated as ordinary query text.
 */
object QueryOperators {
    private val RECOGNIZED_OPS = setOf("site", "intitle", "inurl", "filetype", "ext", "before", "after")
    private val YEAR = Regex("^(\\d{4})$")

    // Month/day accept one or two digits: rejecting `after:2024-3-1` outright would keep the whole
    // token as literal upstream query text (see applyOperator), actively harming results.
    private val YEAR_MONTH = Regex("^(\\d{4})-(\\d{1,2})$")
    private val FULL_DASH = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val FULL_SLASH = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$")

    fun parse(raw: String): ParsedQuery {
        val acc = Accumulator()
        for (token in tokenize(raw)) {
            if (token == "OR" || token == "|") {
                acc.engineParts.add(token)
                continue
            }

            val negated = token.length > 1 && token.startsWith("-")
            val body = if (negated) token.substring(1) else token

            if (body.startsWith("\"")) {
                // A blank phrase (a stray `"` or `-"`) is dropped entirely: an empty excluded phrase
                // would match every result (`contains("")` is always true) and filter everything out.
                val phrase = unquote(body)
                if (phrase.isBlank()) continue
                if (negated) {
                    acc.excludedPhrases.add(phrase)
                    acc.engineParts.add("-\"$phrase\"")
                } else {
                    acc.phrases.add(phrase)
                    acc.cleanParts.add(phrase)
                    acc.engineParts.add("\"$phrase\"")
                }
                continue
            }

            val colonIndex = body.indexOf(':')
            val opName = if (colonIndex > 0) body.substring(0, colonIndex).lowercase() else null
            if (opName != null && opName in RECOGNIZED_OPS) {
                val valueRaw = body.substring(colonIndex + 1)
                if (applyOperator(opName, negated, token, valueRaw, acc)) continue
            }

            if (negated) {
                acc.excludedTerms.add(body)
                acc.engineParts.add(token)
            } else {
                acc.cleanParts.add(token)
                acc.engineParts.add(token)
            }
        }

        return ParsedQuery(
            raw = raw,
            cleanText = acc.cleanParts.joinToString(" "),
            engineQuery = acc.engineParts.joinToString(" "),
            phrases = acc.phrases,
            excludedTerms = acc.excludedTerms,
            excludedPhrases = acc.excludedPhrases,
            includeSites = acc.includeSites,
            excludeSites = acc.excludeSites,
            inTitle = acc.inTitle,
            notInTitle = acc.notInTitle,
            inUrl = acc.inUrl,
            notInUrl = acc.notInUrl,
            fileTypes = acc.fileTypes,
            afterMillis = acc.afterMillis,
            beforeMillis = acc.beforeMillis,
        )
    }

    /** The mutable accumulators [parse] fills in as it walks the tokens, in original token order. */
    private class Accumulator {
        val cleanParts = ArrayList<String>()
        val engineParts = ArrayList<String>()
        val phrases = ArrayList<String>()
        val excludedTerms = ArrayList<String>()
        val excludedPhrases = ArrayList<String>()
        val includeSites = ArrayList<String>()
        val excludeSites = ArrayList<String>()
        val inTitle = ArrayList<String>()
        val notInTitle = ArrayList<String>()
        val inUrl = ArrayList<String>()
        val notInUrl = ArrayList<String>()
        val fileTypes = ArrayList<String>()
        var afterMillis: Long? = null
        var beforeMillis: Long? = null
    }

    /**
     * Handles one `op:value` token, mutating [acc] for the recognized operators.
     *
     * Returns false to signal that the token was NOT actually consumed as an operator (a `-` negated
     * `filetype:`/`ext:`/`before:`/`after:` has no defined negated meaning), so the caller falls back to
     * treating it as a plain `-word` exclusion.
     */
    private fun applyOperator(
        opName: String,
        negated: Boolean,
        token: String,
        valueRaw: String,
        acc: Accumulator,
    ): Boolean {
        when (opName) {
            "site" -> {
                val value = normalizeSite(unquoteOrRaw(valueRaw))
                if (value.isEmpty()) return true // an empty operator is dropped entirely
                if (negated) acc.excludeSites.add(value) else acc.includeSites.add(value)
                acc.engineParts.add(if (negated) "-site:$value" else "site:$value")
            }
            "filetype", "ext" -> {
                if (negated) return false // no defined negated meaning; caller treats it as -word
                val value = normalizeFileType(unquoteOrRaw(valueRaw))
                if (value.isEmpty()) return true
                acc.fileTypes.add(value)
                acc.engineParts.add("filetype:$value")
            }
            "intitle", "inurl" -> {
                val value = unquoteOrRaw(valueRaw)
                if (value.isEmpty()) return true
                if (negated) {
                    if (opName == "intitle") acc.notInTitle.add(value) else acc.notInUrl.add(value)
                    // -intitle:/-inurl: are locally-enforced only; dropped from the upstream query.
                } else {
                    if (opName == "intitle") acc.inTitle.add(value) else acc.inUrl.add(value)
                    acc.cleanParts.add(value)
                    acc.engineParts.add(value)
                }
            }
            "before", "after" -> {
                if (negated) return false // no defined negated meaning; caller treats it as -word
                val value = unquoteOrRaw(valueRaw)
                if (value.isEmpty()) return true
                val millis = parseDate(value)
                if (millis == null) {
                    // Never silently drop something we could not parse: keep the whole token as text.
                    acc.cleanParts.add(token)
                    acc.engineParts.add(token)
                } else {
                    if (opName == "after") acc.afterMillis = millis else acc.beforeMillis = millis
                    // before:/after: are locally-enforced only; dropped from the upstream query.
                }
            }
        }
        return true
    }

    /** Split [raw] on whitespace, except inside `"..."`; an unterminated quote runs to end of string. */
    private fun tokenize(raw: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in raw) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    /** Strip the surrounding quotes off [text] (which starts with `"`); an unterminated one runs to the end. */
    private fun unquote(text: String): String {
        val closing = text.indexOf('"', 1)
        return if (closing >= 0) text.substring(1, closing) else text.substring(1)
    }

    private fun unquoteOrRaw(text: String): String = if (text.startsWith("\"")) unquote(text) else text

    /** `*.example.com` / `example.com.` -> `example.com`. */
    private fun normalizeSite(value: String): String {
        var v = value.trim()
        if (v.startsWith("*.")) v = v.substring(2)
        return v.trimEnd('.').lowercase()
    }

    /** `.PDF` / `PDF` -> `pdf`. */
    private fun normalizeFileType(value: String): String = value.trim().lowercase().removePrefix(".")

    /**
     * `YYYY`, `YYYY-MM`, `YYYY-MM-DD`, or `YYYY/MM/DD` -> UTC epoch millis at the start of that day
     * (year/month default to day 1 / January 1). Null for anything that does not match one of those
     * shapes or names an impossible calendar date (e.g. month 13).
     */
    private fun parseDate(value: String): Long? {
        val date =
            when {
                YEAR.matches(value) -> runCatching { LocalDate.of(value.toInt(), 1, 1) }.getOrNull()
                YEAR_MONTH.matches(value) -> {
                    val (y, m) = YEAR_MONTH.find(value)!!.destructured
                    runCatching { LocalDate.of(y.toInt(), m.toInt(), 1) }.getOrNull()
                }
                FULL_DASH.matches(value) -> {
                    val (y, m, d) = FULL_DASH.find(value)!!.destructured
                    runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
                }
                FULL_SLASH.matches(value) -> {
                    val (y, m, d) = FULL_SLASH.find(value)!!.destructured
                    runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
                }
                else -> null
            }
        return date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    }
}
