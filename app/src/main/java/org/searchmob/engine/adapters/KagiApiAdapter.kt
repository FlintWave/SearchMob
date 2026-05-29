package org.searchmob.engine.adapters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery

/**
 * Kagi Search API (bring-your-own key), v1. Inactive until the user supplies a Kagi API key (from
 * kagi.com/api/keys). Per Kagi's OpenAPI spec it is a POST to `https://kagi.com/api/v1/search` with a
 * JSON body `{"query": "..."}` and HTTP Bearer auth. Web results are under `data.search[]`, each with
 * `url`, `title`, and `snippet`.
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
        val payload =
            buildJsonObject { put("query", query.terms) }
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url("$baseUrl/api/v1/search")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${ctx.apiKey.orEmpty()}")
            .post(payload)
            .build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val search =
            Json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonObject
                ?.get("search")?.jsonArray
                ?: return emptyList()
        var position = 0
        return search.mapNotNull { element ->
            val obj = element.jsonObject
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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
