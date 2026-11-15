package com.aiyu.clavis.core.card

/** What OCR found on a card front. CVV is never read (it is on the back and always typed). */
data class ScannedCard(val pan: String, val expiry: String?, val holder: String?)

/**
 * Turns OCR lines into a card (04-CARD-INTELLIGENCE.md parser rules): Luhn-valid 13–19 digit PAN preferring 16,
 * then 15, then 19; MM/YY expiry next to VALID THRU style labels; holder as an upper-case name line.
 */
object CardTextParser {
    private val digitRun = Regex("""(?<!\d)\d[\d \-]{11,24}\d(?!\d)""")
    private val expiry = Regex("""(?<!\d)(0[1-9]|1[0-2])\s?/\s?(\d{4}|\d{2})(?!\d)""")
    private val sinceLabel = Regex("""MEMBER|SINCE|FROM|ISSUED""", RegexOption.IGNORE_CASE)
    private val nameLine = Regex("""^[A-Z][A-Z .'\-]{1,25}$""")
    private val notNames = setOf(
        "VALID", "THRU", "THROUGH", "GOOD", "EXP", "EXPIRES", "EXPIRY", "MONTH", "YEAR", "BANK", "DEBIT", "CREDIT", "CARD",
        "VISA", "MASTERCARD", "RUPAY", "AMERICAN", "EXPRESS", "DISCOVER", "PLATINUM", "GOLD", "CLASSIC", "SIGNATURE",
        "INFINITE", "WORLD", "BUSINESS", "PREPAID", "ELECTRONIC", "USE", "ONLY", "INTERNATIONAL", "MEMBER", "SINCE",
        "MAESTRO", "CONTACTLESS", "PLUS", "SELECT", "REWARDS", "TITANIUM", "MONEYBACK", "MILLENNIA", "LTD", "LIMITED",
    )

    fun parse(lines: List<String>): ScannedCard? {
        val pans = lines.flatMap { line -> digitRun.findAll(line).map { it.value.filter(Char::isDigit) } }
            .filter { it.length in 13..19 && isLuhnValid(it) }
        val pan = pans.minByOrNull { when (it.length) { 16 -> 0; 15 -> 1; 19 -> 2; else -> 3 } } ?: return null

        val exp = lines.filterNot { sinceLabel.containsMatchIn(it) }
            .flatMap { expiry.findAll(it).toList() }
            .map { m -> m.groupValues[1] + m.groupValues[2].takeLast(2) }
            .mapNotNull { Expiry.parse(it)?.let { e -> it to e } }
            .maxByOrNull { (_, e) -> e.year * 12 + e.month } // valid-thru is the latest date on the card
            ?.first

        val holder = lines.map { it.trim() }
            .filter { nameLine.matches(it) && it.split(' ').size in 2..4 }
            .firstOrNull { line -> line.split(' ', '.').none { it in notNames } }

        return ScannedCard(pan, exp, holder)
    }
}

/** Accepts a PAN once it shows up in [needed] of the last [window] frames: the biggest OCR accuracy lever. */
class ScanStabilizer(private val window: Int = 5, private val needed: Int = 3) {
    private val recent = ArrayDeque<String?>()

    fun offer(pan: String?): Boolean {
        recent.addLast(pan)
        if (recent.size > window) recent.removeFirst()
        return pan != null && recent.count { it == pan } >= needed
    }
}
