package com.aiyu.clavis.core.card

import java.util.Calendar

/** Group sizes for display: Amex 4-6-5, 14-digit Diners 4-6-4, everything else blocks of 4. */
fun groupsFor(network: CardNetwork, length: Int): List<Int> = when {
    network == CardNetwork.AMEX -> listOf(4, 6, 5)
    network == CardNetwork.DINERS && length == 14 -> listOf(4, 6, 4)
    else -> List((length + 3) / 4) { 4 }
}

/** "4242424242424242" -> "4242 4242 4242 4242". Partial input is grouped as far as it goes. */
fun formatPan(digits: String, network: CardNetwork = NetworkDetector.detect(digits)): String {
    val out = StringBuilder()
    var i = 0
    for (size in groupsFor(network, maxOf(digits.length, network.maxLength))) {
        if (i >= digits.length) break
        if (out.isNotEmpty()) out.append(' ')
        out.append(digits, i, minOf(i + size, digits.length))
        i += size
    }
    return out.toString()
}

/** Every digit but the last four becomes a bullet; grouping is kept so the masked number has the card's shape. */
fun maskPan(digits: String, network: CardNetwork = NetworkDetector.detect(digits)): String {
    val keep = digits.length - 4
    val masked = digits.mapIndexed { i, c -> if (i < keep) '•' else c }.joinToString("")
    return formatPan(masked, network)
}

fun lastFour(digits: String): String = digits.takeLast(4)

data class Expiry(val month: Int, val year: Int) {
    val short: String get() = "%02d/%02d".format(month, year % 100)
    val long: String get() = "%02d/%04d".format(month, year)

    /** Cards are valid through the last day of their expiry month. */
    fun isExpired(now: Calendar = Calendar.getInstance()): Boolean {
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH) + 1
        return year < y || (year == y && month < m)
    }

    companion object {
        /** "MMYY" digits (as typed) -> Expiry, or null when the month is not 01–12 or input is short. */
        fun parse(digits: String): Expiry? {
            if (digits.length != 4 || !digits.all(Char::isDigit)) return null
            val month = digits.substring(0, 2).toInt()
            if (month !in 1..12) return null
            return Expiry(month, 2000 + digits.substring(2).toInt())
        }
    }
}
