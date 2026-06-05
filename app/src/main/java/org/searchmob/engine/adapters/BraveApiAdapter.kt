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
 * Brave Search API (bring-your-own key). Independent index with a zero-data-retention policy.
 * Inactive until the user supplies a key. Response is `{web:{results:[{title,url,description}]}}`.
 */
class BraveApiAdapter(
    private val baseUrl: String = "https://api.search.brave.com",
) : HttpEngineAdapter() {
    override val id = "brave-api"
    override val displayName = "Brave (API)"
    override val categories = setOf(SearchCategory.GENERAL)
    override val requiresApiKey = true

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        // Tailor results to the active UI locale via Brave's documented language/region params;
        // English / unmapped locales add none and the request stays region-neutral as before.
        val region = ctx.languageRegion
        val params =
            buildString {
                append("?q=${URLEncoder.encode(query.terms, "UTF-8")}")
                region?.braveCountry?.takeIf { it.isNotBlank() }?.let { append("&country=$it") }
                region?.braveSearchLang?.takeIf { it.isNotBlank() }?.let { append("&search_lang=$it") }
                region?.braveUiLang?.takeIf { it.isNotBlank() }?.let { append("&ui_lang=$it") }
            }
        val url = "$baseUrl/res/v1/web/search$params"
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", ctx.apiKey.orEmpty())
            .get()
            .build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val results =
            Json.parseToJsonElement(body)
                .jsonObject["web"]?.jsonObject
                ?.get("results")?.jsonArray
                ?: return emptyList()
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
