package org.searchmob.engine.http

import kotlin.random.Random

/**
 * Curated User-Agent rotation pool. One is chosen per request so upstream engines never see a
 * stable client identifier from this device.
 */
object UserAgents {
    @Suppress("ktlint:standard:max-line-length") // realistic UA strings are necessarily long
    val POOL: List<String> =
        listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        )

    fun random(rng: Random = Random.Default): String = POOL[rng.nextInt(POOL.size)]
}
