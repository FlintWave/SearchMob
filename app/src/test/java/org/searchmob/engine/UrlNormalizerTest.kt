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

    @Test
    fun httpAndHttpsCollapseToSameKey() {
        // Engines disagree on the scheme for the same page; both must merge into one RRF bucket.
        assertEquals(
            UrlNormalizer.normalize("https://example.com/article"),
            UrlNormalizer.normalize("http://example.com/article"),
        )
    }

    @Test
    fun mobileSubdomainCollapsesToCanonicalHost() {
        assertEquals(
            UrlNormalizer.normalize("https://en.wikipedia.org/wiki/Privacy"),
            UrlNormalizer.normalize("https://en.m.wikipedia.org/wiki/Privacy"),
        )
        assertEquals(
            UrlNormalizer.normalize("https://example.com/page"),
            UrlNormalizer.normalize("https://m.example.com/page"),
        )
    }

    @Test
    fun stripTrackingRemovesTrackersButKeepsTheRestForDisplay() {
        // Host case, www, trailing slash, fragment, and the surviving param order are all kept.
        assertEquals(
            "https://WWW.Example.com/Some/Path/?id=42&q=hi#frag",
            UrlNormalizer.stripTracking(
                "https://WWW.Example.com/Some/Path/?id=42&utm_source=n&q=hi&fbclid=a#frag",
            ),
        )
    }

    @Test
    fun stripTrackingDropsQueryWhenOnlyTrackers() {
        assertEquals(
            "https://example.com/p",
            UrlNormalizer.stripTracking("https://example.com/p?utm_campaign=x&gclid=y"),
        )
    }

    @Test
    fun stripsTheFullTrackerSetSharedWithDesktop() {
        // The union of both apps' tracker keys (gclsrc/msclkid/mc_cid/_hsenc/_hsmi/ref_src/yclid added).
        val url =
            "https://example.com/p?keep=1&gclsrc=aw&msclkid=m&mc_cid=c&mc_eid=e" +
                "&_hsenc=h&_hsmi=i&igshid=g&ref=r&ref_src=s&yclid=y&dclid=d"
        assertEquals("https://example.com/p?keep=1", UrlNormalizer.stripTracking(url))
    }

    @Test
    fun stripTrackingLeavesCleanUrlsUntouched() {
        assertEquals(
            "https://example.com/p/?id=1",
            UrlNormalizer.stripTracking("https://example.com/p/?id=1"),
        )
    }
}
