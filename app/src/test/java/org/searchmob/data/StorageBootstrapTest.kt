package org.searchmob.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.searchmob.data.crypto.Dek
import org.searchmob.data.crypto.Pbkdf2Kdf
import org.searchmob.data.crypto.SecretKeyDekWrapper
import java.io.File

/**
 * JVM coverage for the DEK lifecycle and zero-knowledge re-wrap, using an in-memory [SecretKeyDekWrapper]
 * in place of the Android Keystore and a fast PBKDF2 in place of Argon2id. The on-device Keystore /
 * Argon2id behaviour is exercised in instrumentation tests.
 */
class StorageBootstrapTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(): BootstrapMetadataStore =
        BootstrapMetadataStore(File(tmp.newFolder(), BootstrapMetadataStore.FILE_NAME))

    private fun bootstrap(
        metadataStore: BootstrapMetadataStore,
        kek: ByteArray = Dek.generate(),
        vault: Vault = Vault(),
    ) = StorageBootstrap(
        metadataStore = metadataStore,
        keystoreWrapper = SecretKeyDekWrapper(kek),
        vault = vault,
        kdfFactory = { Pbkdf2Kdf(iterations = 1_000) },
        securityLevelProvider = { "TEE" },
    )

    @Test
    fun firstRunGeneratesWrappedDekAndUnlocks() {
        val meta = store()
        val sut = bootstrap(meta)
        assertFalse(meta.exists())

        assertTrue(sut.bootstrap())
        assertTrue(sut.isUnlocked)
        // Metadata persisted: wrapped (not plaintext) DEK, Keystore mode, recorded security level.
        val written = meta.read()!!
        assertEquals(WrapMode.KEYSTORE, written.mode)
        assertEquals("TEE", written.securityLevel)
        assertTrue(written.wrappedDekBase64.isNotBlank())
    }

    @Test
    fun laterRunUnwrapsSameDekInKeystoreMode() {
        val meta = store()
        val kek = Dek.generate()
        val first = bootstrap(meta, kek)
        first.bootstrap()
        val dek = first.vault().dek().copyOf()

        // Fresh instance, same metadata + same KEK = same unwrapped DEK.
        val second = bootstrap(meta, kek)
        assertTrue(second.bootstrap())
        assertArrayEquals(dek, second.vault().dek())
    }

    @Test
    fun enableZeroKnowledgeRewrapsSameDekAndUnlocksWithPassphrase() {
        val meta = store()
        val sut = bootstrap(meta)
        sut.bootstrap()
        val originalDek = sut.vault().dek().copyOf()

        sut.enableZeroKnowledge("correct horse battery".toCharArray(), warningConfirmed = true)
        assertEquals(WrapMode.PASSPHRASE, meta.read()!!.mode)

        // New session: starts locked; correct passphrase recovers the SAME DEK.
        val relaunch = bootstrap(meta)
        assertFalse(relaunch.bootstrap()) // passphrase mode: stays locked
        assertFalse(relaunch.isUnlocked)
        assertTrue(relaunch.unlockWithPassphrase("correct horse battery".toCharArray()))
        assertArrayEquals(originalDek, relaunch.vault().dek())
    }

    @Test
    fun wrongPassphraseDoesNotUnlock() {
        val meta = store()
        val sut = bootstrap(meta)
        sut.bootstrap()
        sut.enableZeroKnowledge("right".toCharArray(), warningConfirmed = true)

        val relaunch = bootstrap(meta)
        relaunch.bootstrap()
        assertFalse(relaunch.unlockWithPassphrase("wrong".toCharArray()))
        assertFalse(relaunch.isUnlocked)
    }

    @Test
    fun enableZeroKnowledgeRequiresWarningConfirmation() {
        val sut = bootstrap(store())
        sut.bootstrap()
        assertThrows(IllegalArgumentException::class.java) {
            sut.enableZeroKnowledge("pw".toCharArray(), warningConfirmed = false)
        }
    }

    @Test
    fun disableZeroKnowledgeRewrapsBackToKeystoreSameDek() {
        val meta = store()
        val kek = Dek.generate()
        val sut = bootstrap(meta, kek)
        sut.bootstrap()
        val originalDek = sut.vault().dek().copyOf()
        sut.enableZeroKnowledge("pw".toCharArray(), warningConfirmed = true)

        // Unlock with passphrase, then disable -> back to Keystore wrapping of the same DEK.
        val relaunch = bootstrap(meta, kek)
        relaunch.bootstrap()
        relaunch.unlockWithPassphrase("pw".toCharArray())
        relaunch.disableZeroKnowledge()
        assertEquals(WrapMode.KEYSTORE, meta.read()!!.mode)

        val afterDisable = bootstrap(meta, kek)
        assertTrue(afterDisable.bootstrap())
        assertArrayEquals(originalDek, afterDisable.vault().dek())
    }

    @Test
    fun corruptedKeystoreBlobFailsBootstrapWithoutCrash() {
        val meta = store()
        bootstrap(meta).bootstrap()
        // Tamper the persisted wrapped blob; unwrap with a fresh wrapper (different KEK) fails -> locked.
        val tampered = bootstrap(meta, kek = Dek.generate())
        assertFalse(tampered.bootstrap())
        assertFalse(tampered.isUnlocked)
        assertNull(tampered.vault().let { if (it.isUnlocked) it.dek() else null })
    }

    @Test
    fun warningStringStatesUnrecoverable() {
        assertTrue(ZERO_KNOWLEDGE_UNRECOVERABLE_WARNING.contains("UNRECOVERABLE"))
    }
}
