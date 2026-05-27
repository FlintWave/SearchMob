package org.searchmob.engine.http

import okhttp3.Interceptor
import okhttp3.Response
import kotlin.random.Random

/**
 * Strips identifying request metadata and rotates the User-Agent per request, so upstream engines
 * see no cookies, no referrer, and no stable client / user / device / install identifier.
 */
class PrivacyInterceptor(private val rng: Random = Random.Default) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain.request().newBuilder()
                .removeHeader("Cookie")
                .removeHeader("Referer")
                .removeHeader("X-Requested-With")
                .removeHeader("X-Forwarded-For")
                .header("User-Agent", UserAgents.random(rng))
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        return chain.proceed(request)
    }
}
