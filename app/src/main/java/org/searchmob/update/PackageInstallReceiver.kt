package org.searchmob.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives [PackageInstaller] session status callbacks for an in-app update install. The only status
 * that needs handling is STATUS_PENDING_USER_ACTION: the system hands back an Intent that launches its
 * own install-confirmation UI (the app is not a device owner, so the user always confirms). Other
 * statuses (success/failure) are terminal and need no action here; the user sees the system's result.
 */
class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            // The receiver runs outside an Activity context, so a new task is required to launch the UI.
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        }
    }
}
