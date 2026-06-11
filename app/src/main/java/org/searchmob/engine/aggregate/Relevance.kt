package org.searchmob.engine.aggregate

/**
 * Lexical query-match relevance signal blended into the aggregator's RRF ranking.
 *
 * RRF fuses several engines' rankings, but it trusts each engine's order: with mostly single-engine
 * results the fused scores are nearly tied (1/60 .. 1/69), so an irrelevant result one engine happened
 * to rank highly slips into the top. Nothing in the pipeline asks "does this result actually match the
 * query?".
 *
 * This object adds that missing signal: a deterministic, on-device lexical match score over the
 * result's title and snippet (how many of the query's content words appear, weighted toward the title,
 * with a small exact-phrase bonus). The aggregator multiplies each result's RRF score by a factor
 * derived from this lexical score, so query-match leads the ranking and engine consensus stays a
 * secondary signal. No corpus, model, or network: pure string work, the Kotlin twin of the desktop
 * `engines/relevance.py`.
 *
 * The blend is deliberately bounded (a non-matching result keeps [BASE] of its RRF weight rather than
 * zero) so a relevant result phrased differently from the query (e.g. "artificial intelligence" for the
 * query term "ai") that several engines agree on is demoted, not deleted. Everything here is
 * language-agnostic: the tokenizer is Unicode-aware, English stemming is gated to ASCII, and language
 * affinity is script-relative, so it works in whatever language the user searches in.
 */
object Relevance {
    /**
     * The blend is DEMOTION-ONLY: the factor is capped at 1.0, so a well-matching result keeps its
     * full RRF weight and engine consensus still decides the order among good matches (we never promote
     * a keyword-stuffed title over a result several engines agree on). A poorly-matching result is sunk
     * toward [BASE] of its RRF weight. With BASE=0.5, GAIN=1.0 a result matching half the query terms
     * is already at full weight; only weaker matches are penalized.
     */
    const val BASE = 0.5
    const val GAIN = 1.0

    /**
     * Navigational promotion: when the query, squished to one separator-free word, exactly names a
     * result's registrable domain label (query "threejs" -> threejs.org, "node js" -> nodejs.org),
     * that result is almost certainly the site the searcher wanted, so its final score is multiplied
     * by this factor to float it to the top past the demotion-only relevance blend. Exact-match only
     * and capped to short queries, so it is high precision: a long descriptive query never triggers it.
     */
    const val NAVIGATIONAL_BOOST = 4.0

    // A navigational query is short by nature ("threejs", "node js", "khan academy"). Past this many
    // content terms the query is descriptive, not a site name, so the boost is not considered.
    private const val MAX_NAVIGATIONAL_TERMS = 3

    // Second-level public-suffix heads (e.g. "co" in "example.co.uk") so the registrable label is the
    // segment before them, not the suffix itself. Short, common list; a full Public Suffix List is
    // overkill for a high-precision exact-match signal.
    private val COMPOUND_TLD_HEADS = setOf("co", "com", "org", "net", "ac", "gov", "edu")

    // Conservative stopword set: function words and generic query modifiers that carry little subject
    // intent. Kept short on purpose so the actual subject of a query is never stripped. If a query is
    // nothing but stopwords, `contentTerms` falls back to all tokens so matching still works.
    private val STOPWORDS =
        setOf(
            "a", "an", "the", "of", "to", "in", "on", "for", "and", "or", "is", "are", "be", "do",
            "does", "did", "how", "what", "why", "when", "where", "who", "which", "with", "this",
            "that", "it", "at", "by", "from", "as", "your", "my", "i", "vs", "into", "about", "best",
            "top", "good", "vs.", "near", "me",
        )

    // Unicode code-point ranges that count as Latin script (so accented and Vietnamese text is NOT
    // treated as "foreign"): Basic Latin + Latin-1 Supplement + Extended-A/B, Latin Extended Additional,
    // and Extended-C/D. A letter outside these is from another script (Cyrillic, CJK, Arabic, Greek...).
    private val LATIN_RANGES =
        listOf(0x41..0x24F, 0x1E00..0x1EFF, 0x2C60..0x2C7F, 0xA720..0xA7FF)

    /**
     * Maximal runs of letters/digits (any script), the Unicode-aware twin of Python's
     * `re.compile(r"[^\W_]+", re.UNICODE)`. Java/Kotlin regex `\w` is ASCII-only by default, which
     * would silently drop all non-Latin text, so this scans code points with [Character.isLetterOrDigit]
     * instead (the underscore is naturally excluded). Space-less scripts (CJK) tokenize as one run;
     * finer segmentation is left to the localization pass.
     */
    private fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) {
                current.appendCodePoint(cp)
            } else if (current.isNotEmpty()) {
                out.add(current.toString())
                current.setLength(0)
            }
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    /**
     * Very light English suffix folding so "keyboards" matches "keyboard", "reviews" "review".
     *
     * Not a real stemmer: it just trims the commonest inflectional endings on longer words, applied to
     * both the query and the document so matching is symmetric. Conservative on length so short words
     * (e.g. "ios", "css", "vs") are never mangled. Gated to ASCII since the suffix rules are English;
     * non-ASCII words (other languages) pass through untouched, never corrupted. Per-language stemming
     * is a future refinement for the localization pass.
     */
    private fun stem(word: String): String {
        if (!word.all { it.code <= 0x7F }) return word
        if (word.length >= 5) {
            if (word.endsWith("ies")) return word.dropLast(3) + "y"
            for (suffix in listOf("ing", "ers")) {
                if (word.endsWith(suffix)) return word.dropLast(suffix.length)
            }
        }
        if (word.length >= 4) {
            for (suffix in listOf("es", "ed", "er")) {
                if (word.endsWith(suffix)) return word.dropLast(suffix.length)
            }
            if (word.endsWith("s") && !word.endsWith("ss")) return word.dropLast(1)
        }
        return word
    }

    /** Coarse script bucket for a letter code point. Language-agnostic: works for whatever the query is. */
    private fun scriptOf(cp: Int): String {
        if (LATIN_RANGES.any { cp in it }) return "latin"
        if (cp in 0x0400..0x052F) return "cyrillic"
        if (cp in 0x0370..0x03FF) return "greek"
        if (cp in 0x0590..0x05FF) return "hebrew"
        if (cp in 0x0600..0x06FF || cp in 0x0750..0x077F) return "arabic"
        if (cp in 0x0900..0x097F) return "devanagari"
        if (cp in 0x0E00..0x0E7F) return "thai"
        if (cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x3040..0x30FF || cp in 0xAC00..0xD7AF) {
            return "cjk"
        }
        return "other"
    }

    /** Most common letter script in [text], or null when it has no letters (e.g. only digits). */
    private fun dominantScript(text: String): String? {
        val counts = HashMap<String, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) {
                val script = scriptOf(cp)
                counts[script] = (counts[script] ?: 0) + 1
            }
            i += Character.charCount(cp)
        }
        return counts.maxByOrNull { it.value }?.key
    }

    /**
     * 1.0 when the result is in the same script as the query, else a demotion factor.
     *
     * Script-relative on purpose so this works in any UI/query language, not just English: a result
     * dominated by a different script than the query (Cyrillic results for a Latin query, or Latin for a
     * CJK query...) is almost never what the searcher wanted and is sunk. A query with no letters (pure
     * digits/symbols) or a result whose dominant script matches is never penalized. Distinguishing
     * languages that share a script (e.g. English vs French) needs real language detection and is left
     * to the localization pass; this catches the jarring cross-script case.
     */
    fun languageAffinity(
        query: String,
        title: String,
        snippet: String,
    ): Double {
        val queryScript = dominantScript(query) ?: return 1.0
        val resultScript = dominantScript("$title $snippet")
        return if (resultScript == null || resultScript == queryScript) 1.0 else 0.4
    }

    /**
     * Distinct content tokens of [query] (lowercased, length >= 2, stopwords removed, order kept).
     *
     * Falls back to all tokens when every token is a stopword, so a query like "how to" still matches on
     * something rather than scoring every result zero.
     */
    fun contentTerms(query: String): List<String> {
        val distinct = LinkedHashSet<String>()
        for (token in tokens(query.lowercase())) {
            if (token.length >= 2) distinct.add(token)
        }
        val content = distinct.filter { it !in STOPWORDS }
        return content.ifEmpty { distinct.toList() }
    }

    /** The query's content terms joined with no separators, lowercased ("three js" -> "threejs"). */
    fun squishedQuery(terms: List<String>): String = terms.joinToString("").lowercase()

    /**
     * The main label of [host]: "threejs.org" -> "threejs", "docs.python.org" -> "python".
     *
     * Strips a leading "www." and returns the segment immediately left of the public suffix, stepping
     * over a known second-level suffix head ("co.uk", "com.au") when present. Not a full Public Suffix
     * List, which is overkill for an exact-match navigational signal.
     */
    fun registrableLabel(host: String): String {
        val cleaned = host.lowercase().trim().removePrefix("www.")
        val parts = cleaned.split(".").filter { it.isNotEmpty() }
        if (parts.size < 2) return cleaned
        if (parts.size >= 3 && parts[parts.size - 2] in COMPOUND_TLD_HEADS) return parts[parts.size - 3]
        return parts[parts.size - 2]
    }

    /**
     * [NAVIGATIONAL_BOOST] when the squished query exactly names [host]'s main label, else 1.0.
     *
     * High precision by design: only a short query (<= [MAX_NAVIGATIONAL_TERMS] content terms, at least
     * three characters squished) whose separator-free form equals the registrable label fires, so
     * "threejs"/"three js" promote threejs.org while a descriptive query never does.
     */
    fun navigationalFactor(
        terms: List<String>,
        host: String,
    ): Double {
        if (terms.isEmpty() || terms.size > MAX_NAVIGATIONAL_TERMS) return 1.0
        val squished = squishedQuery(terms)
        if (squished.length < 3) return 1.0
        return if (squished == registrableLabel(host)) NAVIGATIONAL_BOOST else 1.0
    }

    /**
     * Stemmed tokens of [text], plus adjacent-pair concatenations.
     *
     * A separator-split brand name in a title ("three.js" -> tokens [three, js]) is bridged by adding
     * the concatenation "threejs" so the same word typed without the separator (the query "threejs")
     * matches. The concatenation is run through [stem] like any token, so the trailing-`s` folding that
     * turns the query "threejs" into "threej" also applies to the bridged "three"+"js" and they meet.
     */
    private fun bridgedStems(text: String): Set<String> {
        val raw = tokens(text.lowercase())
        val stems = raw.map { stem(it) }.toMutableSet()
        for (i in 0 until raw.size - 1) {
            stems.add(stem(raw[i] + raw[i + 1]))
        }
        return stems
    }

    /**
     * How well [title]/[snippet] match [terms], in [0, 1]. Higher = better query match.
     *
     * Combines whole-word coverage (fraction of query terms present anywhere), title coverage (the same
     * but title-only, weighted equally because a title hit is a strong relevance signal), and a small
     * bonus when the terms appear as a contiguous phrase in the title. Whole-word membership (not
     * substring) avoids false hits like the term "ai" matching inside "available". Adjacent tokens are
     * also bridged (see [bridgedStems]) so "threejs" matches a "three.js" title.
     */
    fun lexicalScore(
        title: String,
        snippet: String,
        terms: List<String>,
    ): Double {
        if (terms.isEmpty()) return 0.0
        val titleStems = bridgedStems(title)
        val snippetStems = bridgedStems(snippet)
        val stems = terms.map { stem(it) }
        val n = stems.size
        val inTitle = stems.count { it in titleStems }
        val inAny = stems.count { it in titleStems || it in snippetStems }
        val coverage = inAny.toDouble() / n
        val titleCoverage = inTitle.toDouble() / n
        val titleSeq = tokens(title.lowercase()).joinToString(" ") { stem(it) }
        val phrase = if (n >= 2 && titleSeq.contains(stems.joinToString(" "))) 1.0 else 0.0
        val base = 0.5 * coverage + 0.4 * titleCoverage + 0.1 * phrase
        // The head term is usually the query's subject (after stopwords: "ai" in "ai news today",
        // "mechanical" in "best mechanical keyboard"). A result missing the subject entirely is a poor
        // match even if it covers the generic remainder, so halve its score.
        val headPresent = stems[0] in titleStems || stems[0] in snippetStems
        return if (headPresent) base else base * 0.5
    }

    /**
     * Fold the lexical match and language affinity into an RRF score (demotion-only).
     *
     * The lexical factor is capped at 1.0, so a well-matching result keeps its full RRF weight and
     * engine consensus still orders the good matches (a keyword-stuffed title is never promoted over a
     * result several engines agree on). A weak match is sunk toward [BASE]. The language [affinity]
     * (<= 1.0 for a foreign-script result) multiplies on top, demoting wrong-language hits.
     */
    fun blendedScore(
        rrfScore: Double,
        lexical: Double,
        affinity: Double = 1.0,
    ): Double {
        val lexicalFactor = minOf(1.0, BASE + GAIN * lexical)
        return rrfScore * lexicalFactor * affinity
    }
}
