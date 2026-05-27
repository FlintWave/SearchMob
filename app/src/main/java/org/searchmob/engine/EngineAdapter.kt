package org.searchmob.engine

import okhttp3.OkHttpClient
import org.searchmob.engine.http.Politeness

/** Search category. Only general web search for now; images/news/etc. can be added later. */
enum class SearchCategory { GENERAL, }

/** A user query handed to engines. */
data class SearchQuery(
    val terms: String,
    val category: SearchCategory = SearchCategory.GENERAL,
)

/** A normalized result from one engine, with the rank (0-based position) it held in that engine's list. */
data class EngineResultItem(
    val title: String,
    val url: String,
    val snippet: String,
    val engineId: String,
    val position: Int,
)

/** Shared resources handed to an adapter for a single search. */
data class EngineContext(
    val httpClient: OkHttpClient,
    val apiKey: String? = null,
    val timeoutMs: Long = 5_000L,
    val politeness: Politeness? = null,
)

/** Fail-soft result of one engine: items on success, or a reason on failure — never thrown to the aggregator. */
sealed interface EngineResult {
    data class Success(val items: List<EngineResultItem>) : EngineResult

    data class Failure(val reason: String, val cause: Throwable? = null) : EngineResult
}

/**
 * A search engine source. Implementations MUST be fail-soft: catch their own errors and return
 * [EngineResult.Failure] rather than throwing, so one broken engine never fails the whole query.
 */
interface EngineAdapter {
    val id: String
    val displayName: String
    val categories: Set<SearchCategory>
    val requiresApiKey: Boolean get() = false

    /** Engine ids this adapter replaces when active (e.g. a keyed API superseding its free scraper). */
    val supersedes: Set<String> get() = emptySet()

    suspend fun search(
        query: SearchQuery,
        ctx: EngineContext,
    ): EngineResult
}
