package com.aiyu.clavis.core.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuhnTest {
    @Test fun acceptsPublishedTestNumbers() {
        listOf("4242424242424242", "5555555555554444", "378282246310005").forEach { assertTrue(it, isLuhnValid(it)) }
    }

    @Test fun rejectsBadChecksumLengthAndNonDigits() {
        assertFalse(isLuhnValid("4242424242424241"))
        assertFalse(isLuhnValid("4242"))
        assertFalse(isLuhnValid("4242 4242 4242 4242"))
    }
}
