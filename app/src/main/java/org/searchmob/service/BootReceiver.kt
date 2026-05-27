package org.searchmob.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the always-on service after device boot. Because [SearchMobService] is a `specialUse`
 * foreground service, starting it from `BOOT_COMPLETED` is permitted on Android 15 (API 35).
 * Keep work here minimal — just hand off to the service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ServiceController.start(context.applicationContext)
        }
    }
}
