package org.searchmob.engine.rank

/**
 * Parser for a practical subset of the Brave Goggles format: lines of the form
 * `$boost,site=dev.to` / `$downrank,site=example.com` / `$discard,site=spam.example` (the order of the
 * action and `site=` parts does not matter, and `$boost`/`$downrank` may carry a strength like
 * `$boost=2`). A bare site pattern with no `site=` prefix is accepted as the target. Header metadata
 * (`name:`, `description:`, ...) and comment lines (starting with `!`) are ignored. Parsing is
 * fail-soft: unrecognized or malformed lines are skipped.
 */
object Goggles {
    private val metadataPrefixes =
        listOf("name:", "description:", "public:", "author:", "homepage:", "issues:", "avatar:", "license:")

    fun parse(text: String): List<GoggleRule> = text.lineSequence().mapNotNull { parseLine(it.trim()) }.toList()

    private fun parseLine(line: String): GoggleRule? {
        if (line.isEmpty() || line.startsWith("!")) return null
        if (metadataPrefixes.any { line.startsWith(it, ignoreCase = true) }) return null

        var site: String? = null
        var action: RankRule? = null
        for (part in line.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                part.startsWith("site=") -> site = part.removePrefix("site=").trim().takeIf { it.isNotEmpty() }
                part.startsWith("\$boost") -> action = RankRule.RAISE
                part.startsWith("\$downrank") -> action = RankRule.LOWER
                part == "\$discard" -> action = RankRule.BLOCK
                !part.startsWith("\$") && site == null -> site = part // bare pattern
            }
        }
        return if (site != null && action != null) GoggleRule(site, action) else null
    }

    /** True if [host] matches a goggle [pattern] that may contain `*` wildcards. */
    fun matches(
        pattern: String,
        host: String,
    ): Boolean {
        val p = pattern.lowercase().removePrefix("www.")
        val h = host.lowercase().removePrefix("www.")
        if (!p.contains('*')) return h == p || h.endsWith(".$p")
        val regex =
            buildString {
                append('^')
                for (c in p) {
                    when (c) {
                        '*' -> append(".*")
                        '.', '\\', '+', '?', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> append('\\').append(c)
                        else -> append(c)
                    }
                }
                append('$')
            }
        return runCatching { Regex(regex).matches(h) }.getOrDefault(false)
    }
}
