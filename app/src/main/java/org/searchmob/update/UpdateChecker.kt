package org.searchmob.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.searchmob.engine.MAX_RESPONSE_BYTES

/** Default GitHub Releases endpoint for the project's latest release. */
const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/FlintWave/SearchMob/releases/latest"

/** Fallback releases page opened when a specific release `html_url` is unavailable. */
const val RELEASES_PAGE_URL = "https://github.com/FlintWave/SearchMob/releases/latest"

/** The integrity-anchor asset published alongside the APK (see the release workflow). */
const val SHA256SUMS_ASSET_NAME = "SHA256SUMS"

/** One published asset attached to a GitHub release (the signed APK, or the SHA256SUMS file). */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0,
)

/**
 * Result of a successful update check: the latest published release as a comparable version code, its
 * human-readable name, the page to open, and its published assets. [isNewerThan] tells the caller
 * whether to prompt; [apkAsset]/[checksumsAsset] back the in-app download-and-install path.
 */
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseUrl: String,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    /** An update is available only when the latest version code is strictly greater than [current]. */
    fun isNewerThan(current: Int): Boolean = latestVersionCode > current

    /** The signed APK asset to download for an in-app install, or null when the release has none. */
    fun apkAsset(): ReleaseAsset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    /** The SHA256SUMS asset used to verify a downloaded APK's integrity, or null when absent. */
    fun checksumsAsset(): ReleaseAsset? = assets.firstOrNull { it.name == SHA256SUMS_ASSET_NAME }
}

/**
 * Parses SearchMob's `YY.MM.VV` date version into the same monotonic version code the build derives
 * (`YY*10000 + MM*100 + VV`). Kept as pure logic over the tag string so it is unit-testable on the JVM.
 */
object VersionTag {
    /**
     * Converts a release tag like "v26.05.01" (or "26.05.01") into a version code, or null when the
     * tag is malformed (missing parts, non-numeric segments). Mirrors `app/build.gradle.kts`.
     */
    fun toVersionCode(tag: String?): Int? {
        if (tag.isNullOrBlank()) return null
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".")
        if (parts.size < 3) return null
        val yy = parts[0].toIntOrNull() ?: return null
        val mm = parts[1].toIntOrNull() ?: return null
        // Only the leading numeric run of the patch segment counts (e.g. ignore a "-rc1" suffix).
        val vv = parts[2].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        if (yy < 0 || mm < 0 || vv < 0) return null
        return (yy * 10000) + (mm * 100) + vv
    }
}

/**
 * Checks GitHub Releases for a newer SearchMob build through the shared privacy-proxy OkHttp client
 * (no cookies, stripped headers, rotated User-Agent which also satisfies GitHub's required User-Agent
 * header). Uses a SHORT timeout and a bounded body read, and is strictly fail-soft: any HTTP error,
 * timeout, malformed JSON, or malformed tag returns null rather than throwing, so a launch-time check
 * never blocks or crashes the app.
 *
 * [baseUrl] is the full release endpoint and is injectable so tests can point it at a MockWebServer.
 */
class UpdateChecker(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = LATEST_RELEASE_API_URL,
) {
    /**
     * Fetches and parses the latest release. Returns the parsed [UpdateInfo] or null on any failure.
     * The caller decides whether it is newer than the running build via [UpdateInfo.isNewerThan].
     */
    suspend fun fetchLatest(): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(baseUrl)
                        .header("Accept", "application/vnd.github+json")
                        .get()
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = readBoundedBody(response) ?: return@use null
                    parse(body)
                }
            }.getOrNull()
        }

    /**
     * Parses the GitHub release JSON, reading `tag_name` and `html_url`. Returns null when the tag is
     * missing or malformed. Public so it can be fixture-tested without a server. Falls back to the
     * releases page URL when `html_url` is absent.
     */
    fun parse(body: String): UpdateInfo? =
        runCatching {
            val obj = Json.parseToJsonElement(body).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content
            val versionCode = VersionTag.toVersionCode(tag) ?: return null
            val htmlUrl = obj["html_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL
            UpdateInfo(
                latestVersionCode = versionCode,
                latestVersionName = tag!!.trim().removePrefix("v").removePrefix("V"),
                releaseUrl = htmlUrl,
                assets = parseAssets(obj["assets"]),
            )
        }.getOrNull()

    /**
     * Parses the release `assets` array into [ReleaseAsset]s, skipping malformed entries (missing a
     * usable name or `browser_download_url`). Fail-soft like the rest of the parser.
     */
    private fun parseAssets(element: kotlinx.serialization.json.JsonElement?): List<ReleaseAsset> {
        val array = runCatching { element?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { entry ->
            val obj = runCatching { entry.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url =
                obj["browser_download_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            val size = runCatching { obj["size"]?.jsonPrimitive?.int?.toLong() }.getOrNull() ?: 0L
            ReleaseAsset(name = name, downloadUrl = url, size = size)
        }
    }

    /**
     * Reads at most [MAX_RESPONSE_BYTES] from the response body, mirroring `HttpEngineAdapter`: an
     * oversized body is rejected (returns null) rather than fully buffered.
     */
    private fun readBoundedBody(response: okhttp3.Response): String? {
        val body = response.body ?: return ""
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1)) return null
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }
}
