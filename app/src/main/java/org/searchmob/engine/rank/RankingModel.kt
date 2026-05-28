package org.searchmob.engine.rank

import kotlinx.serialization.Serializable

/**
 * How a domain (or a goggle/lens match) affects ranking. The ranker treats these as ordered buckets:
 * PIN first, then RAISE, then NORMAL, then LOWER; BLOCK is dropped. NORMAL means "no rule" and is never
 * stored.
 */
@Serializable
enum class RankRule { BLOCK, LOWER, NORMAL, RAISE, PIN }

/**
 * A named scope. An [includeDomains] set (when non-empty) restricts results to those domains;
 * [excludeDomains] and [excludeKeywords] remove matches; [includeKeywords] (when non-empty) requires a
 * title/snippet match. Domains are bare hosts ("github.com"); keywords are matched case-insensitively.
 */
@Serializable
data class Lens(
    val name: String,
    val includeDomains: List<String> = emptyList(),
    val excludeDomains: List<String> = emptyList(),
    val includeKeywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
)

/** One imported Brave-Goggles rule: a site pattern (may contain `*`) mapped to a ranking action. */
@Serializable
data class GoggleRule(
    val site: String,
    val action: RankRule,
)

/** The complete on-device personalization rule set. Serialized as one JSON blob in the encrypted store. */
@Serializable
data class RankingRules(
    val domainRules: Map<String, RankRule> = emptyMap(),
    val lenses: List<Lens> = emptyList(),
    val activeLens: String? = null,
    val goggles: List<GoggleRule> = emptyList(),
) {
    val active: Lens? get() = lenses.firstOrNull { it.name == activeLens }

    companion object {
        val EMPTY = RankingRules()
    }
}
