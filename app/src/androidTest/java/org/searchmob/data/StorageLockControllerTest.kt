package org.searchmob.data

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.searchmob.data.crypto.Dek
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.SqlCipherHistoryStore

/**
 * The eviction state machine: in zero-knowledge mode an explicit lock and an `ON_STOP` background event
 * zero the in-memory DEK and close the history handle (without destroying encrypted data), while in
 * Keystore mode there is nothing to evict on background.
 */
@RunWith(AndroidJUnit4::class)
class StorageLockControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanup() {
        SqlCipherHistoryStore(context, { Dek.generate() }).disable()
    }

    private fun history(dek: ByteArray) = SqlCipherHistoryStore(context, { dek }, ttlMs = 60_000)

    @Test
    fun explicitLockZeroesDekAndClosesHistoryHandle() {
        val dek = Dek.generate()
        val vault = Vault().apply { unlock(dek) }
        val history =
            history(dek).apply {
                setEnabled(true)
                add(HistoryEntry("q", 1_000))
            }

        val controller =
            StorageLockController(vault, history, modeProvider = { WrapMode.PASSPHRASE })
        controller.lockNow()

        assertFalse(vault.isUnlocked)
        assertTrue("DEK bytes zeroed in place", dek.all { it == 0.toByte() })
    }

    @Test
    fun backgroundEvictsInZeroKnowledgeMode() {
        val dek = Dek.generate()
        val vault = Vault().apply { unlock(dek) }
        val history = history(dek).apply { setEnabled(true) }
        val controller =
            StorageLockController(vault, history, modeProvider = { WrapMode.PASSPHRASE })

        controller.onStop(fakeOwner())
        assertFalse(vault.isUnlocked)
    }

    @Test
    fun backgroundDoesNotEvictInKeystoreMode() {
        val dek = Dek.generate()
        val vault = Vault().apply { unlock(dek) }
        val history = history(dek)
        val controller =
            StorageLockController(vault, history, modeProvider = { WrapMode.KEYSTORE })

        controller.onStop(fakeOwner())
        assertTrue("Keystore mode is seamless; DEK stays unlocked on background", vault.isUnlocked)
    }

    private fun fakeOwner(): LifecycleOwner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle = LifecycleRegistry(this)
        }

    @Test
    fun dataSurvivesLockAndIsReadableAfterReopen() {
        // The history store holds a fixed DEK provider, so after the lock closes its handle it
        // re-opens lazily with the same key and the encrypted data is still there.
        val dek = Dek.generate()
        val vault = Vault().apply { unlock(dek) }
        val history =
            history(dek).apply {
                setEnabled(true)
                add(HistoryEntry("survives", 1_000))
            }
        val controller =
            StorageLockController(vault, history, modeProvider = { WrapMode.PASSPHRASE })

        controller.lockNow()
        assertFalse(vault.isUnlocked)
        // History handle was closed (not deleted); a fresh read re-opens it and returns the data.
        assertEquals(listOf("survives"), history.list(60_000).map { it.query })
    }
}
