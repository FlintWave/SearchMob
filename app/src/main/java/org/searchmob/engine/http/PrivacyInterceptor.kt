package org.searchmob.engine.http

import okhttp3.Interceptor
import okhttp3.Response
import kotlin.random.Random

/**
 * Strips identifying request metadata and rotates the User-Agent per request, so upstream engines
 * see no cookies, no referrer, and no stable client / user / device / install identifier.
 *
 * A caller that already set a User-Agent keeps it: the engine adapters pick one UA per logical
 * search and re-send it on their 429/503 retries, because switching identities between an initial
 * request and its retry seconds later from the same IP is itself a bot signature.
 */
class PrivacyInterceptor(private val rng: Random = Random.Default) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder =
            chain.request().newBuilder()
                .removeHeader("Cookie")
                .removeHeader("X-Requested-With")
                .removeHeader("X-Forwarded-For")
                .header("Accept-Language", "en-US,en;q=0.9")
        // Referer must never reach an upstream; keep stripping it unconditionally.
        builder.removeHeader("Referer")
        if (chain.request().header("User-Agent") == null) {
            builder.header("User-Agent", UserAgents.random(rng))
        }
        return chain.proceed(builder.build())
    }
}
