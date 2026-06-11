package org.searchmob.engine.aggregate

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.searchmob.engine.EngineAdapter
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResult
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.SearchQuery
import org.searchmob.engine.date.SnippetDateParser
import org.searchmob.engine.rank.DomainRanker

/** A merged, ranked result and the engines that contributed it. */
data class AggregatedResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engines: List<String>,
    val score: Double,
    /** Best-known publication time (epoch millis), or null. Drives freshness sorting. */
    val publishedMillis: Long? = null,
)

/** One engine's outcome for a single search. */
enum class EngineStatus { CONTRIBUTED, EMPTY, FAILED }

/**
 * Per-engine outcome for one search: it returned results, returned nothing, or failed (raised or
 * timed out). Computed locally for owner-facing diagnostics, never persisted or transmitted.
 */
data class EngineOutcome(
    val name: String,
    val status: EngineStatus,
    val count: Int = 0,
)

/**
 * Ranked results, the consensus upstream spelling correction (if any engine offered one), and the
 * per-engine outcome that produced them. [engineStatus] is for owner-facing diagnostics only.
 */
data class AggregationResult(
    val results: List<AggregatedResult>,
    val correction: String? = null,
    val engineStatus: List<EngineOutcome> = emptyList(),
)

/**
 * Queries enabled engines in parallel with bounded concurrency, tolerates per-engine failure and
 * timeout (returning partial results), dedups by normalized URL, and ranks by deterministic
 * Reciprocal Rank Fusion. Identical inputs always produce identical ordering.
 */
class Aggregator(
    private val maxConcurrent: Int = 4,
    private val rrfK: Int = 60,
) {
    suspend fun aggregate(
        query: SearchQuery,
        engines: List<Pair<EngineAdapter, EngineContext>>,
    ): AggregationResult {
        val semaphore = Semaphore(maxConcurrent)
        // Each engine's adapter paired with its result (or null when it timed out / threw), so we can
        // report the per-engine outcome — distinguishing a failure from a genuine empty — alongside
        // the merged results, without changing the fail-soft behaviour.
        val perEngine: List<Pair<EngineAdapter, EngineResult?>> =
            supervisorScope {
                engines
                    .map { (adapter, ctx) ->
                        async {
                            val result =
                                try {
                                    semaphore.withPermit {
                                        withTimeoutOrNull(ctx.timeoutMs) { adapter.search(query, ctx) }
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            adapter to result
                        }
                    }.awaitAll()
            }
        val successes = perEngine.mapNotNull { it.second as? EngineResult.Success }
        val engineStatus =
            perEngine.map { (adapter, result) ->
                when {
                    result is EngineResult.Success && result.items.isNotEmpty() ->
                        EngineOutcome(adapter.id, EngineStatus.CONTRIBUTED, result.items.size)
                    result is EngineResult.Success -> EngineOutcome(adapter.id, EngineStatus.EMPTY)
                    else -> EngineOutcome(adapter.id, EngineStatus.FAILED)
                }
            }
        val results = rank(successes.flatMap { it.items }, query.terms)
        return AggregationResult(results, consensusCorrection(query.terms, successes), engineStatus)
    }

    /**
     * The most frequently reported upstream correction (ties resolve to the first seen), grouped
     * case-insensitively and ignoring any correction equal to the original query. Null when none.
     */
    private fun consensusCorrection(
        query: String,
        successes: List<EngineResult.Success>,
    ): String? {
        val byKey = LinkedHashMap<String, Pair<String, Int>>()
        for (success in successes) {
            val correction = success.correction?.trim()?.takeIf { it.isNotBlank() } ?: continue
            if (correction.equals(query.trim(), ignoreCase = true)) continue
            val key = correction.lowercase()
            val existing = byKey[key]
            byKey[key] = if (existing == null) correction to 1 else existing.first to existing.second + 1
        }
        return byKey.values.maxByOrNull { it.second }?.first
    }

    private fun rank(
        items: List<EngineResultItem>,
        query: String,
    ): List<AggregatedResult> {
        val nowMillis = System.currentTimeMillis()
        val buckets = LinkedHashMap<String, MutableBucket>()
        for (item in items) {
            val key = UrlNormalizer.normalize(item.url)
            val contribution = 1.0 / (rrfK + item.position)
            val bucket = buckets[key]
            if (bucket == null) {
                buckets[key] =
                    MutableBucket(
                        title = item.title,
                        // Surface a tracker-stripped URL so the clicked link drops utm_*/fbclid/etc;
                        // `key` above is still the lossy normalized form used only for dedup.
                        url = UrlNormalizer.stripTracking(item.url),
                        snippet = item.snippet,
                        engines = linkedSetOf(item.engineId),
                        score = contribution,
                        publishedMillis = publishedOf(item, nowMillis),
                    )
            } else {
                bucket.engines.add(item.engineId)
                bucket.score += contribution
                if (bucket.snippet.isBlank() && item.snippet.isNotBlank()) bucket.snippet = item.snippet
                // Keep the newest known date when several engines surface the same URL.
                val candidate = publishedOf(item, nowMillis)
                if (candidate != null && (bucket.publishedMillis == null || candidate > bucket.publishedMillis!!)) {
                    bucket.publishedMillis = candidate
                }
            }
        }
        // Fold a lexical query-match score into the RRF score so the final order leads with relevance
        // (does the result actually contain the query's content words, especially the title) and keeps
        // engine consensus as a strong secondary signal. Without this, near-tied RRF scores let an
        // irrelevant result one engine ranked highly sit among the top hits. The blend is demotion-only
        // and language-agnostic; the existing tie-breakers keep ordering deterministic. See Relevance.
        val terms = Relevance.contentTerms(query)
        return buckets.values
            .map { AggregatedResult(it.title, it.url, it.snippet, it.engines.toList(), it.score, it.publishedMillis) }
            .map {
                // Navigational promotion: when the squished query names this result's domain (query
                // "threejs" -> threejs.org), float it to the top past the demotion-only relevance
                // blend, so the official site is not buried under forum posts that merely contain it.
                val nav = Relevance.navigationalFactor(terms, DomainRanker.host(it.url) ?: "")
                it to
                    Relevance.blendedScore(
                        it.score,
                        Relevance.lexicalScore(it.title, it.snippet, terms),
                        Relevance.languageAffinity(query, it.title, it.snippet),
                    ) * nav
            }
            .sortedWith(
                compareByDescending<Pair<AggregatedResult, Double>> { it.second }
                    .thenBy { UrlNormalizer.normalize(it.first.url) }
                    .thenBy { it.first.engines.joinToString(",") },
            )
            .map { it.first }
    }

    /** A structured date from the engine wins; else parse snippet/title. Weak (bare-year) -> null. */
    private fun publishedOf(
        item: EngineResultItem,
        nowMillis: Long,
    ): Long? {
        item.publishedMillis?.let { return it }
        val parsed = SnippetDateParser.parse("${item.snippet} ${item.title}", nowMillis)
        return if (parsed == null || parsed.weak) null else parsed.epochMillis
    }

    private class MutableBucket(
        val title: String,
        val url: String,
        var snippet: String,
        val engines: LinkedHashSet<String>,
        var score: Double,
        var publishedMillis: Long?,
    )
}
