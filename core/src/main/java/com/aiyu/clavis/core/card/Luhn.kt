package com.aiyu.clavis.core.card

/** True when [digits] is 12–19 digits and passes the Luhn checksum. Spaces and dashes are not accepted. */
fun isLuhnValid(digits: String): Boolean {
    if (digits.length !in 12..19 || !digits.all { it in '0'..'9' }) return false
    var sum = 0
    for ((i, c) in digits.reversed().withIndex()) {
        var d = c - '0'
        if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
        sum += d
    }
    return sum % 10 == 0
}
