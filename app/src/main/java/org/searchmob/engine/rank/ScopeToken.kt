package org.searchmob.engine.rank

/**
 * Parse an inline `+name` scope token out of a search query.
 *
 * Scopes (lenses) are normally a sticky, saved selection. For one-off searches it is handier to name
 * the scope in the query itself: `mechanical keyboards +research` runs that one search through the
 * scope whose name starts with "Research", without touching the saved selection.
 *
 * This is pure: it reads the defined scopes off a [RankingRules] and returns the query with the
 * matched token removed plus the matched scope's name (or `null`). It never resolves the scope's
 * filters or mutates the rules; applying the scope is left to the ranking pass. An unmatched `+word`
 * is left in the query so ordinary `+term` input still works. Mirrors the desktop `scope_token.py`.
 */
object ScopeToken {
    /**
     * Strip the first matching `+name` token from [query] and return `(cleanedQuery, scopeName)`.
     *
     * Walks whitespace-delimited tokens left to right; the first `+<rest>` whose `<rest>` matches a
     * defined scope wins. That one token is removed (the rest, including any unmatched `+word`, is
     * kept) and the matched scope's exact name is returned. When nothing matches, the query is
     * returned unchanged with `null`.
     */
    fun parse(
        query: String,
        rules: RankingRules,
    ): Pair<String, String?> {
        if (!query.contains('+')) return query to null
        val tokens = query.split(WHITESPACE).filter { it.isNotEmpty() }
        for ((index, token) in tokens.withIndex()) {
            if (token.length < 2 || !token.startsWith('+')) continue
            val name = matchScope(token.substring(1), rules)
            if (name != null) {
                val cleaned =
                    (tokens.subList(0, index) + tokens.subList(index + 1, tokens.size))
                        .joinToString(" ")
                return cleaned to name
            }
        }
        return query to null
    }

    /**
     * The name of the scope matched by [candidate], or null. First-word match (the scope name's
     * first word, case-insensitive) is tried against every scope before any whole-name fallback, so
     * a first-word hit always beats a normalized full-name hit.
     */
    private fun matchScope(
        candidate: String,
        rules: RankingRules,
    ): String? {
        val lowered = candidate.lowercase()
        for (lens in rules.lenses) {
            val first = lens.name.trim().split(WHITESPACE).firstOrNull()
            if (first != null && first.lowercase() == lowered) return lens.name
        }
        val normalized = normalize(candidate)
        if (normalized.isNotEmpty()) {
            for (lens in rules.lenses) {
                if (normalize(lens.name) == normalized) return lens.name
            }
        }
        return null
    }

    /** Lowercase [text] and keep only its alphanumerics (for whole-name fallback matching). */
    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private val WHITESPACE = Regex("\\s+")
}
