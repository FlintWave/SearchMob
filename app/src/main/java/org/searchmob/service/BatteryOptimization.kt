package org.searchmob.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption: detection and a user-initiated request.
 *
 * The exemption is never auto-granted. [requestExemptionIntent] is launched ONLY in response to an
 * explicit user action; otherwise OEM/Doze battery management may kill the always-on service.
 */
object BatteryOptimization {
    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Intent the UI fires on explicit user action to request the exemption. */
    fun requestExemptionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
}
