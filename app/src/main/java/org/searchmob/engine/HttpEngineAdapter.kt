package org.searchmob.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.searchmob.engine.http.Politeness

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
                val request = buildRequest(query, ctx)
                ctx.politeness?.acquire(request.url.host)
                executeWithBackoff(ctx, request)
            } catch (e: Exception) {
                EngineResult.Failure(e.message ?: "error", e)
            }
        }

    private suspend fun executeWithBackoff(
        ctx: EngineContext,
        request: Request,
    ): EngineResult {
        var attempt = 0
        while (true) {
            val response = ctx.httpClient.newCall(request).execute()
            val code = response.code
            if (code == 429 || code == 503) {
                response.close()
                val backoff = Politeness.backoffMs(code, attempt) ?: return EngineResult.Failure("HTTP $code")
                delay(backoff)
                attempt++
                continue
            }
            return response.use {
                if (!it.isSuccessful) {
                    EngineResult.Failure("HTTP $code")
                } else {
                    EngineResult.Success(parse(it.body?.string().orEmpty()))
                }
            }
        }
    }
}
