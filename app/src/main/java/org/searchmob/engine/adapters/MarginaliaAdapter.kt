package org.searchmob.engine.adapters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery

/**
 * Marginalia (independent indie-web index) via its free public JSON API
 * (`/public/search/{query}`). Response is `{results:[{url,title,description}]}`. No registration key.
 */
class MarginaliaAdapter(
    private val baseUrl: String = "https://api.marginalia-search.com",
) : HttpEngineAdapter() {
    override val id = "marginalia"
    override val displayName = "Marginalia"
    override val categories = setOf(SearchCategory.GENERAL)

    // Keyword index with no `site:`/`OR` syntax: it gets the operator-free query, and any site
    // constraint is enforced locally over the merged results.
    override val supportsSiteOperators = false

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url =
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("public")
                .addPathSegment("search")
                .addPathSegment(query.unscopedTerms)
                .build()
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
            if (url.isBlank()) return@mapIndexedNotNull null
            EngineResultItem(
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                url = url,
                snippet = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                engineId = id,
                position = index,
            )
        }
    }
}
