package org.searchmob.engine

import okhttp3.OkHttpClient
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.server.SearchResult
import org.searchmob.server.SearchResultProvider

/**
 * Bridges the metasearch engine to the server's [SearchResultProvider] contract, so the engine drops
 * in behind the unchanged HTTP routes (replacing the phase-3 stub).
 */
class MetaSearchResultProvider(
    private val registry: EngineRegistry,
    private val aggregator: Aggregator = Aggregator(),
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
) : SearchResultProvider {
    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val aggregated = aggregator.aggregate(SearchQuery(query), registry.activeEngines(httpClient))
        return aggregated.map { result ->
            SearchResult(
                title = result.title,
                url = result.url,
                snippet = result.snippet,
                engine = result.engines.joinToString(","),
            )
        }
    }
}
