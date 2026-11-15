package com.aiyu.clavis.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
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

    /**
     * K_recovery (D13 revised): RSA key whose private half needs the phone's screen lock. The DEK is wrapped with the
     * public half, which needs no auth, so the wrap can be refreshed on every unlock. Throws when the phone has no
     * secure lock screen.
     */
    fun recoveryWrap(secret: ByteArray): ByteArray {
        if (!ks.containsAlias(RECOVERY)) {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER)
            gen.initialize(
                KeyGenParameterSpec.Builder(RECOVERY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setUserAuthenticationRequired(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_DEVICE_CREDENTIAL)
                        else @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(30)
                    }
                    .build(),
            )
            gen.generateKeyPair()
        }
        // Public-key ops run in software; rebuild the key so the platform provider, not Keystore, does them.
        val public = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(X509EncodedKeySpec(ks.getCertificate(RECOVERY).publicKey.encoded))
        return Cipher.getInstance(RSA).apply { init(Cipher.ENCRYPT_MODE, public, OAEP) }.doFinal(secret)
    }

    /**
     * Decrypt cipher for K_recovery. On API 30+ it must go through BiometricPrompt as a CryptoObject; below that it
     * only works within 30 s after the phone lock was confirmed. Throws KeyPermanentlyInvalidatedException after
     * the screen lock was removed.
     */
    fun recoveryCipher(): Cipher? {
        val key = ks.getKey(RECOVERY, null) as PrivateKey? ?: return null
        return Cipher.getInstance(RSA).apply { init(Cipher.DECRYPT_MODE, key, OAEP) }
    }

    fun deleteRecoveryKey() = ks.deleteEntry(RECOVERY)

    fun deleteAll() {
        ks.deleteEntry(DEVICE)
        ks.deleteEntry(BIO)
        ks.deleteEntry(RECOVERY)
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
        const val RECOVERY = "clavis.k_recovery"
        const val RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
