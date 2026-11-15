package com.aiyu.clavis.core.card

import java.nio.ByteBuffer

/** What the first digits of a card say about it. Every field is a suggestion the user can edit. */
data class BinInfo(val issuer: String?, val type: CardType, val network: CardNetwork, val country: String?)

/**
 * Offline BIN (IIN) lookup over the bundled table built by tools/build_bin_table.py (BIN List Data, CC BY 4.0).
 * Nothing leaves the device: the card prefix is searched in memory.
 * shortcut: the source data is 6-digit only; add 8-digit rows and longest-prefix match when a dataset has them (D16).
 */
class BinLookup(bytes: ByteArray) {
    private val issuers: Array<String>
    private val countries: Array<String>
    private val records: ByteBuffer
    private val count: Int

    init {
        val buf = ByteBuffer.wrap(bytes)
        require(bytes.size > 5 && String(bytes, 0, 4, Charsets.US_ASCII) == "CBIN" && bytes[4].toInt() == 1) { "Not a CBIN v1 table" }
        buf.position(5)
        issuers = Array(buf.int) {
            val len = buf.get().toInt() and 0xFF
            String(bytes, buf.position(), len, Charsets.UTF_8).also { buf.position(buf.position() + len) }
        }
        countries = Array(buf.short.toInt() and 0xFFFF) {
            String(bytes, buf.position(), 2, Charsets.US_ASCII).also { buf.position(buf.position() + 2) }
        }
        count = buf.int
        records = buf.slice()
    }

    val size: Int get() = count

    /** Info for the card whose digits start with [digits], or null below 6 digits or for an unknown BIN. */
    fun lookup(digits: String): BinInfo? {
        if (digits.length < 6) return null
        val bin = digits.substring(0, 6).toIntOrNull() ?: return null
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val at = mid * RECORD
            val v = ((records.get(at).toInt() and 0xFF) shl 16) or ((records.get(at + 1).toInt() and 0xFF) shl 8) or (records.get(at + 2).toInt() and 0xFF)
            when {
                v < bin -> lo = mid + 1
                v > bin -> hi = mid - 1
                else -> {
                    val issuer = records.getShort(at + 3).toInt() and 0xFFFF
                    val type = records.get(at + 5).toInt()
                    val network = records.get(at + 6).toInt()
                    val country = records.get(at + 7).toInt() and 0xFF
                    return BinInfo(
                        issuer = if (issuer == 0xFFFF) null else issuers[issuer],
                        type = when (type) { 1 -> CardType.CREDIT; 2 -> CardType.DEBIT; 3 -> CardType.PREPAID; else -> CardType.UNKNOWN },
                        network = CardNetwork.entries.getOrElse(network) { CardNetwork.UNKNOWN },
                        country = if (country == 0xFF) null else countries[country],
                    )
                }
            }
        }
        return null
    }

    private companion object {
        const val RECORD = 8
    }
}
