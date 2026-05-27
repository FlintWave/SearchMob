package org.searchmob.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Base for adapters that fetch one HTTP response and parse it. Handles the IO dispatch, the call,
 * HTTP error mapping, and fail-soft exception handling, so concrete adapters only build the request
 * and parse the body. The shared [EngineContext.httpClient] (privacy proxy) is always used.
 */
abstract class HttpEngineAdapter : EngineAdapter {
    /** Build the upstream request for [query]. May read [EngineContext.apiKey] for key-gated engines. */
    protected abstract fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request

    /** Parse a successful response body into normalized items. Public so it can be fixture-tested. */
    abstract fun parse(body: String): List<EngineResultItem>

    override suspend fun search(
        query: SearchQuery,
        ctx: EngineContext,
    ): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                ctx.httpClient.newCall(buildRequest(query, ctx)).execute().use { response ->
                    if (!response.isSuccessful) {
                        EngineResult.Failure("HTTP ${response.code}")
                    } else {
                        EngineResult.Success(parse(response.body?.string().orEmpty()))
                    }
                }
            } catch (e: Exception) {
                EngineResult.Failure(e.message ?: "error", e)
            }
        }
}
