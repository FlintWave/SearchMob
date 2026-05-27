package org.searchmob.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.searchmob.ui.setup.setupUrls

class SetupUrlsTest {
    @Test
    fun visitUrl_isLoopbackHomeOnBoundPort() {
        assertEquals("http://127.0.0.1:8080/", setupUrls(8080).visitUrl)
    }

    @Test
    fun searchTemplate_keepsLiteralPlaceholder() {
        // %s MUST stay literal — browsers substitute the query for it; it is not URL-encoded.
        assertEquals("http://127.0.0.1:8080/search?q=%s", setupUrls(8080).searchTemplateUrl)
    }

    @Test
    fun urls_reflectTheExactBoundPort() {
        val urls = setupUrls(54321)
        assertEquals("http://127.0.0.1:54321/", urls.visitUrl)
        assertEquals("http://127.0.0.1:54321/search?q=%s", urls.searchTemplateUrl)
    }
}
