package org.searchmob.data.crypto

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Achieved hardware backing of the Keystore wrapping key, recorded after generation so it can be
 * surfaced (diagnostics) and asserted in tests. [STRONGBOX] is the dedicated secure element,
 * [TEE] the Trusted Execution Environment, [SOFTWARE] a non-hardware key.
 */
enum class SecurityLevel { STRONGBOX, TEE, SOFTWARE, UNKNOWN }

/**
 * Wraps the DEK with an AES-256-GCM key held in the [AndroidKeyStore]. The key never leaves the
 * Keystore: wrapping/unwrapping run inside it. StrongBox is requested when the device advertises
 * [PackageManager.FEATURE_STRONGBOX_KEYSTORE]; a [StrongBoxUnavailableException] during generation
 * falls back to a TEE-backed key. A fresh random IV is generated per wrap (the Keystore supplies it)
 * and prepended to the ciphertext. Unwrapping a tampered blob fails GCM authentication and returns
 * null rather than yielding incorrect key material.
 *
 * Optional user-authentication binding ([requireUserAuthentication]) makes the key usable only after
 * a recent device unlock / biometric ([authValiditySeconds]); it is OFF by default and independent of
 * zero-knowledge mode.
 */
class KeystoreDekWrapper(
    private val packageManager: PackageManager,
    private val keyAlias: String = DEFAULT_ALIAS,
    private val requireUserAuthentication: Boolean = false,
    private val authValiditySeconds: Int = DEFAULT_AUTH_VALIDITY_SECONDS,
) : DekWrapper {
    /** The hardware backing achieved for the wrapping key; populated on first key generation. */
    var securityLevel: SecurityLevel = SecurityLevel.UNKNOWN
        private set

    override fun wrap(dek: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        // The Keystore generates a fresh random IV for this operation.
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(dek)
        return iv + ciphertext
    }

    override fun unwrap(blob: ByteArray): ByteArray? =
        try {
            val key = loadKey() ?: return null
            val iv = blob.copyOfRange(0, IV_LENGTH)
            val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: java.security.GeneralSecurityException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    /** Deletes the wrapping key from the Keystore (used when re-keying / switching wrap modes). */
    fun deleteKey() {
        keystore().deleteEntry(keyAlias)
        securityLevel = SecurityLevel.UNKNOWN
    }

    private fun getOrCreateKey(): SecretKey {
        loadKey()?.let {
            securityLevel = readSecurityLevel(it)
            return it
        }
        return generateKey()
    }

    private fun loadKey(): SecretKey? {
        val ks = keystore()
        val entry = ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry ?: return null
        return entry.secretKey
    }

    private fun generateKey(): SecretKey {
        val hasStrongBox =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (hasStrongBox) {
            try {
                return generateKey(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                // Advertised but unusable on this device, fall back to TEE.
            }
        }
        return generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        // Bind the key to a device-unlocked state so the wrapped DEK cannot be unwrapped while the
        // device is locked (API 28+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        if (requireUserAuthentication) {
            applyUserAuthentication(builder)
        }

        generator.init(builder.build())
        val key = generator.generateKey()
        securityLevel = readSecurityLevel(key)
        return key
    }

    private fun applyUserAuthentication(builder: KeyGenParameterSpec.Builder) {
        builder.setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                authValiditySeconds,
                KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(authValiditySeconds)
        }
    }

    private fun readSecurityLevel(key: SecretKey): SecurityLevel =
        try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> SecurityLevel.SOFTWARE
                    else -> SecurityLevel.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) SecurityLevel.TEE else SecurityLevel.SOFTWARE
            }
        } catch (_: java.security.GeneralSecurityException) {
            SecurityLevel.UNKNOWN
        }

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "searchmob.dek.kek"
        private const val DEFAULT_AUTH_VALIDITY_SECONDS = 30
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
