package com.aiyu.clavis.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class CryptoTest {
    private val dek = Gcm.randomBytes(32)
    private val plain = "4242424242424242".toByteArray()

    @Test fun aeadRoundTrip() {
        val c = VaultCipher(dek)
        assertArrayEquals(plain, c.open("a", Field.PAYLOAD, c.seal("a", Field.PAYLOAD, plain)))
        assertArrayEquals(plain, c.open("a", Field.CVV, c.seal("a", Field.CVV, plain)))
    }

    @Test(expected = GeneralSecurityException::class) fun movedCiphertextFails() {
        val c = VaultCipher(dek)
        c.open("b", Field.PAYLOAD, c.seal("a", Field.PAYLOAD, plain))
    }

    @Test(expected = GeneralSecurityException::class) fun cvvSubkeyIsSeparate() {
        val c = VaultCipher(dek)
        c.open("a", Field.PAYLOAD, c.seal("a", Field.CVV, plain))
    }

    @Test(expected = GeneralSecurityException::class) fun wrongKeyFails() {
        VaultCipher(Gcm.randomBytes(32)).open("a", Field.PAYLOAD, VaultCipher(dek).seal("a", Field.PAYLOAD, plain))
    }

    @Test fun kdfIsDeterministicAndSalted() {
        val salt = Gcm.randomBytes(16)
        val a = PinKdf.derive("482916".toCharArray(), salt, 1_000)
        assertArrayEquals(a, PinKdf.derive("482916".toCharArray(), salt, 1_000))
        assertFalse(a.contentEquals(PinKdf.derive("482916".toCharArray(), Gcm.randomBytes(16), 1_000)))
        assertEquals(32, a.size)
    }

    @Test fun gcmWrapRoundTrip() {
        val k = Gcm.key(Gcm.randomBytes(32))
        assertArrayEquals(dek, Gcm.open(k, Gcm.seal(k, dek)))
    }

    @Test fun pinPolicy() {
        listOf("000000", "123456", "987654", "111111").forEach { assertTrue(it, PinPolicy.isTrivial(it)) }
        listOf("482916", "112233", "135790").forEach { assertFalse(it, PinPolicy.isTrivial(it)) }
    }

    @Test fun throttleSchedule() {
        assertEquals(0, Throttle.lockoutAfter(4))
        assertEquals(30_000, Throttle.lockoutAfter(5))
        assertEquals(60_000, Throttle.lockoutAfter(6))
        assertEquals(3_600_000, Throttle.lockoutAfter(20))
        assertEquals(1, Throttle.attemptsLeft(4))
    }

    @Test fun throttleSurvivesReboot() {
        // Lockout set at t=10_000 for 30 s, then the device reboots and the clock restarts at 500.
        assertEquals(30_500, Throttle.normalizedLockUntil(40_000, 30_000, 500))
        assertEquals(40_000, Throttle.normalizedLockUntil(40_000, 30_000, 15_000))
    }
}
