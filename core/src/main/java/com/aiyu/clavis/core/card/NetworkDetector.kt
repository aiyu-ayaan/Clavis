package com.aiyu.clavis.core.card

/**
 * Prefix (IIN) rules from development/docs/04-CARD-INTELLIGENCE.md. The longest matching prefix wins, so
 * Discover 6011 beats RuPay 60 and Discover 622126–622925 beats UnionPay 62. Equal lengths: table order.
 * Ranges still need checking against each scheme's docs before release (V5).
 */
object NetworkDetector {

    private class Rule(val network: CardNetwork, val from: Int, val to: Int, val digits: Int)

    private fun r(network: CardNetwork, from: Int, to: Int = from) = Rule(network, from, to, from.toString().length)

    private val rules = listOf(
        r(CardNetwork.AMEX, 34), r(CardNetwork.AMEX, 37),
        r(CardNetwork.DINERS, 300, 305), r(CardNetwork.DINERS, 36), r(CardNetwork.DINERS, 38, 39),
        r(CardNetwork.JCB, 3528, 3589),
        r(CardNetwork.DISCOVER, 6011), r(CardNetwork.DISCOVER, 644, 649), r(CardNetwork.DISCOVER, 65),
        r(CardNetwork.DISCOVER, 622126, 622925),
        r(CardNetwork.RUPAY, 6521, 6522), r(CardNetwork.RUPAY, 60), r(CardNetwork.RUPAY, 81, 82), r(CardNetwork.RUPAY, 508),
        r(CardNetwork.UNIONPAY, 62),
        r(CardNetwork.MASTERCARD, 51, 55), r(CardNetwork.MASTERCARD, 2221, 2720),
        r(CardNetwork.VISA, 4),
        r(CardNetwork.MAESTRO, 50), r(CardNetwork.MAESTRO, 56, 69),
    )

    /** Network for the digits typed so far; UNKNOWN until a rule's whole prefix is present. */
    fun detect(digits: String): CardNetwork {
        var best: Rule? = null
        for (rule in rules) {
            if (digits.length < rule.digits) continue
            val prefix = digits.substring(0, rule.digits).toIntOrNull() ?: continue
            if (prefix in rule.from..rule.to && (best == null || rule.digits > best.digits)) best = rule
        }
        return best?.network ?: CardNetwork.UNKNOWN
    }
}

val CardNetwork.validLengths: IntRange
    get() = when (this) {
        CardNetwork.AMEX -> 15..15
        CardNetwork.MASTERCARD, CardNetwork.RUPAY -> 16..16
        CardNetwork.VISA -> 13..19
        CardNetwork.DINERS -> 14..19
        CardNetwork.DISCOVER, CardNetwork.JCB, CardNetwork.UNIONPAY -> 16..19
        CardNetwork.MAESTRO -> 12..19
        CardNetwork.UNKNOWN -> 12..19
    }

val CardNetwork.maxLength: Int get() = validLengths.last

val CardNetwork.cvvLength: Int get() = if (this == CardNetwork.AMEX) 4 else 3

/** Luhn plus the network's allowed lengths. */
fun isPanValid(digits: String): Boolean = isLuhnValid(digits) && digits.length in NetworkDetector.detect(digits).validLengths
