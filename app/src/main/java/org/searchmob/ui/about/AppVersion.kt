package org.searchmob.ui.about

import android.content.Context
import android.content.pm.PackageManager

/**
 * Resolves the human-readable app version from the installed package, never a hard-coded literal.
 * Kept as pure logic over a [versionName] string so the formatting can be unit-tested on the JVM.
 */
object AppVersion {
    /** Fallback shown when the platform cannot report a version name. */
    const val UNKNOWN = "unknown"

    /** Formats a raw [PackageManager] versionName into the display string (e.g. "1.2.3"). */
    fun format(versionName: String?): String = versionName?.takeIf { it.isNotBlank() } ?: UNKNOWN

    /** Reads the current package's versionName via [PackageManager] and formats it for display. */
    fun of(context: Context): String {
        val name =
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull()
        return format(name)
    }
}
