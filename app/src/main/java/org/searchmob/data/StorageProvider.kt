package org.searchmob.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import org.searchmob.data.crypto.KeystoreDekWrapper
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.history.SqlCipherHistoryStore
import org.searchmob.data.prefs.EncryptedDataStorePreferences
import org.searchmob.data.prefs.EncryptedPreferencesCodec
import org.searchmob.data.prefs.EncryptedPreferencesSerializer
import org.searchmob.data.prefs.EngineConfigPreferences
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import java.io.File

/**
 * Minimal dependency provider that wires the Android storage layer together: Keystore-wrapped DEK
 * bootstrap, an encrypted DataStore for preferences, the SQLCipher history store, the engine-config
 * bridge, and the lock/eviction controller. A later DI framework (or the UI phase) can replace this;
 * it intentionally does no Android lifecycle registration itself (the caller registers
 * [lockController] against `ProcessLifecycleOwner`).
 *
 * Call [bootstrap] once at startup. In Keystore mode the vault unlocks immediately; in zero-knowledge
 * mode it stays locked until [StorageBootstrap.unlockWithPassphrase].
 */
class StorageProvider private constructor(
    private val appContext: Context,
    val bootstrap: StorageBootstrap,
    val history: HistoryStore,
    val preferences: PreferencesStore,
    val engineConfig: EngineConfigPreferences,
    val dataStore: DataStore<Preferences>,
) {
    /** The lock/eviction state machine; register against `ProcessLifecycleOwner.get().lifecycle`. */
    val lockController: StorageLockController by lazy {
        StorageLockController(
            vault = bootstrap.vault(),
            history = history,
            modeProvider = { bootstrap.mode },
        )
    }

    companion object {
        fun create(context: Context): StorageProvider {
            val appContext = context.applicationContext
            val metadataStore =
                BootstrapMetadataStore(File(appContext.filesDir, BootstrapMetadataStore.FILE_NAME))
            val keystoreWrapper = KeystoreDekWrapper(appContext.packageManager)
            val bootstrap =
                StorageBootstrap(
                    metadataStore = metadataStore,
                    keystoreWrapper = keystoreWrapper,
                    securityLevelProvider = { keystoreWrapper.securityLevel.name },
                    keyDeleter = { keystoreWrapper.deleteKey() },
                )

            // DataStore payload is encrypted with the DEK via the codec; the DEK is read lazily so the
            // serializer fails cleanly while locked rather than capturing a key.
            val codec = EncryptedPreferencesCodec(bootstrap.dekProvider())
            val dataStore: DataStore<Preferences> =
                DataStoreFactory.create(
                    serializer = EncryptedPreferencesSerializer(codec),
                    produceFile = { File(appContext.filesDir, PREFS_FILE_NAME) },
                )
            val preferences = EncryptedDataStorePreferences(dataStore)
            val engineConfig = EngineConfigPreferences(preferences)

            val history =
                SqlCipherHistoryStore(
                    context = appContext,
                    dekProvider = bootstrap.dekProvider(),
                )

            return StorageProvider(
                appContext = appContext,
                bootstrap = bootstrap,
                history = history,
                preferences = preferences,
                engineConfig = engineConfig,
                dataStore = dataStore,
            )
        }

        private const val PREFS_FILE_NAME = "searchmob-prefs.enc"
    }
}
