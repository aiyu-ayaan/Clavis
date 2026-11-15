package com.aiyu.clavis.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorded-style OCR output built around published test PANs only. */
class CardTextParserTest {
    @Test fun readsFrontOfCard() {
        val card = CardTextParser.parse(listOf("HDFC BANK", "Platinum", "4242 4242 4242 4242", "MEMBER SINCE 01/15", "VALID THRU 08/29", "A B SHARMA", "VISA"))!!
        assertEquals("4242424242424242", card.pan)
        assertEquals("0829", card.expiry)
        assertEquals("A B SHARMA", card.holder)
    }

    @Test fun amexGroupingAndFourDigitYear() {
        val card = CardTextParser.parse(listOf("AMERICAN EXPRESS", "3782 822463 10005", "VALID THRU 11/2030"))!!
        assertEquals("378282246310005", card.pan)
        assertEquals("1130", card.expiry)
        assertNull(card.holder)
    }

    @Test fun rejectsLuhnFailuresAndNoise() {
        assertNull(CardTextParser.parse(listOf("4242 4242 4242 4241", "CUSTOMER CARE 1800 123 4567")))
    }

    @Test fun stabilizerNeedsThreeOfFive() {
        val s = ScanStabilizer()
        assertFalse(s.offer("4242424242424242"))
        assertFalse(s.offer(null))
        assertFalse(s.offer("4242424242424242"))
        assertTrue(s.offer("4242424242424242"))
    }
}
