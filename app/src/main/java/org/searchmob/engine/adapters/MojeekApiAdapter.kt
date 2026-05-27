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
 * Mojeek Search API (bring-your-own key). When active it supersedes the free Mojeek HTML scraper so
 * the same upstream is not queried twice. Response is `{response:{results:[{title,url,desc}]}}`.
 */
class MojeekApiAdapter(
    private val baseUrl: String = "https://api.mojeek.com",
) : HttpEngineAdapter() {
    override val id = "mojeek-api"
    override val displayName = "Mojeek (API)"
    override val categories = setOf(SearchCategory.GENERAL)
    override val requiresApiKey = true
    override val supersedes = setOf("mojeek")

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val q = URLEncoder.encode(query.terms, "UTF-8")
        val key = URLEncoder.encode(ctx.apiKey.orEmpty(), "UTF-8")
        return Request.Builder()
            .url("$baseUrl/search?q=$q&api_key=$key&fmt=json")
            .get()
            .build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val results =
            Json.parseToJsonElement(body)
                .jsonObject["response"]?.jsonObject
                ?.get("results")?.jsonArray
                ?: return emptyList()
        return results.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
            if (url.isBlank()) return@mapIndexedNotNull null
            EngineResultItem(
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                url = url,
                snippet = obj["desc"]?.jsonPrimitive?.content.orEmpty(),
                engineId = id,
                position = index,
            )
        }
    }
}
