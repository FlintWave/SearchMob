package org.searchmob.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.searchmob.data.crypto.Dek
import org.searchmob.data.crypto.KeystoreDekWrapper
import org.searchmob.data.crypto.SecurityLevel

/**
 * On-device behaviour of the Android Keystore wrapper: wrap/unwrap round-trip, tamper rejection, a
 * recorded (non-UNKNOWN) security level, and StrongBox-or-TEE fallback without crashing. Emulators
 * usually lack StrongBox, so we assert TEE/SOFTWARE/STRONGBOX is recorded rather than a specific one.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreDekWrapperTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val alias = "searchmob.test.kek"

    private fun wrapper() = KeystoreDekWrapper(context.packageManager, keyAlias = alias)

    @Before
    @After
    fun cleanup() {
        runCatching { wrapper().deleteKey() }
    }

    @Test
    fun wrapThenUnwrapYieldsSameDek() {
        val w = wrapper()
        val dek = Dek.generate()
        val blob = w.wrap(dek)
        assertNotEquals("blob must differ from raw DEK", dek.toList(), blob.toList())
        assertArrayEquals(dek, w.unwrap(blob))
    }

    @Test
    fun tamperedBlobIsRejected() {
        val w = wrapper()
        val blob = w.wrap(Dek.generate())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertNull(w.unwrap(blob))
    }

    @Test
    fun securityLevelIsRecordedAfterGeneration() {
        val w = wrapper()
        w.wrap(Dek.generate())
        // Must fall back gracefully and record a concrete backing (StrongBox preferred, else TEE/SW).
        assertTrue(
            w.securityLevel in
                setOf(SecurityLevel.STRONGBOX, SecurityLevel.TEE, SecurityLevel.SOFTWARE),
        )
    }

    @Test
    fun differentInstancesShareTheSameKeystoreKey() {
        val dek = Dek.generate()
        val blob = wrapper().wrap(dek)
        // A fresh wrapper with the same alias loads the persisted key and unwraps the same DEK.
        assertArrayEquals(dek, wrapper().unwrap(blob))
    }
}
