package org.searchmob.engine.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer

/** A knowledge-panel-style Wikipedia summary for a query, shown above the results. */
data class WikiSummary(
    val title: String,
    val description: String,
    val extract: String,
    val url: String,
    val thumbnailUrl: String? = null,
)

/**
 * Contextual Wikipedia summary: the lead paragraph of a related article for entity-like queries.
 *
 * Two-step, fail-soft flow mirroring the desktop port: resolve a candidate title via the Action API
 * OpenSearch endpoint, apply a relevance gate, then fetch that title's REST summary and reject
 * disambiguation pages / empty extracts. Any error, timeout, low-confidence match, or long /
 * navigational query yields null and no box.
 */
class WikiSummaryProvider(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://en.wikipedia.org",
) {
    suspend fun fetch(query: String): WikiSummary? {
        if (!isEntityLikeQuery(query)) return null
        val title = resolveTitle(query) ?: return null
        if (!isConfidentMatch(query, title)) return null
        return fetchSummary(title)
    }

    private suspend fun resolveTitle(query: String): String? {
        val url =
            "$baseUrl/w/api.php?action=opensearch&format=json&search=${enc(query)}" +
                "&limit=3&namespace=0&redirects=resolve"
        val body = get(url) ?: return null
        return runCatching {
            Json.parseToJsonElement(body).jsonArray[1].jsonArray
                .firstOrNull()
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun fetchSummary(title: String): WikiSummary? {
        val url = "$baseUrl/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}"
        val body = get(url) ?: return null
        return runCatching {
            val obj = Json.parseToJsonElement(body).jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "disambiguation") return null
            val extract = obj["extract"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
            val pageUrl =
                obj["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")
                    ?.jsonPrimitive?.content.orEmpty()
            val thumb = obj["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content
            WikiSummary(
                title = obj["title"]?.jsonPrimitive?.content ?: title,
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                extract = truncate(extract),
                url = pageUrl,
                thumbnailUrl = thumb,
            )
        }.getOrNull()
    }

    private suspend fun get(url: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else readBoundedBody(resp)
                }
            }.getOrNull()
        }

    /**
     * Reads at most [MAX_RESPONSE_BYTES][org.searchmob.engine.MAX_RESPONSE_BYTES] of the body,
     * mirroring `HttpEngineAdapter`: this is the one fetch path that previously buffered an unbounded
     * body, and a summary endpoint has no business returning megabytes.
     */
    private fun readBoundedBody(response: okhttp3.Response): String? {
        val body = response.body ?: return null
        val source = body.source()
        if (source.request(org.searchmob.engine.MAX_RESPONSE_BYTES + 1)) return null
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val MAX_QUERY_TOKENS = 6
        private const val MAX_QUERY_CHARS = 60
        private const val MAX_EXTRACT_CHARS = 320
        private const val MIN_JACCARD = 0.6
        private val PUNCT = Regex("[^\\p{L}\\p{N}\\s]")
        private val TRAILING_PAREN = Regex("\\s*\\([^)]*\\)\\s*$")

        fun isEntityLikeQuery(query: String): Boolean {
            val q = query.trim()
            if (q.isEmpty() || q.length > MAX_QUERY_CHARS) return false
            if (q.split(Regex("\\s+")).size > MAX_QUERY_TOKENS) return false
            val navigational =
                "://" in q || q.startsWith("http") || q.startsWith("www.") ||
                    (!q.contains(' ') && q.contains('.'))
            return !navigational
        }

        fun isConfidentMatch(
            query: String,
            title: String,
        ): Boolean {
            val qn = normalize(query)
            val tn = normalize(title)
            if (qn.isEmpty() || tn.isEmpty()) return false
            if (qn == tn) return true
            val qt = tokens(query)
            val tt = tokens(title)
            if (qt.isEmpty() || tt.isEmpty()) return false
            if (tt.containsAll(qt) || qt.containsAll(tt)) return true
            val overlap = qt.intersect(tt).size.toDouble() / qt.union(tt).size
            return overlap >= MIN_JACCARD
        }

        private fun normalize(text: String): String {
            val noParen = TRAILING_PAREN.replace(text, "")
            val decomposed =
                Normalizer.normalize(noParen, Normalizer.Form.NFKD)
                    .replace(Regex("\\p{Mn}+"), "")
            return PUNCT.replace(decomposed.lowercase(), " ").replace(Regex("\\s+"), " ").trim()
        }

        private fun tokens(text: String): Set<String> = normalize(text).split(' ').filter { it.isNotEmpty() }.toSet()

        private fun truncate(extract: String): String {
            val e = extract.trim()
            if (e.length <= MAX_EXTRACT_CHARS) return e
            val head = e.substring(0, MAX_EXTRACT_CHARS)
            val cut = maxOf(head.lastIndexOf(". "), head.lastIndexOf("! "), head.lastIndexOf("? "))
            if (cut >= MAX_EXTRACT_CHARS / 2) return head.substring(0, cut + 1)
            val space = head.lastIndexOf(' ')
            return (if (space > 0) head.substring(0, space) else head).trimEnd() + "…"
        }
    }
}
