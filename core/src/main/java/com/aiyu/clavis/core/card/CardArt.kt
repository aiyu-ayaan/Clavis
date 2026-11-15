package com.aiyu.clavis.core.card

/**
 * Bank colour themes for the card face (D25, 04-CARD-INTELLIGENCE.md "Card art").
 * Only a base colour per bank, taken from its public brand colour. No logos or artwork are bundled (V8).
 * Patterns are whole words in the normalised issuer name. Order matters: the first match wins, so a more
 * specific name ("State Bank of India", "Union Bank of India") comes before one it contains ("Bank of India").
 */
enum class IssuerBrand(val argb: Long, private vararg val patterns: String) {
    SBI(0xFF1E4FA1, "STATE BANK OF INDIA", "SBI"),
    UNION_BANK_OF_INDIA(0xFFE31E24, "UNION BANK OF INDIA"),
    CENTRAL_BANK_OF_INDIA(0xFF1C4C9A, "CENTRAL BANK OF INDIA"),
    BANK_OF_INDIA(0xFFF7941D, "BANK OF INDIA"),
    HDFC(0xFF004C8F, "HDFC"),
    ICICI(0xFFAE282E, "ICICI"),
    AXIS(0xFF97144D, "AXIS BANK"),
    KOTAK(0xFFED1C24, "KOTAK"),
    INDUSIND(0xFF98272A, "INDUSIND"),
    YES(0xFF00518F, "YES BANK"),
    IDFC(0xFF9C1D26, "IDFC"),
    IDBI(0xFF00836C, "IDBI"),
    RBL(0xFF21409A, "RBL", "RATNAKAR BANK"),
    CANARA(0xFF0B7DC1, "CANARA"),
    BANK_OF_BARODA(0xFFF15A29, "BANK OF BARODA"),
    PNB(0xFFA20A3A, "PUNJAB NATIONAL", "PNB"),
    FEDERAL(0xFF0F4C9C, "FEDERAL BANK"),
    STANDARD_CHARTERED(0xFF0072AA, "STANDARD CHARTERED"),
    AMEX(0xFF016FD0, "AMERICAN EXPRESS", "AMEX", "AEBC"),
    HSBC(0xFFDB0011, "HSBC"),
    CITI(0xFF056DAE, "CITIBANK", "CITI"),
    CHASE(0xFF117ACA, "CHASE", "JPMORGAN"),
    BANK_OF_AMERICA(0xFF012169, "BANK OF AMERICA"),
    WELLS_FARGO(0xFFD71E28, "WELLS FARGO"),
    CAPITAL_ONE(0xFF004977, "CAPITAL ONE"),
    US_BANK(0xFF0C2074, "U S BANK", "US BANK"),
    BARCLAYS(0xFF00AEEF, "BARCLAYS"),
    SANTANDER(0xFFEC0000, "SANTANDER"),
    DISCOVER(0xFFFF6000, "DISCOVER"),
    ;

    companion object {
        /** The bank behind [issuer] (BIN table or typed by the user), or null when it has no theme. */
        fun of(issuer: String?): IssuerBrand? {
            val name = normalise(issuer) ?: return null
            return entries.firstOrNull { b -> b.patterns.any { name.contains(" $it ") } }
        }
    }
}

/** How many colour variants each bank theme has, so several cards from one bank still look different. */
const val ART_VARIANTS = 4

/** Base colour when the bank has no theme: one per network. */
fun networkArt(network: CardNetwork): Long = when (network) {
    CardNetwork.VISA -> 0xFF1A1F71
    CardNetwork.MASTERCARD -> 0xFF2B2B2E
    CardNetwork.RUPAY -> 0xFF0F6E4A
    CardNetwork.AMEX -> 0xFF016FD0
    CardNetwork.DISCOVER -> 0xFFFF6000
    CardNetwork.DINERS -> 0xFF0079BE
    CardNetwork.JCB -> 0xFF0E4C96
    CardNetwork.UNIONPAY -> 0xFFC8102E
    CardNetwork.MAESTRO -> 0xFF0099DF
    CardNetwork.UNKNOWN -> 0xFF37474F
}

/** Base colour of the card face: the bank's theme, else the network's. */
fun artBase(issuer: String?, network: CardNetwork): Long = IssuerBrand.of(issuer)?.argb ?: networkArt(network)

/**
 * Which cards share a base colour: same bank theme, else same issuer text, else same network.
 * Cards in one family get different variants by default.
 */
fun artFamily(issuer: String?, network: CardNetwork): String =
    IssuerBrand.of(issuer)?.name ?: normalise(issuer)?.trim() ?: "network:${network.name}"

/**
 * Default variant for a new card: the one used least by [others] in the same family, lowest index on a tie.
 * The fifth card from one bank reuses a variant; its last four digits still tell them apart.
 */
fun nextArtVariant(others: List<Triple<String?, CardNetwork, Int?>>, issuer: String?, network: CardNetwork): Int {
    val family = artFamily(issuer, network)
    val used = IntArray(ART_VARIANTS)
    others.forEach { (i, n, v) -> if (v != null && artFamily(i, n) == family) used[v.mod(ART_VARIANTS)]++ }
    return used.indices.minBy { used[it] }
}

/** " HDFC BANK " from "DC HDFC Bank, Ltd.": upper case, words split on anything that is not a letter or digit. */
private fun normalise(issuer: String?): String? {
    val words = issuer?.uppercase()?.split(Regex("[^A-Z0-9]+"))?.filter { it.isNotEmpty() }.orEmpty()
    return if (words.isEmpty()) null else words.joinToString(" ", prefix = " ", postfix = " ")
}
