package org.searchmob.engine.adapters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery
import java.net.URLEncoder

/**
 * Wikipedia (English) via the documented REST search API
 * (`/w/rest.php/v1/search/page`). JSON, no API key.
 */
class WikipediaAdapter(
    private val baseUrl: String = "https://en.wikipedia.org",
) : HttpEngineAdapter() {
    override val id = "wikipedia"
    override val displayName = "Wikipedia"
    override val categories = setOf(SearchCategory.GENERAL)

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url = "$baseUrl/w/rest.php/v1/search/page?q=${enc(query.terms)}&limit=10"
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val pages = Json.parseToJsonElement(body).jsonObject["pages"]?.jsonArray ?: return emptyList()
        return pages.mapIndexed { index, element ->
            val obj = element.jsonObject
            val key = obj["key"]?.jsonPrimitive?.content.orEmpty()
            val title = obj["title"]?.jsonPrimitive?.content.orEmpty()
            val excerpt = obj["excerpt"]?.jsonPrimitive?.content.orEmpty().replace(HTML_TAG, "")
            EngineResultItem(
                title = title,
                url = "$baseUrl/wiki/${key.ifEmpty { title.replace(' ', '_') }}",
                snippet = excerpt,
                engineId = id,
                position = index,
            )
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        val HTML_TAG = Regex("<[^>]*>")
    }
}
