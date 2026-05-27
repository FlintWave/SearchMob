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

/** A merged, ranked result and the engines that contributed it. */
data class AggregatedResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engines: List<String>,
    val score: Double,
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
    ): List<AggregatedResult> {
        val semaphore = Semaphore(maxConcurrent)
        val perEngine: List<List<EngineResultItem>> =
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
                            (result as? EngineResult.Success)?.items ?: emptyList()
                        }
                    }.awaitAll()
            }
        return rank(perEngine.flatten())
    }

    private fun rank(items: List<EngineResultItem>): List<AggregatedResult> {
        val buckets = LinkedHashMap<String, MutableBucket>()
        for (item in items) {
            val key = UrlNormalizer.normalize(item.url)
            val contribution = 1.0 / (rrfK + item.position)
            val bucket = buckets[key]
            if (bucket == null) {
                buckets[key] =
                    MutableBucket(
                        title = item.title,
                        url = item.url,
                        snippet = item.snippet,
                        engines = linkedSetOf(item.engineId),
                        score = contribution,
                    )
            } else {
                bucket.engines.add(item.engineId)
                bucket.score += contribution
                if (bucket.snippet.isBlank() && item.snippet.isNotBlank()) bucket.snippet = item.snippet
            }
        }
        return buckets.values
            .map { AggregatedResult(it.title, it.url, it.snippet, it.engines.toList(), it.score) }
            .sortedWith(
                compareByDescending<AggregatedResult> { it.score }
                    .thenBy { UrlNormalizer.normalize(it.url) }
                    .thenBy { it.engines.joinToString(",") },
            )
    }

    private class MutableBucket(
        val title: String,
        val url: String,
        var snippet: String,
        val engines: LinkedHashSet<String>,
        var score: Double,
    )
}
