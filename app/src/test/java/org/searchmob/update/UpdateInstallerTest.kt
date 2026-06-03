package org.searchmob.update

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateInstallerTest {
    private fun client() = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tempDir(): File =
        File.createTempFile("updtest", "").let {
            it.delete()
            it.mkdirs()
            it
        }

    // ----- parseSha256Sums -----

    @Test
    fun parse_readsTwoSpaceAndBinaryMarkerAndSkipsJunk() {
        val d = "a".repeat(64)
        val text = "$d  plain.apk\n$d *binary.apk\n\n# comment\nbad line\n"
        assertEquals(mapOf("plain.apk" to d, "binary.apk" to d), UpdateInstaller.parseSha256Sums(text))
    }

    @Test
    fun parse_lowercasesAndRejectsNonHexOrShort() {
        assertEquals(mapOf("x.apk" to "a".repeat(64)), UpdateInstaller.parseSha256Sums("${"A".repeat(64)}  x.apk"))
        assertTrue(UpdateInstaller.parseSha256Sums("zzzz  bad.apk\nABCDEF  short.apk").isEmpty())
    }

    @Test
    fun expectedDigest_nullWhenAbsent() {
        assertNull(UpdateInstaller.expectedDigest("", "missing.apk"))
    }

    // ----- downloadAndVerify -----

    private fun apkAsset(server: MockWebServer) = ReleaseAsset("searchmob.apk", server.url("/searchmob.apk").toString())

    private fun sumsAsset(server: MockWebServer) =
        ReleaseAsset(SHA256SUMS_ASSET_NAME, server.url("/SHA256SUMS").toString())

    @Test
    fun downloadAndVerify_writesFileWhenChecksumMatches() =
        runTest {
            val payload = ("installer-bytes").repeat(100).toByteArray()
            val server = MockWebServer()
            // Order: the verifier fetches SHA256SUMS first, then streams the APK.
            server.enqueue(MockResponse().setBody("${sha256Hex(payload)}  searchmob.apk\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            server.start()
            try {
                val dir = tempDir()
                val file = UpdateInstaller.downloadAndVerify(client(), apkAsset(server), sumsAsset(server), dir)
                assertEquals("searchmob.apk", file.name)
                assertArrayEquals(payload, file.readBytes())
                // Only the final file is left; no .part temp remains.
                assertEquals(listOf("searchmob.apk"), dir.listFiles()!!.map { it.name })
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun downloadAndVerify_rejectsChecksumMismatch() =
        runTest {
            val payload = "the-real-bytes".toByteArray()
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("${sha256Hex("other".toByteArray())}  searchmob.apk\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            server.start()
            try {
                val dir = tempDir()
                var failed = false
                try {
                    UpdateInstaller.downloadAndVerify(client(), apkAsset(server), sumsAsset(server), dir)
                } catch (e: UpdateDownloadException) {
                    failed = true
                    assertTrue(e.message!!.contains("checksum did not match"))
                }
                assertTrue("expected a checksum mismatch failure", failed)
                // A mismatched download must not be left behind under any name.
                assertFalse(File(dir, "searchmob.apk").exists())
                assertEquals(emptyList<String>(), dir.listFiles()!!.map { it.name })
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun downloadAndVerify_failsWhenNoChecksumEntry() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("${"a".repeat(64)}  other.apk\n"))
            server.enqueue(MockResponse().setBody("x"))
            server.start()
            try {
                var msg: String? = null
                try {
                    UpdateInstaller.downloadAndVerify(client(), apkAsset(server), sumsAsset(server), tempDir())
                } catch (e: UpdateDownloadException) {
                    msg = e.message
                }
                assertTrue(msg!!.contains("No published checksum"))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun downloadAndVerify_enforcesSizeCap() =
        runTest {
            val payload = "x".repeat(5000).toByteArray()
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("${sha256Hex(payload)}  searchmob.apk\n"))
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            server.start()
            try {
                val dir = tempDir()
                var msg: String? = null
                try {
                    UpdateInstaller.downloadAndVerify(
                        client(),
                        apkAsset(server),
                        sumsAsset(server),
                        dir,
                        maxBytes = 1000,
                    )
                } catch (e: UpdateDownloadException) {
                    msg = e.message
                }
                assertTrue(msg!!.contains("exceeded the expected size"))
                assertEquals(emptyList<String>(), dir.listFiles()!!.map { it.name })
            } finally {
                server.shutdown()
            }
        }
}
