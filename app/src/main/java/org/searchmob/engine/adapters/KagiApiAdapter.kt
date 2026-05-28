package org.searchmob.engine.adapters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
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
 * Kagi Search API (bring-your-own key). Inactive until the user supplies a Kagi token. The response is
 * `{data:[{t,url,title,snippet}, ...]}` where `t == 0` marks a search result and other `t` values
 * (e.g. related searches) are ignored. Authenticated with `Authorization: Bot <token>`.
 */
class KagiApiAdapter(
    private val baseUrl: String = "https://kagi.com",
) : HttpEngineAdapter() {
    override val id = "kagi-api"
    override val displayName = "Kagi (API)"
    override val categories = setOf(SearchCategory.GENERAL)
    override val requiresApiKey = true

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url = "$baseUrl/api/v0/search?q=${URLEncoder.encode(query.terms, "UTF-8")}"
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bot ${ctx.apiKey.orEmpty()}")
            .get()
            .build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val data =
            Json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonArray
                ?: return emptyList()
        var position = 0
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            if (obj["t"]?.jsonPrimitive?.intOrNull != 0) return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
            if (url.isBlank()) return@mapNotNull null
            EngineResultItem(
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                url = url,
                snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty(),
                engineId = id,
                position = position++,
            )
        }
    }
}
