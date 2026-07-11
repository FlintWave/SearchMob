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
 * Mwmbl (community-curated index) via its public JSON API. Each result's `title` and `extract` are
 * arrays of `{value, is_bold}` fragments which are concatenated into plain text. No API key.
 */
class MwmblAdapter(
    private val baseUrl: String = "https://api.mwmbl.org",
) : HttpEngineAdapter() {
    override val id = "mwmbl"
    override val displayName = "Mwmbl"
    override val categories = setOf(SearchCategory.GENERAL)

    // Keyword index with no `site:`/`OR` syntax: it gets the operator-free query, and any site
    // constraint is enforced locally over the merged results.
    override val supportsSiteOperators = false

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url = "$baseUrl/search/?s=${URLEncoder.encode(query.unscopedTerms, "UTF-8")}"
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val array = Json.parseToJsonElement(body).jsonArray
        return array.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
            if (url.isBlank()) return@mapIndexedNotNull null
            EngineResultItem(
                title = joinFragments(obj, "title"),
                url = url,
                snippet = joinFragments(obj, "extract"),
                engineId = id,
                position = index,
            )
        }
    }

    private fun joinFragments(
        obj: kotlinx.serialization.json.JsonObject,
        field: String,
    ): String =
        obj[field]?.jsonArray
            ?.joinToString("") { it.jsonObject["value"]?.jsonPrimitive?.content.orEmpty() }
            .orEmpty()
}
