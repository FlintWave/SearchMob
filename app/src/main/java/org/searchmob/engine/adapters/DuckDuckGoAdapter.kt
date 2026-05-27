package org.searchmob.engine.adapters

import okhttp3.Request
import org.jsoup.Jsoup
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * DuckDuckGo via the no-JS HTML endpoint (`html.duckduckgo.com/html`). Web results are
 * `div.result.web-result` (ad results are `result--ad` and are excluded by the selector); each
 * result's link is a `uddg=`-encoded redirect that we decode back to the real destination URL.
 */
class DuckDuckGoAdapter(
    private val baseUrl: String = "https://html.duckduckgo.com",
) : HttpEngineAdapter() {
    override val id = "duckduckgo"
    override val displayName = "DuckDuckGo"
    override val categories = setOf(SearchCategory.GENERAL)

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url = "$baseUrl/html/?q=${URLEncoder.encode(query.terms, "UTF-8")}"
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val doc = Jsoup.parse(body)
        return doc.select("div.result.web-result").mapIndexedNotNull { index, element ->
            val anchor = element.selectFirst("a.result__a") ?: return@mapIndexedNotNull null
            val realUrl = decodeRedirect(anchor.attr("href")) ?: return@mapIndexedNotNull null
            val snippet = element.selectFirst(".result__snippet")?.text().orEmpty()
            EngineResultItem(
                title = anchor.text(),
                url = realUrl,
                snippet = snippet,
                engineId = id,
                position = index,
            )
        }
    }

    /** DuckDuckGo wraps result links as `//duckduckgo.com/l/?uddg=<encoded-url>&rut=...`. */
    private fun decodeRedirect(href: String): String? {
        val marker = href.indexOf("uddg=")
        if (marker < 0) return null
        val encoded = href.substring(marker + "uddg=".length).substringBefore("&")
        return try {
            URLDecoder.decode(encoded, "UTF-8").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
