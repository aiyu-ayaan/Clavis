package com.aiyu.clavis.core.card

import org.junit.Assert.assertEquals
import org.junit.Test

class CardGroupingTest {
    @Test fun sortsSectionsAtoZWithUnknownLastAndKeepsOrderInside() {
        val items = listOf("visa1" to "Visa", "x" to null, "mc" to "mastercard", "visa2" to "Visa", "amex" to "American Express")
        val groups = groupForDisplay(items) { it.second }
        assertEquals(listOf("American Express", "mastercard", "Visa", null), groups.map { it.first })
        assertEquals(listOf("visa1", "visa2"), groups[2].second.map { it.first })
    }

    @Test fun yearsSortInOrder() {
        assertEquals(listOf("2026", "2029", "2031"), groupForDisplay(listOf("2031", "2026", "2029")) { it }.map { it.first })
    }
}
