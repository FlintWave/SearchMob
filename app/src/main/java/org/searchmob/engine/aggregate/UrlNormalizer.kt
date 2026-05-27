package org.searchmob.engine.aggregate

import java.net.URI

/**
 * Normalizes a URL for dedup: lowercase scheme/host, drop a leading `www.`, strip a trailing slash,
 * and remove common tracking query parameters (`utm_*`, `fbclid`, `gclid`, ...). Remaining query
 * params are sorted so equivalent URLs collapse deterministically.
 */
object UrlNormalizer {
    private val trackingPrefixes = listOf("utm_")
    private val trackingKeys = setOf("fbclid", "gclid", "gclsrc", "dclid", "msclkid", "mc_eid", "igshid", "ref")

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return trimmed.lowercase()
            val scheme = (uri.scheme ?: "https").lowercase()
            var path = uri.path ?: ""
            if (path.length > 1 && path.endsWith("/")) path = path.dropLast(1)
            val query =
                uri.query
                    ?.split("&")
                    ?.filter { it.isNotBlank() }
                    ?.filterNot { param ->
                        val key = param.substringBefore("=").lowercase()
                        trackingKeys.contains(key) || trackingPrefixes.any { key.startsWith(it) }
                    }
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
