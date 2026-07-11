package org.searchmob.engine.adapters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery
import java.net.URLEncoder

/**
 * Wikipedia (English) via the OpenSearch JSON endpoint (`action=opensearch`), which returns a
 * four-element array `[query, titles, snippets, urls]` matched against article titles. This is
 * deliberately conservative: it contributes an article only when the query actually names one, so a
 * multi-word, non-entity query adds nothing instead of polluting the results with full-text matches
 * on individual words (unrelated encyclopedia articles that merely share one word with the query).
 * Mirrors the desktop `fetch_wikipedia`. JSON, no API key.
 */
class WikipediaAdapter(
    private val baseUrl: String = "https://en.wikipedia.org",
) : HttpEngineAdapter() {
    override val id = "wikipedia"
    override val displayName = "Wikipedia"
    override val categories = setOf(SearchCategory.GENERAL)

    // Title index: a `(site:... OR ...)` clause would be matched as literal title text and kill every
    // hit, so this engine receives the operator-free query and site: is enforced locally instead.
    override val supportsSiteOperators = false

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url =
            "$baseUrl/w/api.php?action=opensearch&format=json&search=${enc(query.unscopedTerms)}&limit=$LIMIT"
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val arr = Json.parseToJsonElement(body).jsonArray
        // [query, titles[], snippets[], urls[]] — anything shorter is an unexpected/empty shape.
        if (arr.size < 4) return emptyList()
        val titles = arr[1].jsonArray
        val snippets = arr[2].jsonArray
        val urls = arr[3].jsonArray
        return urls.mapIndexedNotNull { index, urlElement ->
            val url =
                urlElement.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
            EngineResultItem(
                title = titles.getOrNull(index)?.jsonPrimitive?.contentOrNull.orEmpty(),
                url = url,
                snippet = snippets.getOrNull(index)?.jsonPrimitive?.contentOrNull.orEmpty(),
                engineId = id,
                position = index,
            )
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val LIMIT = 10
    }
}
