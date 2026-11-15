package com.aiyu.clavis.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardArtTest {
    @Test fun matchesBinTableAndTypedNames() {
        assertEquals(IssuerBrand.HDFC, IssuerBrand.of("DC HDFC Bank"))
        assertEquals(IssuerBrand.HDFC, IssuerBrand.of("hdfc"))
        assertEquals(IssuerBrand.SBI, IssuerBrand.of("SBI Cards and Payment Services"))
        assertEquals(IssuerBrand.US_BANK, IssuerBrand.of("U.s. Bank"))
        assertEquals(IssuerBrand.AMEX, IssuerBrand.of("Aebc India - Gstar Cons Revolve"))
    }

    @Test fun specificNamesWinOverNamesTheyContain() {
        assertEquals(IssuerBrand.SBI, IssuerBrand.of("State Bank of India"))
        assertEquals(IssuerBrand.UNION_BANK_OF_INDIA, IssuerBrand.of("Union Bank of India"))
        assertEquals(IssuerBrand.CENTRAL_BANK_OF_INDIA, IssuerBrand.of("Central Bank of India"))
        assertEquals(IssuerBrand.BANK_OF_INDIA, IssuerBrand.of("Bank of India"))
    }

    @Test fun wholeWordsOnly() {
        assertNull(IssuerBrand.of("Citizens Bank")) // not CITI
        assertNull(IssuerBrand.of("Chasewood Credit Union")) // not CHASE
        assertNull(IssuerBrand.of(null))
        assertNull(IssuerBrand.of("  "))
    }

    @Test fun unknownBankFallsBackToNetwork() {
        assertEquals(networkArt(CardNetwork.VISA), artBase("Some Co-operative Bank", CardNetwork.VISA))
        assertEquals(IssuerBrand.HDFC.argb, artBase("HDFC Bank", CardNetwork.VISA))
    }

    @Test fun sameBankCardsGetDifferentVariants() {
        val cards = mutableListOf<Triple<String?, CardNetwork, Int?>>()
        val picked = (1..ART_VARIANTS).map { nextArtVariant(cards, "HDFC Bank", CardNetwork.VISA).also { v -> cards += Triple("DC HDFC Bank", CardNetwork.MASTERCARD, v) } }
        assertEquals((0 until ART_VARIANTS).toList(), picked)
        // Fifth card reuses the least used one; another bank starts fresh; theme-tinted cards (null) do not count.
        assertEquals(0, nextArtVariant(cards, "hdfc", CardNetwork.RUPAY))
        assertEquals(0, nextArtVariant(cards + Triple("ICICI Bank", CardNetwork.VISA, null), "ICICI Bank", CardNetwork.VISA))
    }

    @Test fun unknownIssuersGroupByTextThenNetwork() {
        val others = listOf(Triple<String?, CardNetwork, Int?>("Akbar Travels", CardNetwork.VISA, 0), Triple(null, CardNetwork.VISA, 1))
        assertEquals(1, nextArtVariant(others, "akbar travels", CardNetwork.MASTERCARD))
        assertEquals(0, nextArtVariant(others, null, CardNetwork.VISA))
        assertEquals(0, nextArtVariant(others, null, CardNetwork.RUPAY))
    }
}
