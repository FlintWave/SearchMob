package org.searchmob

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import org.searchmob.data.StorageProvider

/**
 * Process-wide owner of the encrypted storage layer. Both the UI ([MainActivity]/[org.searchmob.ui.AppDependencies])
 * and the foreground service ([org.searchmob.service.SearchMobService]) read the SAME [StorageProvider]
 * from here, so the encrypted history DB and the encrypted preferences (including BYO API keys) are a
 * single shared instance rather than per-component copies.
 *
 * On startup it unwraps the data key: in the default Keystore mode this unlocks the vault silently; a
 * zero-knowledge (passphrase) vault would stay locked until the user enters the passphrase, but that
 * capture/unlock flow is not wired into the UI yet, so today [StorageBootstrap.bootstrap] always
 * unlocks here. The lock controller is registered against the process lifecycle so the DEK is evicted
 * on background/inactivity once zero-knowledge mode lands (a no-op in Keystore mode).
 */
class SearchMobApplication : Application() {
    val storage: StorageProvider by lazy { StorageProvider.create(this) }

    override fun onCreate() {
        super.onCreate()
        // Synchronous on purpose: the unlocked DEK must be available before any encrypted read/write.
        // It is one metadata-file read plus one Keystore unwrap, well under the startup budget.
        storage.bootstrap.bootstrap()
        ProcessLifecycleOwner.get().lifecycle.addObserver(storage.lockController)
    }
}
