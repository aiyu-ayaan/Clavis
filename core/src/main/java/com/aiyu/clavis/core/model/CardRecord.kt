package com.aiyu.clavis.core.model

import com.aiyu.clavis.core.card.CardNetwork
import com.aiyu.clavis.core.card.CardType
import com.aiyu.clavis.core.card.Expiry
import kotlinx.serialization.Serializable

/** Everything about a card except the CVV. Lives in memory; on disk only as AEAD ciphertext (02-SECURITY.md). */
@Serializable
data class CardRecord(
    val nickname: String? = null,
    val holderName: String,
    val pan: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val network: CardNetwork,
    val type: CardType = CardType.UNKNOWN,
    val issuer: String? = null,
    val country: String? = null,
    val colorSeed: Int = 0,
    val notes: String? = null,
) {
    val expiry: Expiry get() = Expiry(expiryMonth, expiryYear)

    override fun toString() = "CardRecord(${network.name} ••${pan.takeLast(4)})" // never print the PAN
}

/** A decrypted card as the UI sees it. The CVV stays encrypted until asked for through a fresh auth. */
data class Card(val id: String, val record: CardRecord, val hasCvv: Boolean, val sortKey: Int)
