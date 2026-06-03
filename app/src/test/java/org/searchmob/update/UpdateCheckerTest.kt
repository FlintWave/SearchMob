package org.searchmob.update

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCheckerTest {
    private fun proxyClient() = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()

    private fun releaseJson(
        tag: String,
        htmlUrl: String = "https://github.com/FlintWave/SearchMob/releases/tag/$tag",
    ) = """{"tag_name":"$tag","html_url":"$htmlUrl","name":"$tag"}"""

    // ----- VersionTag.toVersionCode -----

    @Test
    fun versionTag_parsesWithLeadingV() {
        assertEquals(260501, VersionTag.toVersionCode("v26.05.01"))
    }

    @Test
    fun versionTag_parsesWithoutLeadingV() {
        assertEquals(260600, VersionTag.toVersionCode("26.06.00"))
    }

    @Test
    fun versionTag_ignoresPrereleaseSuffixOnPatch() {
        assertEquals(270100, VersionTag.toVersionCode("v27.01.00-rc1"))
    }

    @Test
    fun versionTag_returnsNullForMalformedTags() {
        assertNull(VersionTag.toVersionCode(null))
        assertNull(VersionTag.toVersionCode(""))
        assertNull(VersionTag.toVersionCode("notaversion"))
        assertNull(VersionTag.toVersionCode("v26.05")) // too few parts
        assertNull(VersionTag.toVersionCode("vXX.05.01")) // non-numeric
    }

    // ----- UpdateInfo.isNewerThan (version-code comparison) -----

    @Test
    fun isNewerThan_trueWhenLatestIsGreater() {
        val info = UpdateInfo(260502, "26.05.02", RELEASES_PAGE_URL)
        assertTrue(info.isNewerThan(260501))
    }

    @Test
    fun isNewerThan_falseWhenEqual() {
        val info = UpdateInfo(260501, "26.05.01", RELEASES_PAGE_URL)
        assertFalse(info.isNewerThan(260501))
    }

    @Test
    fun isNewerThan_falseWhenOlder() {
        val info = UpdateInfo(260500, "26.05.00", RELEASES_PAGE_URL)
        assertFalse(info.isNewerThan(260501))
    }

    // ----- parse -----

    @Test
    fun parse_readsTagAndHtmlUrl() {
        val checker = UpdateChecker(proxyClient())
        val info = checker.parse(releaseJson("v26.06.00"))
        assertEquals(260600, info!!.latestVersionCode)
        assertEquals("26.06.00", info.latestVersionName)
        assertEquals("https://github.com/FlintWave/SearchMob/releases/tag/v26.06.00", info.releaseUrl)
    }

    @Test
    fun parse_fallsBackToReleasesPageWhenHtmlUrlMissing() {
        val checker = UpdateChecker(proxyClient())
        val info = checker.parse("""{"tag_name":"v26.06.00"}""")
        assertEquals(RELEASES_PAGE_URL, info!!.releaseUrl)
    }

    @Test
    fun parse_returnsNullForMalformedTag() {
        val checker = UpdateChecker(proxyClient())
        assertNull(checker.parse("""{"tag_name":"not-a-version","html_url":"x"}"""))
    }

    @Test
    fun parse_returnsNullForMalformedJson() {
        val checker = UpdateChecker(proxyClient())
        assertNull(checker.parse("this is not json"))
    }

    // ----- assets parsing + selection -----

    private val releaseWithAssets =
        """
        {"tag_name":"v26.07.00","html_url":"https://example.test/r/v26.07.00","assets":[
          {"name":"searchmob-26.07.00.apk","browser_download_url":"https://example.test/dl/app.apk","size":12345},
          {"name":"SHA256SUMS","browser_download_url":"https://example.test/dl/SHA256SUMS"},
          {"name":"broken-no-url"},
          "not-a-dict"
        ]}
        """.trimIndent()

    @Test
    fun parse_readsAssetsAndSkipsMalformedEntries() {
        val info = UpdateChecker(proxyClient()).parse(releaseWithAssets)!!
        assertEquals(listOf("searchmob-26.07.00.apk", "SHA256SUMS"), info.assets.map { it.name })
        assertEquals(12345L, info.assets[0].size)
        assertEquals(0L, info.assets[1].size)
    }

    @Test
    fun apkAndChecksumsAssetSelection() {
        val info = UpdateChecker(proxyClient()).parse(releaseWithAssets)!!
        assertEquals("searchmob-26.07.00.apk", info.apkAsset()!!.name)
        assertEquals("SHA256SUMS", info.checksumsAsset()!!.name)
    }

    @Test
    fun apkAsset_nullWhenAbsent() {
        val info = UpdateChecker(proxyClient()).parse(releaseJson("v26.07.00"))!!
        assertNull(info.apkAsset())
        assertNull(info.checksumsAsset())
    }

    // ----- fetchLatest (fail-soft over MockWebServer) -----

    @Test
    fun fetchLatest_returnsInfoForNewerRelease() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody(releaseJson("v26.06.00")))
            server.start()
            try {
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                val info = checker.fetchLatest()
                assertEquals(260600, info!!.latestVersionCode)
                assertTrue(info.isNewerThan(260501))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun fetchLatest_returnsNullOnHttpError() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            try {
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                assertNull(checker.fetchLatest())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun fetchLatest_returnsNullOnMalformedJson() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("definitely not json"))
            server.start()
            try {
                val checker = UpdateChecker(proxyClient(), baseUrl = server.url("/latest").toString())
                assertNull(checker.fetchLatest())
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun fetchLatest_returnsNullOnTimeout() =
        runTest {
            val server = MockWebServer()
            // Hang the connection so the short read timeout fires; must fail soft, not throw.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            try {
                val client =
                    OkHttpClient.Builder()
                        .cookieJar(CookieJar.NO_COOKIES)
                        .readTimeout(200, TimeUnit.MILLISECONDS)
                        .build()
                val checker = UpdateChecker(client, baseUrl = server.url("/latest").toString())
                assertNull(checker.fetchLatest())
            } finally {
                server.shutdown()
            }
        }
}
