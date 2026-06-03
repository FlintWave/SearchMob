package org.searchmob.update

import okhttp3.OkHttpClient
import java.io.File

/**
 * Prepares the one-click update: re-fetch the latest release (so the asset URLs and checksum are
 * fresh), pick the APK + SHA256SUMS, and download-and-verify. The outcome tells the caller whether to
 * launch the system installer, fall back to the release page (Linux-style multi-format / no usable
 * asset), or report a failure. Kept off the Android framework so it is unit-testable on the JVM.
 */
object UpdateFlow {
    sealed interface Result {
        /** A verified APK is ready to hand to the system package installer. */
        data class Installable(val file: File) : Result

        /** No in-app install path; open this release page in the browser instead. */
        data class OpenPage(val url: String) : Result

        /** The download or verification failed; show [message] and open [url]. */
        data class Failed(val message: String, val url: String) : Result
    }

    suspend fun prepare(
        client: OkHttpClient,
        cacheDir: File,
        fallbackUrl: String,
    ): Result {
        val info = UpdateChecker(client).fetchLatest() ?: return Result.OpenPage(fallbackUrl)
        val apk = info.apkAsset()
        val sums = info.checksumsAsset()
        if (apk == null || sums == null) return Result.OpenPage(info.releaseUrl)
        return try {
            Result.Installable(UpdateInstaller.downloadAndVerify(client, apk, sums, File(cacheDir, "updates")))
        } catch (e: UpdateDownloadException) {
            Result.Failed(e.message ?: "Download failed.", info.releaseUrl)
        }
    }
}
