package org.searchmob.engine.sort

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.exp

/** How to order results. `FRESH_RELEVANT` is the default. */
enum class SortMode(val value: String) {
    RELEVANCE("relevance"),
    DATE("date"),
    FRESH_RELEVANT("fresh"),
    ;

    companion object {
        /** Parse a stored/query value; default to the freshness blend on anything unrecognized. */
        fun fromValue(value: String?): SortMode = entries.firstOrNull { it.value == value } ?: FRESH_RELEVANT
    }
}

/**
 * Reorders an already-relevance-ranked list by relevance, strict date, or a freshness+relevance
 * blend. The blend ("query deserves freshness") multiplies a recency boost into the relevance rank,
 * floored at 1.0 so undated results keep full standing - evergreen queries with no dated results
 * look identical to plain relevance. Generic over the item type (like `DomainRanker`). Mirrors the
 * desktop `sort.py`.
 */
object ResultSorter {
    private const val RRF_K = 60
    private const val DAY_MS = 86_400_000.0
    private const val HALF_LIFE_DAYS = 30.0

    fun <T> sort(
        items: List<T>,
        mode: SortMode,
        query: String,
        nowMillis: Long,
        publishedOf: (T) -> Long?,
    ): List<T> {
        if (mode == SortMode.RELEVANCE || items.size < 2) return items

        if (mode == SortMode.DATE) {
            val dated = items.withIndex().filter { publishedOf(it.value) != null }
            val undated = items.filter { publishedOf(it) == null }
            val sortedDated =
                dated.sortedWith(
                    compareByDescending<IndexedValue<T>> { publishedOf(it.value) ?: 0L }
                        .thenBy { it.index },
                )
            return sortedDated.map { it.value } + undated
        }

        val weight = QdfHeuristic.weightFor(query, nowMillis)
        return items
            .mapIndexed { index, item -> Triple(score(index, publishedOf(item), nowMillis, weight), index, item) }
            .sortedWith(compareByDescending<Triple<Double, Int, T>> { it.first }.thenBy { it.second })
            .map { it.third }
    }

    private fun score(
        index: Int,
        published: Long?,
        nowMillis: Long,
        weight: Double,
    ): Double = 1.0 / (RRF_K + index) * recency(published, nowMillis, weight)

    private fun recency(
        published: Long?,
        nowMillis: Long,
        weight: Double,
    ): Double {
        if (published == null) return 1.0
        val ageDays = maxOf(0.0, (nowMillis - published) / DAY_MS)
        return 1.0 + weight * exp(-ageDays / HALF_LIFE_DAYS)
    }
}

/** The "query deserves freshness" weight: a baseline, boosted for obviously time-sensitive queries. */
object QdfHeuristic {
    private const val FRESH_WEIGHT = 0.6
    private const val QDF_BOOST = 1.8
    private val FRESH_KEYWORDS =
        Regex(
            "\\b(release dates?|releases?|latest|today|tonight|this week|breaking|news|updates?|" +
                "scores?|results?|vs\\.?|schedule|when is|when does|prices?|stock|weather|live|now|current)\\b",
            RegexOption.IGNORE_CASE,
        )

    fun weightFor(
        query: String,
        nowMillis: Long,
    ): Double {
        if (FRESH_KEYWORDS.containsMatchIn(query)) return FRESH_WEIGHT * QDF_BOOST
        val year = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).year
        if (query.contains(year.toString()) || query.contains((year + 1).toString())) {
            return FRESH_WEIGHT * QDF_BOOST
        }
        return FRESH_WEIGHT
    }
}
