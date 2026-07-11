package org.searchmob.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.searchmob.engine.http.Politeness
import org.searchmob.engine.http.UserAgents

/** Hard cap on how many bytes of an upstream body are read; a larger body is treated as a failure. */
const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

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

    /**
     * Extract the engine's own spelling correction ("did you mean" / "showing results for X") from the
     * response, or null if it offers none. Default: no correction. Public so it can be fixture-tested.
     */
    open fun parseCorrection(body: String): String? = null

    override suspend fun search(
        query: SearchQuery,
        ctx: EngineContext,
    ): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                // One User-Agent per logical search, reused verbatim by any 429/503 retry below: a
                // retry that shows up seconds later from the same IP with a different identity is a
                // classic bot signature. The PrivacyInterceptor respects a pre-set UA.
                val request =
                    buildRequest(query, ctx).newBuilder()
                        .header("User-Agent", UserAgents.random())
                        .build()
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
                    when (val body = readBoundedBody(it)) {
                        null -> EngineResult.Failure("response body over $MAX_RESPONSE_BYTES bytes")
                        else -> EngineResult.Success(parse(body), parseCorrection(body))
                    }
                }
            }
        }
    }

    /**
     * Reads at most [MAX_RESPONSE_BYTES] from the response body and returns it as a String, or null if
     * the upstream sent more than the cap. The source is read incrementally (no full pre-buffering), so
     * an oversized or unbounded body is rejected without being fully materialized in memory.
     */
    private fun readBoundedBody(response: okhttp3.Response): String? {
        val body = response.body ?: return ""
        val source = body.source()
        // request(n) returns true only if at least n bytes are available; if MAX_RESPONSE_BYTES + 1
        // bytes can be buffered the body is over the cap, so we reject it as a failed fetch.
        if (source.request(MAX_RESPONSE_BYTES + 1)) {
            return null
        }
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }
}
