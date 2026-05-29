package org.searchmob.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end encrypted preferences over a Keystore-bootstrapped DEK: first-run bootstrap unlocks, a
 * written BYO API key is ciphertext on disk, round-trips back, and survives a simulated "reboot"
 * (fresh process re-unwrapping the persisted wrapped DEK with the persisted Keystore key).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun prefsFile() = File(context.filesDir, "searchmob-prefs.enc")

    private fun bootstrapFile() = File(context.filesDir, BootstrapMetadataStore.FILE_NAME)

    @Before
    @After
    fun cleanup() {
        prefsFile().delete()
        bootstrapFile().delete()
        runCatching {
            org.searchmob.data.crypto
                .KeystoreDekWrapper(context.packageManager)
                .deleteKey()
        }
    }

    @Test
    fun bootstrapWritesCiphertextRoundTripsAndSurvivesReboot() =
        runBlocking {
            val provider = StorageProvider.create(context)
            assertTrue("Keystore mode unlocks on first run", provider.bootstrap.bootstrap())

            provider.preferences.put("apiKey.brave", "super-secret-byok")
            assertEquals("super-secret-byok", provider.preferences.get("apiKey.brave"))

            // On-disk DataStore payload must be ciphertext (no plaintext secret).
            val onDisk = prefsFile().readBytes()
            assertFalse(
                "secret must not appear in plaintext on disk",
                String(onDisk, Charsets.ISO_8859_1).contains("super-secret-byok"),
            )
            // The wrapped DEK metadata is persisted and is NOT the plaintext DEK.
            assertTrue(bootstrapFile().exists())

            // Release the first DataStore (a reboot would); DataStore forbids two instances on one file.
            provider.close()

            // Simulate reboot: brand-new provider over the same files re-unwraps via the Keystore key.
            val rebooted = StorageProvider.create(context)
            assertTrue(rebooted.bootstrap.bootstrap())
            assertEquals("super-secret-byok", rebooted.preferences.get("apiKey.brave"))
        }
}
