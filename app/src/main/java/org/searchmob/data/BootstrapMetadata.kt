package org.searchmob.data

import kotlinx.serialization.Serializable

/** How the DEK is wrapped. */
enum class WrapMode { KEYSTORE, PASSPHRASE }

/**
 * Unencrypted bootstrap metadata persisted so the DEK can be unwrapped on later runs. It contains the
 * wrapped (encrypted) DEK and a random salt, never the plaintext DEK or the passphrase. [securityLevel]
 * records the achieved Keystore backing (StrongBox / TEE / software) for diagnostics.
 */
@Serializable
data class BootstrapMetadata(
    val wrappedDekBase64: String,
    val saltBase64: String,
    val mode: WrapMode,
    val securityLevel: String = "unknown",
)
