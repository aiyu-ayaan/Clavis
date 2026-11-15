package com.aiyu.clavis.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against the real bundled table, with published test PANs and well-known public BIN prefixes only. */
class BinLookupTest {
    private val table = BinLookup(File("src/main/assets/bins.bin").readBytes())

    @Test fun loadsTable() = assertTrue(table.size > 300_000)

    @Test fun stripeTestCard() {
        val info = table.lookup("4242424242424242")!!
        assertEquals(CardNetwork.VISA, info.network)
        assertEquals(CardType.CREDIT, info.type)
        assertEquals("GB", info.country)
    }

    @Test fun debitAndCreditAreTold() {
        assertEquals(CardType.DEBIT, table.lookup("4000056655665556")!!.type) // Stripe's Visa debit test PAN
        assertEquals(CardType.PREPAID, table.lookup("5555555555554444")!!.type)
    }

    @Test fun shortOrUnknown() {
        assertNull(table.lookup("42424"))
        assertNull(table.lookup("000000000000"))
    }
}
