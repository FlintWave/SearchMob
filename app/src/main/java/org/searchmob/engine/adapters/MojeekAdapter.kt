package org.searchmob.engine.adapters

import okhttp3.Request
import org.jsoup.Jsoup
import org.searchmob.engine.EngineContext
import org.searchmob.engine.EngineResultItem
import org.searchmob.engine.HttpEngineAdapter
import org.searchmob.engine.SearchCategory
import org.searchmob.engine.SearchQuery
import java.net.URLEncoder

/**
 * Mojeek (independent index) via its HTML results page. Results are `ul.results-standard > li`, each
 * with a direct (non-redirected) link in `h2 a.title` and a snippet in `p.s`. No API key.
 */
class MojeekAdapter(
    private val baseUrl: String = "https://www.mojeek.com",
) : HttpEngineAdapter() {
    override val id = "mojeek"
    override val displayName = "Mojeek"
    override val categories = setOf(SearchCategory.GENERAL)

    override fun buildRequest(
        query: SearchQuery,
        ctx: EngineContext,
    ): Request {
        val url = "$baseUrl/search?q=${URLEncoder.encode(query.terms, "UTF-8")}"
        return Request.Builder().url(url).get().build()
    }

    override fun parse(body: String): List<EngineResultItem> {
        val doc = Jsoup.parse(body)
        return doc.select("ul.results-standard > li").mapIndexedNotNull { index, li ->
            val anchor = li.selectFirst("h2 a.title") ?: return@mapIndexedNotNull null
            val url = anchor.attr("href")
            if (url.isBlank()) return@mapIndexedNotNull null
            EngineResultItem(
                title = anchor.text(),
                url = url,
                snippet = li.selectFirst("p.s")?.text().orEmpty(),
                engineId = id,
                position = index,
            )
        }
    }

    /**
     * Mojeek shows "Did you mean: <correction>" above results. Selectors are verified against live
     * HTML; if they miss, the on-device corrector still covers the case.
     */
    override fun parseCorrection(body: String): String? {
        val doc = Jsoup.parse(body)
        return firstText(
            doc,
            "p.did-you-mean a",
            ".did-you-mean a",
            "a.spell",
        )
    }
}
