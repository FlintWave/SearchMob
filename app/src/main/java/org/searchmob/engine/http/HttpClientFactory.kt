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
    ): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(true)
            // Refuse https->http (cleartext) downgrade redirects; an upstream cannot silently drop TLS.
            .followSslRedirects(false)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(PrivacyInterceptor())
            .build()
}
