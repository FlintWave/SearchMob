package org.searchmob.engine.http

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the single shared OkHttp client used for all engine fan-out: no cookie storage/sending
 * ([CookieJar.NO_COOKIES]), the [PrivacyInterceptor], and bounded timeouts. Adapters receive this
 * client via [org.searchmob.engine.EngineContext] and cannot bypass it.
 */
object HttpClientFactory {
    fun create(
        connectTimeoutMs: Long = 5_000,
        readTimeoutMs: Long = 5_000,
        callTimeoutMs: Long = 8_000,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(true)
            // Refuse https->http (cleartext) downgrade redirects; an upstream cannot silently drop TLS.
            .followSslRedirects(false)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            // A whole-call deadline. The per-read timeout above resets on every packet, so a server
            // that trickles bytes (or a slow 2 MB body) could otherwise hold a search open for minutes:
            // the aggregator's coroutine timeout cannot cancel a blocking OkHttp call, but this can.
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(PrivacyInterceptor())
            .build()
}
