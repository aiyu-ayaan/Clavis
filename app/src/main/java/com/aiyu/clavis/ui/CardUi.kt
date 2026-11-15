package com.aiyu.clavis.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aiyu.clavis.core.card.CardNetwork
import com.aiyu.clavis.core.card.CardType
import com.aiyu.clavis.core.card.lastFour
import com.aiyu.clavis.core.model.CardRecord
import com.aiyu.clavis.ui.components.CardTint

@StringRes
fun CardNetwork.labelRes(): Int = when (this) {
    CardNetwork.VISA -> R.string.network_visa
    CardNetwork.MASTERCARD -> R.string.network_mastercard
    CardNetwork.RUPAY -> R.string.network_rupay
    CardNetwork.AMEX -> R.string.network_amex
    CardNetwork.DISCOVER -> R.string.network_discover
    CardNetwork.DINERS -> R.string.network_diners
    CardNetwork.JCB -> R.string.network_jcb
    CardNetwork.UNIONPAY -> R.string.network_unionpay
    CardNetwork.MAESTRO -> R.string.network_maestro
    CardNetwork.UNKNOWN -> R.string.network_unknown
}

/** Text treatment in place of official logos (V8: brand rules unchecked). */
@Composable
fun CardNetwork.mark(): String = when (this) {
    CardNetwork.UNKNOWN -> ""
    CardNetwork.AMEX -> stringResource(R.string.network_amex_short)
    CardNetwork.VISA -> stringResource(R.string.network_visa).uppercase()
    else -> stringResource(labelRes())
}

@StringRes
fun CardType.labelRes(): Int = when (this) {
    CardType.CREDIT -> R.string.type_credit
    CardType.DEBIT -> R.string.type_debit
    CardType.PREPAID -> R.string.type_prepaid
    CardType.UNKNOWN -> R.string.type_unknown
}

val CardRecord.tint: CardTint get() = CardTint.entries[colorSeed.mod(CardTint.entries.size)]

fun defaultSeedFor(network: CardNetwork): Int = when (network) {
    CardNetwork.VISA, CardNetwork.DISCOVER -> CardTint.PRIMARY
    CardNetwork.MASTERCARD, CardNetwork.JCB -> CardTint.NIGHT
    CardNetwork.RUPAY, CardNetwork.UNIONPAY -> CardTint.TERTIARY
    CardNetwork.AMEX -> CardTint.SECONDARY
    else -> CardTint.CONTAINER
}.ordinal

/** "Visa ending 4242": what TalkBack reads for a card. Never the full number. */
@Composable
fun CardRecord.maskedDescription(): String =
    stringResource(R.string.detail_masked_description, stringResource(network.labelRes()), lastFour(pan))

/** Title on the card face: nickname, else issuer, else network. */
@Composable
fun CardRecord.title(): String = nickname?.takeIf { it.isNotBlank() } ?: issuer?.takeIf { it.isNotBlank() } ?: stringResource(network.labelRes())
