package org.searchmob.engine

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.http.PrivacyInterceptor
import org.searchmob.engine.http.UserAgents
import kotlin.random.Random

class PrivacyInterceptorTest {
    @Test
    fun stripsCookieAndRefererAndSetsUaFromPool() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val client = OkHttpClient.Builder().addInterceptor(PrivacyInterceptor(Random(1))).build()
            val request =
                Request.Builder()
                    .url(server.url("/"))
                    .header("Cookie", "session=secret")
                    .header("Referer", "https://leak.example/private")
                    .build()
            client.newCall(request).execute().close()

            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("Cookie"))
            assertNull(recorded.getHeader("Referer"))
            assertTrue(UserAgents.POOL.contains(recorded.getHeader("User-Agent")))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun userAgentRotatesAcrossRequests() {
        val server = MockWebServer()
        repeat(10) { server.enqueue(MockResponse().setBody("ok")) }
        server.start()
        try {
            val client = OkHttpClient.Builder().addInterceptor(PrivacyInterceptor(Random(42))).build()
            val seen =
                (1..10).map {
                    client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
                    server.takeRequest().getHeader("User-Agent")
                }.toSet()
            assertTrue("expected UA rotation, saw $seen", seen.size > 1)
            assertTrue(seen.all { UserAgents.POOL.contains(it) })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun setCookieIsNotReplayed() {
        val server = MockWebServer()
        server.enqueue(MockResponse().addHeader("Set-Cookie", "sid=secret").setBody("1"))
        server.enqueue(MockResponse().setBody("2"))
        server.start()
        try {
            val client =
                OkHttpClient.Builder()
                    .cookieJar(CookieJar.NO_COOKIES)
                    .addInterceptor(PrivacyInterceptor(Random(1)))
                    .build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            server.takeRequest()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }
}
