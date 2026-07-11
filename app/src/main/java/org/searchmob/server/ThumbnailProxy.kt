package org.searchmob.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/** A proxied image body plus its (sanitized, image-only) content type. */
data class ProxiedImage(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Server-side fetcher for the Wikipedia summary card's thumbnail, so the BROWSER never contacts
 * Wikimedia directly. Without this, the served page embedded an `upload.wikimedia.org` image URL and
 * the user's browser fetched it with the user's real IP - the image filename names the searched
 * entity, which is exactly the query-subject-to-third-party leak the metasearch proxy exists to
 * prevent. Now the app fetches it through the same privacy-proxied OkHttp stack (rotated UA, no
 * cookies) and re-serves it from the loopback origin.
 *
 * Strictly scoped against SSRF: only https URLs on the Wikimedia upload host are ever fetched, only
 * image content types are re-served, and the body is size-capped. Anything else yields null and the
 * card simply renders without a picture.
 */
object ThumbnailProxy {
    /** The only host the proxy will fetch from - where Wikipedia REST summaries host thumbnails. */
    private const val ALLOWED_HOST = "upload.wikimedia.org"

    /** Cap well below the engine-body cap: a summary thumbnail is tens of kilobytes. */
    private const val MAX_IMAGE_BYTES = 1L * 1024 * 1024

    /** True when [url] is an https URL this proxy is willing to fetch. */
    fun isAllowed(url: String): Boolean =
        runCatching {
            val uri = URI(url.trim())
            uri.scheme?.lowercase() == "https" && uri.host?.lowercase() == ALLOWED_HOST
        }.getOrDefault(false)

    /** Fetch [url] (must pass [isAllowed]) and return the image, or null on any failure. */
    suspend fun fetch(
        client: OkHttpClient,
        url: String,
    ): ProxiedImage? {
        if (!isAllowed(url)) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val type = response.body?.contentType()
                    if (type == null || type.type != "image") return@use null
                    val source = response.body?.source() ?: return@use null
                    if (source.request(MAX_IMAGE_BYTES + 1)) return@use null
                    ProxiedImage(source.buffer.readByteArray(), "${type.type}/${type.subtype}")
                }
            }.getOrNull()
        }
    }
}
