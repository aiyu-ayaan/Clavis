package com.aiyu.clavis.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed, non-exportable Keystore keys (02-SECURITY.md key hierarchy):
 * K_device wraps the PIN path, K_bio (biometric-bound, invalidated on new enrollment) wraps the DEK for biometric unlock.
 * StrongBox when the device has it, TEE otherwise.
 */
class KeyManager {
    private val ks: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun deviceKey(): SecretKey = (ks.getKey(DEVICE, null) as SecretKey?) ?: generate(DEVICE) {}

    /** Fresh K_bio and an encrypt cipher for it; the cipher must be authorized by BiometricPrompt before use. */
    fun newBioEncryptCipher(): Cipher {
        ks.deleteEntry(BIO)
        val key = generate(BIO) {
            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
        }
        return Cipher.getInstance(GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Decrypt cipher for K_bio. Throws KeyPermanentlyInvalidatedException after a biometric enrollment change. */
    fun bioDecryptCipher(iv: ByteArray): Cipher? {
        val key = ks.getKey(BIO, null) as SecretKey? ?: return null
        return Cipher.getInstance(GCM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }

    fun deleteBioKey() = ks.deleteEntry(BIO)

    fun deleteAll() {
        ks.deleteEntry(DEVICE)
        ks.deleteEntry(BIO)
    }

    private fun generate(alias: String, extra: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(strongBox)
            .apply(extra)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        return try {
            gen.init(spec(strongBox = true)); gen.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            gen.init(spec(strongBox = false)); gen.generateKey()
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val GCM = "AES/GCM/NoPadding"
        const val DEVICE = "clavis.k_device"
        const val BIO = "clavis.k_bio"
    }
}
