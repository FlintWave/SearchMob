package org.searchmob.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Upper bound on a downloaded APK. SearchMob's APK is a few MiB; this bounds disk/memory use. */
const val MAX_APK_BYTES = 256L * 1024 * 1024

/** Upper bound on the SHA256SUMS body: one short line per asset. */
private const val MAX_SUMS_BYTES = 64L * 1024L

/** A download or integrity check failed. Carries a user-facing message. */
class UpdateDownloadException(message: String) : Exception(message)

/**
 * Downloads the release APK and verifies it against the published `SHA256SUMS`, then hands it to the
 * system package installer. This is a fetch-and-hand-off, not a silent install: the OS always shows
 * its install confirmation (the app is not a device owner), which is the right place for the user to
 * consent. The download streams through the shared privacy-proxy OkHttp client (no cookies, rotated
 * User-Agent), is size-capped, and computes the SHA-256 while streaming so a tampered byte fails the
 * check before the file is ever opened.
 */
object UpdateInstaller {
    /**
     * Parses `SHA256SUMS` content into `{assetName: lowercaseHexDigest}`. Accepts the standard
     * `sha256sum` format `<64-hex><space><space-or-asterisk><name>`; malformed lines are skipped and a
     * leading `*` (binary-mode marker) on the name is stripped.
     */
    fun parseSha256Sums(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            val digest = parts[0].lowercase()
            val name = parts[1].trim().removePrefix("*")
            if (digest.length == 64 && digest.all { it in "0123456789abcdef" }) {
                out[name] = digest
            }
        }
        return out
    }

    /** The expected SHA-256 for [assetName] from SHA256SUMS content, or null when absent. */
    fun expectedDigest(
        sumsText: String,
        assetName: String,
    ): String? = parseSha256Sums(sumsText)[assetName]

    /**
     * Streams [apk] into [destDir], verifying its SHA-256 against [sums]. Returns the saved file.
     * Throws [UpdateDownloadException] on any transport failure, an oversized body, a missing checksum
     * entry, or a digest mismatch. The file is written to a temp name and only moved into place once
     * the checksum verifies, so a partial or tampered download never lands at the final path.
     */
    suspend fun downloadAndVerify(
        client: OkHttpClient,
        apk: ReleaseAsset,
        sums: ReleaseAsset,
        destDir: File,
        maxBytes: Long = MAX_APK_BYTES,
    ): File =
        withContext(Dispatchers.IO) {
            val expected =
                expectedDigest(fetchText(client, sums.downloadUrl, MAX_SUMS_BYTES), apk.name)
                    ?: throw UpdateDownloadException(
                        "No published checksum for ${apk.name}; refusing to install an unverified download.",
                    )

            destDir.mkdirs()
            val finalFile = File(destDir, apk.name)
            val tmp = File.createTempFile("download-", ".apk.part", destDir)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val request = Request.Builder().url(apk.downloadUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw UpdateDownloadException("Download failed: HTTP ${response.code}")
                    }
                    val source =
                        response.body?.byteStream()
                            ?: throw UpdateDownloadException("Download failed: empty response.")
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = source.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) {
                                throw UpdateDownloadException("The download exceeded the expected size; aborting.")
                            }
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    throw UpdateDownloadException(
                        "The downloaded file's checksum did not match the published value; discarding it.",
                    )
                }
                if (finalFile.exists()) finalFile.delete()
                if (!tmp.renameTo(finalFile)) {
                    throw UpdateDownloadException("Could not finalize the downloaded file.")
                }
                finalFile
            } catch (e: UpdateDownloadException) {
                tmp.delete()
                throw e
            } catch (e: Exception) {
                tmp.delete()
                throw UpdateDownloadException("Download failed: ${e.message}")
            }
        }

    /**
     * Hands [apk] to the system [PackageInstaller]. The OS shows its install confirmation UI (routed
     * via [PackageInstallReceiver] when it reports STATUS_PENDING_USER_ACTION). Requires the
     * `REQUEST_INSTALL_PACKAGES` permission; the user grants "install unknown apps" if not already.
     */
    fun installApk(
        context: Context,
        apk: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("searchmob.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, PackageInstallReceiver::class.java)
            val pending =
                android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            session.commit(pending.intentSender)
        }
    }

    /** Reads at most [maxBytes] of a URL's body as UTF-8 text, or throws [UpdateDownloadException]. */
    private fun fetchText(
        client: OkHttpClient,
        url: String,
        maxBytes: Long,
    ): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateDownloadException("Could not fetch the checksum file: HTTP ${response.code}")
            }
            val body = response.body ?: throw UpdateDownloadException("Could not fetch the checksum file.")
            val source = body.source()
            if (source.request(maxBytes + 1)) {
                throw UpdateDownloadException("Checksum file was unexpectedly large; aborting.")
            }
            return source.buffer.readString(body.contentType()?.charset() ?: Charsets.UTF_8)
        }
    }
}
