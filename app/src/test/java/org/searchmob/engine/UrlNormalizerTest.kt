package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.searchmob.engine.aggregate.UrlNormalizer

class UrlNormalizerTest {
    @Test
    fun stripsWwwTrailingSlashAndLowercasesHost() {
        assertEquals(
            "https://example.com/page",
            UrlNormalizer.normalize("https://WWW.Example.com/page/"),
        )
    }

    @Test
    fun dropsTrackingParamsAndSortsRemaining() {
        assertEquals(
            "https://example.com/p?a=1&b=2",
            UrlNormalizer.normalize("https://example.com/p?b=2&utm_source=x&a=1&fbclid=y"),
        )
    }

    @Test
    fun equivalentUrlsCollapseToSameKey() {
        assertEquals(
            UrlNormalizer.normalize("http://example.com/x"),
            UrlNormalizer.normalize("http://www.example.com/x/"),
        )
    }
}
