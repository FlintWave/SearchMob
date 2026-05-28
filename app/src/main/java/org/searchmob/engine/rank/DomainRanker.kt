package org.searchmob.engine.rank

import java.net.URI

/**
 * Applies the user's personalization [RankingRules] to an already-ranked result list, locally and
 * deterministically. Generic over the item type so the same logic serves the aggregator's results and
 * the UI's results: callers supply [hostOf] (the result's host) and [textOf] (title + snippet, for lens
 * keyword matching).
 *
 * The reordering is score-free and order-preserving: items are bucketed PIN, RAISE, NORMAL, LOWER and
 * concatenated, each bucket keeping the input (relevance) order; BLOCKed items are dropped. An active
 * lens filters first (include/exclude domains and keywords). A per-domain rule wins over a goggle; among
 * matching goggles, discard (block) wins, then boost (raise), then downrank (lower).
 */
object DomainRanker {
    fun host(url: String): String? =
        runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    fun <T> apply(
        items: List<T>,
        rules: RankingRules,
        hostOf: (T) -> String?,
        textOf: (T) -> String = { "" },
    ): List<T> {
        if (rules == RankingRules.EMPTY) return items
        val lens = rules.active
        val pinned = ArrayList<T>()
        val raised = ArrayList<T>()
        val normal = ArrayList<T>()
        val lowered = ArrayList<T>()
        for (item in items) {
            val host = hostOf(item)
            if (lens != null && !passesLens(lens, host, textOf(item))) continue
            when (effectiveRule(host, rules)) {
                RankRule.BLOCK -> Unit // dropped
                RankRule.PIN -> pinned.add(item)
                RankRule.RAISE -> raised.add(item)
                RankRule.LOWER -> lowered.add(item)
                RankRule.NORMAL -> normal.add(item)
            }
        }
        return pinned + raised + normal + lowered
    }

    private fun passesLens(
        lens: Lens,
        host: String?,
        text: String,
    ): Boolean {
        if (host != null) {
            if (lens.includeDomains.isNotEmpty() && lens.includeDomains.none { domainMatch(it, host) }) return false
            if (lens.excludeDomains.any { domainMatch(it, host) }) return false
        }
        val lower = text.lowercase()
        if (lens.includeKeywords.isNotEmpty() &&
            lens.includeKeywords.none { lower.contains(it.lowercase()) }
        ) {
            return false
        }
        if (lens.excludeKeywords.any { lower.contains(it.lowercase()) }) return false
        return true
    }

    private fun effectiveRule(
        host: String?,
        rules: RankingRules,
    ): RankRule {
        if (host == null) return RankRule.NORMAL
        rules.domainRules.entries.firstOrNull { domainMatch(it.key, host) }?.let { return it.value }
        val actions = rules.goggles.filter { Goggles.matches(it.site, host) }.map { it.action }.toSet()
        return when {
            RankRule.BLOCK in actions -> RankRule.BLOCK
            RankRule.RAISE in actions -> RankRule.RAISE
            RankRule.LOWER in actions -> RankRule.LOWER
            else -> RankRule.NORMAL
        }
    }

    /** A rule key matches a host exactly or as a parent domain (so "example.com" covers subdomains). */
    private fun domainMatch(
        ruleDomain: String,
        host: String,
    ): Boolean {
        val d = ruleDomain.lowercase().removePrefix("www.")
        return host == d || host.endsWith(".$d")
    }
}
