package org.searchmob.engine

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.DuckDuckGoAdapter

class RegionTest {
    @Test
    fun languageRegionForKnownLocales() {
        val es = languageRegionFor("es")!!
        assertEquals("es-es", es.ddgKl)
        assertEquals("es", es.braveSearchLang)
        assertEquals("ES", es.braveCountry)
        assertEquals("es-ES", es.braveUiLang)

        // Locales DuckDuckGo has no region for leave ddgKl empty but keep Brave params.
        val bn = languageRegionFor("bn")!!
        assertEquals("", bn.ddgKl)
        assertEquals("bn", bn.braveSearchLang)
    }

    @Test
    fun languageRegionForNormalizesTheTag() {
        assertEquals(languageRegionFor("ar"), languageRegionFor("ar-EG"))
        assertEquals(languageRegionFor("zh"), languageRegionFor("zh-Hans-CN"))
    }

    @Test
    fun languageRegionForEnglishAndUnmappedIsNull() {
        assertNull(languageRegionFor("en"))
        assertNull(languageRegionFor("de"))
        assertNull(languageRegionFor(""))
        assertNull(languageRegionFor(null))
    }

    /** Run [block] against a [MockWebServer] that returns [body], yielding the path the adapter hit. */
    private fun capturePath(
        body: String,
        block: suspend (baseUrl: String) -> Unit,
    ): String =
        MockWebServer().run {
            enqueue(MockResponse().setBody(body))
            start()
            try {
                runTest { block(url("/").toString().removeSuffix("/")) }
                takeRequest().path!!
            } finally {
                shutdown()
            }
        }

    @Test
    fun duckDuckGoAddsKlForANonEnglishLocaleAndNoneForEnglish() {
        val adapter: (String) -> DuckDuckGoAdapter = { DuckDuckGoAdapter(baseUrl = it) }
        val client = OkHttpClient()

        val es = capturePath("<html></html>") { base ->
            adapter(base).search(SearchQuery("cats"), EngineContext(client, languageRegion = languageRegionFor("es")))
        }
        assertTrue("expected kl param, got $es", es.contains("kl=es-es"))

        val english = capturePath("<html></html>") { base ->
            adapter(base).search(SearchQuery("cats"), EngineContext(client, languageRegion = null))
        }
        assertFalse("English should be region-neutral", english.contains("kl="))

        // A locale with no DuckDuckGo region (Bengali) must not append an empty kl.
        val bn = capturePath("<html></html>") { base ->
            adapter(base).search(SearchQuery("cats"), EngineContext(client, languageRegion = languageRegionFor("bn")))
        }
        assertFalse(bn.contains("kl="))
    }

    @Test
    fun braveAddsLanguageRegionParamsForANonEnglishLocaleAndNoneForEnglish() {
        val adapter: (String) -> BraveApiAdapter = { BraveApiAdapter(baseUrl = it) }
        val client = OkHttpClient()

        val ar = capturePath("{\"web\":{\"results\":[]}}") { base ->
            adapter(base).search(SearchQuery("cats"), EngineContext(client, apiKey = "k", languageRegion = languageRegionFor("ar")))
        }
        assertTrue(ar.contains("country=SA"))
        assertTrue(ar.contains("search_lang=ar"))
        assertTrue(ar.contains("ui_lang=ar-SA"))

        val english = capturePath("{\"web\":{\"results\":[]}}") { base ->
            adapter(base).search(SearchQuery("cats"), EngineContext(client, apiKey = "k", languageRegion = null))
        }
        assertFalse(english.contains("country="))
        assertFalse(english.contains("search_lang="))
        assertFalse(english.contains("ui_lang="))
    }
}
