package com.aiyu.clavis.core.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN -> KEK. PBKDF2-HMAC-SHA256 at the OWASP 600k floor (D15: chosen over Argon2id to avoid a native
 * dependency). The output is further wrapped under the hardware Keystore key, so guessing needs the device.
 */
object PinKdf {
    const val ITERATIONS = 600_000

    fun derive(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

object PinPolicy {
    const val LENGTH = 6

    /** Rejects all-same digits and straight ascending/descending runs (000000, 123456, 987654). */
    fun isTrivial(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}
