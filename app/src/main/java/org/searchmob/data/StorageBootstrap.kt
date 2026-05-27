package org.searchmob.data

import org.searchmob.data.crypto.Argon2idKdf
import org.searchmob.data.crypto.Dek
import org.searchmob.data.crypto.DekWrapper
import org.searchmob.data.crypto.Kdf
import org.searchmob.data.crypto.PassphraseDekWrapper
import java.security.SecureRandom
import java.util.Base64

/**
 * The required, unmissable warning shown before enabling zero-knowledge mode. It is a normative spec
 * requirement (not just UX copy): the user must confirm they understand the data becomes permanently
 * unrecoverable if the passphrase is lost. There is intentionally no recovery / escrow / reset path.
 */
const val ZERO_KNOWLEDGE_UNRECOVERABLE_WARNING: String =
    "Zero-knowledge mode encrypts your data with a passphrase only you know. " +
        "If you forget this passphrase, your settings, saved API keys, and search history become " +
        "PERMANENTLY UNRECOVERABLE — there is no reset, recovery, or backup. " +
        "Your data is also unreadable until you unlock with the passphrase each session."

/**
 * Owns the DEK lifecycle: first-run bootstrap, later-run unwrap, and zero-knowledge enable/disable as a
 * re-wrap of the SAME DEK between the Keystore wrapper ([keystoreWrapper]) and a passphrase
 * ([PassphraseDekWrapper]). Re-wrapping never re-encrypts the DataStore payload or the SQLCipher DB —
 * switching modes only rewrites the small wrapped-DEK blob in [BootstrapMetadata], so history survives
 * a mode switch.
 *
 * The unwrapped DEK only ever lives in [vault] (process memory). On bootstrap/unlock it is set; on
 * lock/background/timeout it is zeroed (see [StorageLockController]).
 *
 * [keystoreWrapper] is a [DekWrapper] (production: `KeystoreDekWrapper`). [securityLevelProvider] and
 * [keyDeleter] capture the Keystore-specific bits the interface does not expose, keeping this class
 * JVM-testable with an in-memory wrapper.
 */
class StorageBootstrap(
    private val metadataStore: BootstrapMetadataStore,
    private val keystoreWrapper: DekWrapper,
    private val vault: Vault = Vault(),
    private val kdfFactory: () -> Kdf = { Argon2idKdf() },
    private val saltSize: Int = DEFAULT_SALT_SIZE,
    private val securityLevelProvider: () -> String = { "unknown" },
    private val keyDeleter: () -> Unit = {},
) {
    val isUnlocked: Boolean get() = vault.isUnlocked

    /** Current persisted wrap mode, or null before first-run bootstrap. */
    val mode: WrapMode? get() = metadataStore.read()?.mode

    /**
     * Ensure a DEK exists and is unlocked when possible. On first run, generates a DEK, Keystore-wraps
     * it, and persists metadata. On later runs in [WrapMode.KEYSTORE], unwraps and unlocks. In
     * [WrapMode.PASSPHRASE] the DEK cannot be unlocked here (no passphrase) — the caller must
     * [unlockWithPassphrase]. Returns true if the vault is unlocked afterward.
     */
    fun bootstrap(): Boolean {
        val existing = metadataStore.read()
        if (existing == null) {
            firstRun()
            return true
        }
        return when (existing.mode) {
            WrapMode.KEYSTORE -> {
                val dek = keystoreWrapper.unwrap(decode(existing.wrappedDekBase64)) ?: return false
                vault.unlock(dek)
                true
            }
            // Passphrase mode requires the user's passphrase; stay locked until unlockWithPassphrase.
            WrapMode.PASSPHRASE -> false
        }
    }

    private fun firstRun() {
        val dek = Dek.generate()
        val wrapped = keystoreWrapper.wrap(dek)
        metadataStore.write(
            BootstrapMetadata(
                wrappedDekBase64 = encode(wrapped),
                saltBase64 = encode(randomSalt()),
                mode = WrapMode.KEYSTORE,
                securityLevel = securityLevelProvider(),
            ),
        )
        vault.unlock(dek)
    }

    /** Unlock a passphrase-wrapped DEK. Returns true on success; a wrong passphrase fails GCM auth. */
    fun unlockWithPassphrase(passphrase: CharArray): Boolean {
        val meta = metadataStore.read() ?: return false
        if (meta.mode != WrapMode.PASSPHRASE) return false
        val wrapper = PassphraseDekWrapper(kdfFactory(), passphrase, decode(meta.saltBase64))
        val dek = wrapper.unwrap(decode(meta.wrappedDekBase64)) ?: return false
        vault.unlock(dek)
        return true
    }

    /**
     * Enable zero-knowledge mode: re-wrap the SAME unlocked DEK with a fresh-salt passphrase wrapper.
     * Requires the vault to be unlocked and the caller to have shown + confirmed
     * [ZERO_KNOWLEDGE_UNRECOVERABLE_WARNING]. No data is re-encrypted.
     */
    fun enableZeroKnowledge(
        passphrase: CharArray,
        warningConfirmed: Boolean,
    ) {
        require(warningConfirmed) {
            "Zero-knowledge mode requires explicit confirmation of the unrecoverable-data warning."
        }
        check(vault.isUnlocked) { "Cannot enable zero-knowledge mode while locked." }
        val dek = vault.dek()
        val salt = randomSalt()
        val wrapped = PassphraseDekWrapper(kdfFactory(), passphrase, salt).wrap(dek)
        metadataStore.write(
            BootstrapMetadata(
                wrappedDekBase64 = encode(wrapped),
                saltBase64 = encode(salt),
                mode = WrapMode.PASSPHRASE,
                securityLevel = securityLevelProvider(),
            ),
        )
        // The Keystore wrapping key is no longer the wrapper of record; drop it.
        runCatching { keyDeleter() }
    }

    /**
     * Disable zero-knowledge mode: re-wrap the SAME unlocked DEK back under the Keystore key. Requires
     * the vault to be unlocked (the user has just entered the passphrase). No data is re-encrypted.
     */
    fun disableZeroKnowledge() {
        check(vault.isUnlocked) { "Cannot disable zero-knowledge mode while locked." }
        val dek = vault.dek()
        val wrapped = keystoreWrapper.wrap(dek)
        metadataStore.write(
            BootstrapMetadata(
                wrappedDekBase64 = encode(wrapped),
                saltBase64 = encode(randomSalt()),
                mode = WrapMode.KEYSTORE,
                securityLevel = securityLevelProvider(),
            ),
        )
    }

    fun lock() = vault.lock()

    /** The unlocked DEK provider for the codec / SQLCipher; throws while locked. */
    fun dekProvider(): () -> ByteArray = vault::dek

    fun vault(): Vault = vault

    private fun randomSalt(): ByteArray = ByteArray(saltSize).also { SecureRandom().nextBytes(it) }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(s: String): ByteArray = Base64.getDecoder().decode(s)

    companion object {
        const val DEFAULT_SALT_SIZE = 16
    }
}
