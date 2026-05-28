package org.searchmob

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.searchmob.data.StorageProvider
import org.searchmob.engine.correct.AssetDictionaryLoader
import org.searchmob.engine.correct.OnDeviceSpellCorrector
import org.searchmob.engine.correct.SpellCorrector

/**
 * Process-wide owner of the encrypted storage layer and the spell corrector. Both the UI
 * ([MainActivity]/[org.searchmob.ui.AppDependencies]) and the foreground service
 * ([org.searchmob.service.SearchMobService]) read the SAME [StorageProvider] and [spellCorrector] from
 * here, so the encrypted history DB, the encrypted preferences (including BYO API keys), and the
 * correction dictionary are single shared instances rather than per-component copies.
 *
 * On startup it unwraps the data key: in the default Keystore mode this unlocks the vault silently; a
 * zero-knowledge (passphrase) vault would stay locked until the user enters the passphrase, but that
 * capture/unlock flow is not wired into the UI yet, so today [StorageBootstrap.bootstrap] always
 * unlocks here. The lock controller is registered against the process lifecycle so the DEK is evicted
 * on background/inactivity once zero-knowledge mode lands (a no-op in Keystore mode).
 */
class SearchMobApplication : Application() {
    val storage: StorageProvider by lazy { StorageProvider.create(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dictionaryLoader by lazy {
        AssetDictionaryLoader(
            context = this,
            // Past queries from the encrypted history enrich the dictionary; a locked/disabled history
            // simply yields no extra terms.
            historyTerms = {
                runCatching { storage.history.list(System.currentTimeMillis()).map { it.query } }
                    .getOrDefault(emptyList())
            },
        )
    }

    /** Offline corrector shared by the UI and the service; suggests nothing until the dictionary loads. */
    val spellCorrector: SpellCorrector by lazy {
        OnDeviceSpellCorrector(dictionary = { dictionaryLoader.current() })
    }

    override fun onCreate() {
        super.onCreate()
        // Synchronous on purpose: the unlocked DEK must be available before any encrypted read/write.
        // It is one metadata-file read plus one Keystore unwrap, well under the startup budget.
        storage.bootstrap.bootstrap()
        ProcessLifecycleOwner.get().lifecycle.addObserver(storage.lockController)
        // Build the correction dictionary off the main thread; until it finishes, suggest() returns null.
        appScope.launch { runCatching { dictionaryLoader.load() } }
    }
}
