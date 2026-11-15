package com.aiyu.clavis.core.card

/** How the home list is split into sections. Labels are resolved in the UI; this layer only orders them. */
enum class CardGrouping { NONE, NAME, NETWORK, BANK, TYPE, EXPIRY }

/** How the home list draws each card. */
enum class CardLayout { CARDS, LIST, WALLET }

/**
 * Splits [items] into sections by [label], A to Z ignoring case, like a contacts list.
 * A null label means "unknown" and goes last. Order inside a section is kept (the user's sort order).
 */
fun <T> groupForDisplay(items: List<T>, label: (T) -> String?): List<Pair<String?, List<T>>> =
    items.groupBy(label).toList().sortedWith(compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.first })
