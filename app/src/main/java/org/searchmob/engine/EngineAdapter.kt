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
    // Best-known publication time (epoch millis), or null when unknown (the common case for general
    // web results). Drives freshness sorting; an adapter may set a structured date, else the
    // aggregator parses it from the snippet/title.
    val publishedMillis: Long? = null,
)

/** Shared resources handed to an adapter for a single search. */
data class EngineContext(
    val httpClient: OkHttpClient,
    val apiKey: String? = null,
    val timeoutMs: Long = 5_000L,
    val politeness: Politeness? = null,
    // Per-engine language/region parameters for the active UI locale, or null for English / a locale
    // with no mapping (the engine then behaves region-neutrally, exactly as before). Only the engines
    // that document such a parameter (DuckDuckGo, Brave) read it.
    val languageRegion: LanguageRegion? = null,
)

/** Fail-soft result of one engine: items on success, or a reason on failure, never thrown to the aggregator. */
sealed interface EngineResult {
    /**
     * Items from one engine. [correction] is the engine's own spelling correction ("did you mean" /
     * "showing results for X") when it exposed one in this response, else null.
     */
    data class Success(
        val items: List<EngineResultItem>,
        val correction: String? = null,
    ) : EngineResult

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
