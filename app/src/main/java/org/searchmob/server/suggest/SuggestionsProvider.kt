package org.searchmob.server.suggest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.searchmob.data.history.HistoryStore
import org.searchmob.engine.MAX_RESPONSE_BYTES
import java.net.URLEncoder

/**
 * A source of autocomplete suggestions for a partial query. Implementations MUST be fail-soft: any
 * error, timeout, or unavailable backing source returns an empty list rather than throwing, so the
 * suggestions endpoint never hangs or fails while the user is typing.
 */
interface SuggestionsProvider {
    /** Up to [limit] suggestions for [query], best-first. Never throws; returns empty on any problem. */
    suspend fun suggest(
        query: String,
        limit: Int,
    ): List<String>
}

/** Default provider that never offers suggestions; used where no real source is wired (e.g. tests). */
object NoSuggestionsProvider : SuggestionsProvider {
    override suspend fun suggest(
        query: String,
        limit: Int,
    ): List<String> = emptyList()
}

/**
 * Local suggestions from the opt-in, encrypted search history: distinct past queries that
 * prefix-match the term (case-insensitive), most-recent first. Always available (no network) and
 * returns an empty list when history is disabled, locked, or empty (it never throws).
 */
class HistorySuggestionsProvider(
    private val historyStore: HistoryStore,
    private val nowMsProvider: () -> Long = System::currentTimeMillis,
) : SuggestionsProvider {
    override suspend fun suggest(
        query: String,
        limit: Int,
    ): List<String> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return runCatching { historyStore.suggest(query, limit, nowMsProvider()) }.getOrDefault(emptyList())
    }
}

/**
 * Opt-in upstream autocomplete from DuckDuckGo's suggestion endpoint
 * (`https://ac.duckduckgo.com/ac/?q=<term>&type=list`), fetched through the shared privacy-proxy
 * OkHttp client (no cookies, stripped headers, rotated User-Agent). Uses a SHORT timeout and a bounded
 * body read, and returns an empty list on ANY failure or timeout so typing never blocks.
 *
 * The response is the OpenSearch-style two-element array `["<term>", ["s1", "s2", ...]]`; only the
 * suggestion strings are returned.
 */
class UpstreamSuggestionsProvider(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://ac.duckduckgo.com",
) : SuggestionsProvider {
    override suspend fun suggest(
        query: String,
        limit: Int,
    ): List<String> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/ac/?q=${URLEncoder.encode(query, "UTF-8")}&type=list"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = readBoundedBody(response) ?: return@use emptyList()
                    parse(body).take(limit)
                }
            }.getOrDefault(emptyList())
        }
    }

    /** Parse the DDG ac list shape `["term", ["s1", "s2", ...]]`. Public so it can be fixture-tested. */
    fun parse(body: String): List<String> =
        runCatching {
            val outer = Json.parseToJsonElement(body).jsonArray
            // Element 0 is the echoed term; element 1 is the array of suggestion strings.
            val suggestions = outer.getOrNull(1)?.jsonArray ?: return emptyList()
            suggestions.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())

    /**
     * Reads at most [MAX_RESPONSE_BYTES] from the response body, mirroring `HttpEngineAdapter`: an
     * oversized body is rejected (returns null) rather than fully buffered.
     */
    private fun readBoundedBody(response: okhttp3.Response): String? {
        val body = response.body ?: return ""
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1)) return null
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }
}

/**
 * Merges local and (opt-in) upstream suggestions. Local history is queried unless [localEnabled]
 * returns false (used to suppress it while network mode is on, so the owner's history is not served
 * as autocomplete to other devices on the network). The upstream provider is queried ONLY when
 * [upstreamEnabled] returns true (the default-off opt-in preference). Results are merged local-first,
 * de-duplicated case-insensitively (keeping the first/local casing), and capped to [maxTotal].
 */
class CompositeSuggestionsProvider(
    private val history: SuggestionsProvider,
    private val upstream: SuggestionsProvider,
    private val upstreamEnabled: () -> Boolean,
    private val localEnabled: () -> Boolean = { true },
    private val maxTotal: Int = 8,
) : SuggestionsProvider {
    override suspend fun suggest(
        query: String,
        limit: Int,
    ): List<String> {
        if (query.isBlank()) return emptyList()
        val cap = minOf(limit, maxTotal)
        if (cap <= 0) return emptyList()

        // Local history is suppressed in network mode so it is not exposed to other network clients.
        val local = if (localEnabled()) history.suggest(query, cap) else emptyList()
        // Ask upstream only when the opt-in preference is on; otherwise it is never contacted.
        val remote = if (upstreamEnabled()) upstream.suggest(query, cap) else emptyList()

        val merged = LinkedHashSet<String>()
        // Local first; case-insensitive dedup keeps the local casing for a collision.
        (local + remote).forEach { suggestion ->
            if (suggestion.isNotBlank() && merged.none { it.equals(suggestion, ignoreCase = true) }) {
                merged.add(suggestion)
            }
        }
        return merged.take(cap)
    }
}
