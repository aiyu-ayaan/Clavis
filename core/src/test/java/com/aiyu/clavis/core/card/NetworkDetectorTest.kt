package com.aiyu.clavis.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDetectorTest {
    @Test fun detectsPublishedTestPans() {
        mapOf(
            "4242424242424242" to CardNetwork.VISA,
            "5555555555554444" to CardNetwork.MASTERCARD,
            "2223003122003222" to CardNetwork.MASTERCARD,
            "378282246310005" to CardNetwork.AMEX,
            "6011111111111117" to CardNetwork.DISCOVER,
            "3056930009020004" to CardNetwork.DINERS,
            "3566002020360505" to CardNetwork.JCB,
            "6200000000000005" to CardNetwork.UNIONPAY,
        ).forEach { (pan, net) -> assertEquals(pan, net, NetworkDetector.detect(pan)) }
    }

    @Test fun longestPrefixResolvesOverlaps() {
        assertEquals(CardNetwork.RUPAY, NetworkDetector.detect("6521000000000000"))
        assertEquals(CardNetwork.DISCOVER, NetworkDetector.detect("6500000000000000"))
        assertEquals(CardNetwork.RUPAY, NetworkDetector.detect("6080000000000000"))
        assertEquals(CardNetwork.DISCOVER, NetworkDetector.detect("6221260000000000"))
        assertEquals(CardNetwork.MAESTRO, NetworkDetector.detect("5018000000000000"))
    }

    @Test fun partialInput() {
        assertEquals(CardNetwork.UNKNOWN, NetworkDetector.detect(""))
        assertEquals(CardNetwork.VISA, NetworkDetector.detect("4"))
        assertEquals(CardNetwork.UNKNOWN, NetworkDetector.detect("2"))
    }

    @Test fun formatsAndMasksPerNetwork() {
        assertEquals("4242 4242 4242 4242", formatPan("4242424242424242"))
        assertEquals("3782 822463 10005", formatPan("378282246310005"))
        assertEquals("4242 42", formatPan("424242"))
        assertEquals("•••• •••• •••• 4242", maskPan("4242424242424242"))
        assertEquals("•••• •••••• •0005", maskPan("378282246310005"))
    }

    @Test fun panValidityChecksLength() {
        assertTrue(isPanValid("4242424242424242"))
        assertFalse(isPanValid("4242424242424241"))
    }

    @Test fun expiryParsing() {
        assertEquals(Expiry(8, 2029), Expiry.parse("0829"))
        assertNull(Expiry.parse("1329"))
        assertNull(Expiry.parse("08"))
        assertEquals("08/29", Expiry(8, 2029).short)
        assertTrue(Expiry(1, 2001).isExpired())
    }
}
