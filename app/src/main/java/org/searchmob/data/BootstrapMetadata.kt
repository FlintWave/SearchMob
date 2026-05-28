package org.searchmob.data

import kotlinx.serialization.Serializable

/** How the DEK is wrapped. */
enum class WrapMode { KEYSTORE, PASSPHRASE }

/**
 * Unencrypted bootstrap metadata persisted so the DEK can be unwrapped on later runs. It contains the
 * wrapped (encrypted) DEK and a random salt, never the plaintext DEK or the passphrase. [securityLevel]
 * records the achieved Keystore backing (StrongBox / TEE / software) for diagnostics.
 *
 * In [WrapMode.PASSPHRASE] the KDF parameters used to derive the KEK are recorded ([kdfAlgorithm],
 * [kdfIterations], [kdfMemoryKib], [kdfParallelism]) so the exact same KEK can be reproduced on later
 * unlocks even if the compile-time defaults change. Tuning [Argon2idKdf.DEFAULT_MEMORY_KIB] and friends
 * later therefore cannot brick an existing zero-knowledge vault.
 *
 * The defaults below are the LEGACY_* constants, not the live [Argon2idKdf] defaults, on purpose: a
 * blob that lacks these fields was, by definition, written before the fields existed, so it must
 * deserialize with the parameters that were live at that time. Pinning the fallback to fixed legacy
 * constants keeps that true even as the live defaults are tuned. New vaults always write the current
 * params explicitly (see StorageBootstrap.enableZeroKnowledge), so they never rely on these defaults.
 * The fields are unused in [WrapMode.KEYSTORE].
 */
@Serializable
data class BootstrapMetadata(
    val wrappedDekBase64: String,
    val saltBase64: String,
    val mode: WrapMode,
    val securityLevel: String = "unknown",
    val kdfAlgorithm: String = LEGACY_KDF_ALGORITHM,
    val kdfIterations: Int = LEGACY_KDF_ITERATIONS,
    val kdfMemoryKib: Int = LEGACY_KDF_MEMORY_KIB,
    val kdfParallelism: Int = LEGACY_KDF_PARALLELISM,
) {
    companion object {
        // The Argon2id parameters that were live before the KDF fields were added to this schema.
        // Metadata missing the fields predates them, so it must unlock with these exact values. Do
        // NOT repoint these at Argon2idKdf.DEFAULT_*; they must stay fixed independent of tuning.
        const val LEGACY_KDF_ALGORITHM = "argon2id"
        const val LEGACY_KDF_ITERATIONS = 4
        const val LEGACY_KDF_MEMORY_KIB = 128 * 1024
        const val LEGACY_KDF_PARALLELISM = 1
    }
}
