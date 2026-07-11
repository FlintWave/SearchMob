package org.searchmob.engine.aggregate

import java.net.URI

/**
 * URL helpers for the aggregator. [normalize] builds a lossy dedup key (lowercase scheme/host, drop
 * a leading `www.`, strip a trailing slash, remove tracking params, sort the rest). [stripTracking]
 * removes the same tracking params but otherwise keeps the URL faithful, for the link shown/clicked.
 */
object UrlNormalizer {
    private val trackingPrefixes = listOf("utm_")

    // Kept in sync with the desktop app's normalize.py so both strip the same trackers.
    private val trackingKeys =
        setOf(
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid",
            "mc_cid", "mc_eid", "_hsenc", "_hsmi", "igshid",
            "ref", "ref_src", "yclid",
        )

    private fun isTracking(param: String): Boolean {
        val key = param.substringBefore("=").lowercase()
        return trackingKeys.contains(key) || trackingPrefixes.any { key.startsWith(it) }
    }

    /**
     * Returns [rawUrl] with known tracking params removed, preserving everything else for display:
     * scheme/host case, path, trailing slash, fragment, and the order of the surviving params. This
     * is the link the user actually clicks, so unlike [normalize] (a lossy dedup key) it is kept
     * faithful apart from the trackers. Falls back to the trimmed input if it does not parse.
     */
    fun stripTracking(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            val uri = URI(trimmed)
            val query = uri.rawQuery ?: return trimmed
            val kept =
                query
                    .split("&")
                    .filter { it.isNotBlank() }
                    .filterNot { isTracking(it) }
            buildString {
                append(trimmed.substringBefore("?"))
                if (kept.isNotEmpty()) append("?").append(kept.joinToString("&"))
                uri.rawFragment?.let { append("#").append(it) }
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            val uri = URI(trimmed)
            var host = uri.host?.lowercase()?.removePrefix("www.") ?: return trimmed.lowercase()
            // Fold the mobile-site subdomain into the canonical host so `en.m.wikipedia.org` and
            // `en.wikipedia.org` (or `m.example.com` / `example.com`) merge instead of splitting the
            // engine-consensus signal across two entries. Dedup key only; the displayed URL is kept.
            host = host.removePrefix("m.").replace(".m.", ".")
            // The dedup key treats http and https as the same page: engines disagree on the scheme
            // for the same URL often enough that keeping both wastes a slot and splits the RRF score.
            val rawScheme = (uri.scheme ?: "https").lowercase()
            val scheme = if (rawScheme == "http") "https" else rawScheme
            var path = uri.path ?: ""
            if (path.length > 1 && path.endsWith("/")) path = path.dropLast(1)
            val query =
                uri.query
                    ?.split("&")
                    ?.filter { it.isNotBlank() }
                    ?.filterNot { isTracking(it) }
                    ?.sorted()
                    ?.joinToString("&")
                    ?.takeIf { it.isNotEmpty() }
            buildString {
                append(scheme).append("://").append(host).append(path)
                if (query != null) append("?").append(query)
            }
        } catch (_: Exception) {
            trimmed.lowercase()
        }
    }
}
