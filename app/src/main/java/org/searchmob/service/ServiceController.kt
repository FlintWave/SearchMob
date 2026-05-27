package org.searchmob.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the always-on [SearchMobService] from the app and the boot receiver. */
object ServiceController {
    /** Start (or re-affirm) the foreground service. Idempotent, safe to call on every app open. */
    fun start(context: Context) {
        val intent = Intent(context, SearchMobService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stop the running service. Delivered via [Context.startService] (not foreground-start) because
     * the service is already running, so no new foreground promotion is required.
     */
    fun stop(context: Context) {
        context.startService(SearchMobService.stopIntent(context))
    }
}
