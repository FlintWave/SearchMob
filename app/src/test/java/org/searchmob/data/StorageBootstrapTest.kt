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
import org.searchmob.data.crypto.KdfParams
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

    // The JVM test KDF is PBKDF2; we map the recorded "iterations" param onto its iteration count so
    // the stored params actually drive derivation (a wrong/changed count yields a different KEK).
    private fun bootstrap(
        metadataStore: BootstrapMetadataStore,
        kek: ByteArray = Dek.generate(),
        vault: Vault = Vault(),
        kdfParams: KdfParams = KdfParams(algorithm = "pbkdf2", iterations = 1_000),
    ) = StorageBootstrap(
        metadataStore = metadataStore,
        keystoreWrapper = SecretKeyDekWrapper(kek),
        vault = vault,
        kdfFactory = { params -> Pbkdf2Kdf(iterations = params.iterations) },
        kdfParams = kdfParams,
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
    fun kdfParamsAreWrittenToMetadataOnEnableZeroKnowledge() {
        val meta = store()
        val sut = bootstrap(meta, kdfParams = KdfParams(algorithm = "pbkdf2", iterations = 1_234))
        sut.bootstrap()
        sut.enableZeroKnowledge("pw".toCharArray(), warningConfirmed = true)

        // The exact params used to wrap are recorded so a later unlock can reproduce them.
        val written = meta.read()!!
        assertEquals("pbkdf2", written.kdfAlgorithm)
        assertEquals(1_234, written.kdfIterations)
    }

    @Test
    fun unlockUsesStoredKdfParamsNotChangedDefaults() {
        val meta = store()
        val kek = Dek.generate()
        // Enable zero-knowledge with one iteration count, recorded into metadata.
        val enabling = bootstrap(meta, kek, kdfParams = KdfParams(algorithm = "pbkdf2", iterations = 1_000))
        enabling.bootstrap()
        val originalDek = enabling.vault().dek().copyOf()
        enabling.enableZeroKnowledge("correct horse".toCharArray(), warningConfirmed = true)

        // A later session whose *default* params differ (simulating a tuning change) must still derive
        // the SAME KEK by reading the stored params, recovering the original DEK.
        val relaunch = bootstrap(meta, kek, kdfParams = KdfParams(algorithm = "pbkdf2", iterations = 9_999))
        relaunch.bootstrap()
        assertTrue(relaunch.unlockWithPassphrase("correct horse".toCharArray()))
        assertArrayEquals(originalDek, relaunch.vault().dek())
    }

    @Test
    fun olderMetadataWithoutKdfFieldsDeserializesWithLegacyParams() {
        // A metadata blob written before the KDF fields existed (only the original four fields). It
        // must still deserialize (no crash) and fill in the LEGACY params that were live when such a
        // blob was written, not the current (possibly retuned) defaults, so the original KEK derives.
        val file = File(tmp.newFolder(), BootstrapMetadataStore.FILE_NAME)
        file.writeText(
            """{"wrappedDekBase64":"AA==","saltBase64":"AA==",""" +
                """"mode":"PASSPHRASE","securityLevel":"TEE"}""",
        )
        val read = BootstrapMetadataStore(file).read()!!
        assertEquals(WrapMode.PASSPHRASE, read.mode)
        assertEquals(BootstrapMetadata.LEGACY_KDF_ALGORITHM, read.kdfAlgorithm)
        assertEquals(BootstrapMetadata.LEGACY_KDF_ITERATIONS, read.kdfIterations)
        assertEquals(BootstrapMetadata.LEGACY_KDF_MEMORY_KIB, read.kdfMemoryKib)
        assertEquals(BootstrapMetadata.LEGACY_KDF_PARALLELISM, read.kdfParallelism)
    }

    @Test
    fun warningStringStatesUnrecoverable() {
        assertTrue(ZERO_KNOWLEDGE_UNRECOVERABLE_WARNING.contains("UNRECOVERABLE"))
    }

    @Test
    fun corruptMetadataFailsClosedAndNeverRekeys() {
        // A present-but-unreadable metadata file (truncated write, flipped byte) must NOT be treated
        // as first run: re-keying would overwrite the only copy of the wrapped DEK and permanently
        // destroy every encrypted store. Bootstrap stays locked and the corrupt file is untouched.
        val file = File(tmp.newFolder(), BootstrapMetadataStore.FILE_NAME)
        file.writeText("""{"wrappedDekBase64":"AA=""") // torn JSON
        val meta = BootstrapMetadataStore(file)
        val sut = bootstrap(meta)

        assertFalse(sut.bootstrap())
        assertFalse(sut.isUnlocked)
        assertEquals("""{"wrappedDekBase64":"AA=""", file.readText())
    }

    @Test
    fun metadataWriteReplacesAtomicallyLeavingNoScratchFile() {
        val meta = store()
        val sut = bootstrap(meta)
        assertTrue(sut.bootstrap())
        val first = meta.read()!!

        // A later rewrite (mode switch) replaces the file wholesale and leaves no .tmp sibling.
        sut.enableZeroKnowledge("pass".toCharArray(), warningConfirmed = true)
        val second = meta.read()!!
        assertEquals(WrapMode.PASSPHRASE, second.mode)
        assertTrue(second.wrappedDekBase64 != first.wrappedDekBase64)
        val folder = File(checkNotNull(metaFileOf(meta)).parent!!)
        assertTrue(folder.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    // Reflection-free way to find the metadata file: the store was built from a temp folder with the
    // canonical file name, so locate it under the TemporaryFolder root.
    private fun metaFileOf(
        @Suppress("UNUSED_PARAMETER") store: BootstrapMetadataStore,
    ): File? =
        tmp.root
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == BootstrapMetadataStore.FILE_NAME }
}
