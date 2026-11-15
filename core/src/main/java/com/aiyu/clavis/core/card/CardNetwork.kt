package com.aiyu.clavis.core.card

/** Card scheme. Prefix rules land in NetworkDetector (see development/docs/04-CARD-INTELLIGENCE.md). */
enum class CardNetwork { VISA, MASTERCARD, RUPAY, AMEX, DISCOVER, DINERS, JCB, UNIONPAY, MAESTRO, UNKNOWN }

enum class CardType { CREDIT, DEBIT, PREPAID, UNKNOWN }
