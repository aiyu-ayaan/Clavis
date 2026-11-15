package com.aiyu.clavis.core.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class Field(val label: String) { PAYLOAD("payload"), CVV("cvv") }

/**
 * Field-level AEAD for card data (Tink AES-256-GCM, random 96-bit nonce). CVV uses its own subkey,
 * HKDF(DEK, "cvv"). AAD binds every ciphertext to its card, field and schema, so rows cannot be swapped.
 */
class VaultCipher(dek: ByteArray) {
    private val payload: Aead = AesGcmJce(dek.copyOf())
    private val cvv: Aead = AesGcmJce(Hkdf.computeHkdf("HMACSHA256", dek, ByteArray(0), Field.CVV.label.toByteArray(), 32))

    fun seal(cardId: String, field: Field, plaintext: ByteArray): ByteArray = aead(field).encrypt(plaintext, aad(cardId, field))

    /** Throws [java.security.GeneralSecurityException] on a wrong key, tampering or a moved ciphertext. */
    fun open(cardId: String, field: Field, ciphertext: ByteArray): ByteArray = aead(field).decrypt(ciphertext, aad(cardId, field))

    private fun aead(field: Field) = if (field == Field.CVV) cvv else payload

    companion object {
        const val SCHEMA = 1
        fun aad(cardId: String, field: Field) = "$cardId|${field.label}|$SCHEMA".toByteArray()
    }
}

/** Plain AES-GCM helpers for key wrapping. Output is iv(12) || ciphertext+tag. */
object Gcm {
    private const val IV = 12
    private val random = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key) // provider picks the IV (Keystore keys refuse caller IVs)
        return c.iv + c.doFinal(plaintext)
    }

    fun open(key: SecretKey, blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV))
        return c.doFinal(blob, IV, blob.size - IV)
    }

    fun key(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")
}
