package com.aiyu.clavis.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.aiyu.clavis.ui.components.CardColors

// Card art (D25): a bank's base colour turned into one of four face variants. Fixed colours on purpose, like a
// real card, so they do not follow light/dark or dynamic colour. Text is white or ink, whichever has more contrast.

private val ArtInk = Color(0xFF1B1C20)
private val ArtGraphite = Color(0xFF1E1F23)

/** Face colours for [variant] of a bank or network [base] colour (ARGB): classic, deep, light, graphite. */
fun cardArtColors(base: Long, variant: Int): CardColors {
    val b = Color(base)
    val container = when (variant.mod(4)) {
        1 -> lerp(b, Color.Black, 0.42f)
        2 -> lerp(b, Color.White, 0.62f)
        3 -> lerp(ArtGraphite, b, 0.22f)
        else -> b
    }
    val l = container.luminance()
    val onWhite = 1.05f / (l + 0.05f)
    val onInk = (l + 0.05f) / (ArtInk.luminance() + 0.05f)
    return CardColors(container, if (onInk > onWhite) ArtInk else Color.White)
}
