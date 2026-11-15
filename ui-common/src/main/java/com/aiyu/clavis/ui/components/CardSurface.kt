package com.aiyu.clavis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import com.aiyu.clavis.ui.theme.CardNumberTextStyle

/** Card color, always a scheme role pair, so it follows light/dark/dynamic and keeps text contrast. */
enum class CardTint { PRIMARY, TERTIARY, SECONDARY, NIGHT, CONTAINER }

data class CardColors(val container: Color, val content: Color)

@Composable
fun CardTint.colors(): CardColors {
    val c = MaterialTheme.colorScheme
    return when (this) {
        CardTint.PRIMARY -> CardColors(c.primary, c.onPrimary)
        CardTint.TERTIARY -> CardColors(c.tertiary, c.onTertiary)
        CardTint.SECONDARY -> CardColors(c.secondary, c.onSecondary)
        CardTint.NIGHT -> CardColors(c.inverseSurface, c.inverseOnSurface)
        CardTint.CONTAINER -> CardColors(c.primaryContainer, c.onPrimaryContainer)
    }
}

const val CARD_RATIO = 1.586f

/** Ring layouts (x, y, radius twice, as fractions of the card), one per card-art variant so same-colour cards differ. */
private val RINGS = listOf(
    floatArrayOf(1.02f, -0.05f, 0.55f, 0.92f, 1.1f, 0.38f),
    floatArrayOf(-0.1f, 1.05f, 0.6f, 1.05f, 0.15f, 0.3f),
    floatArrayOf(0.5f, -0.65f, 0.75f, 1.1f, 1.2f, 0.45f),
    floatArrayOf(0.12f, -0.2f, 0.45f, 0.85f, 1.25f, 0.6f),
)

/**
 * The card face (06-UX-DESIGN.md card surface spec): ID-1 ratio, extraLarge shape, tonal gradient, chip glyph,
 * grouped number, holder and expiry. Grows taller instead of clipping at large font scales.
 * Screen readers get [description] only (masked, "Visa ending 4242"), never the digits.
 */
@Composable
fun CardSurface(
    number: String,
    holder: String,
    expiry: String,
    networkMark: String,
    validThruLabel: String,
    description: String,
    colors: CardColors,
    modifier: Modifier = Modifier,
    pattern: Int = 0,
    title: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rings = RINGS[pattern.mod(RINGS.size)]
    val shape = MaterialTheme.shapes.extraLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minHeight = maxWidth / CARD_RATIO
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(Brush.linearGradient(listOf(colors.container, lerp(colors.container, colors.content, 0.14f))))
                .drawBehind {
                    // Two soft rings: depth without shadows, drawn in the content color at low alpha.
                    drawCircle(colors.content.copy(alpha = 0.07f), radius = size.width * rings[2], center = Offset(size.width * rings[0], size.height * rings[1]))
                    drawCircle(colors.content.copy(alpha = 0.05f), radius = size.width * rings[5], center = Offset(size.width * rings[3], size.height * rings[4]))
                }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (onClick != null) role = Role.Button
                }
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                Column(Modifier.fillMaxWidth().heightIn(min = minHeight - 40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        Text(
                            networkMark,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 0.5.sp),
                        )
                    }
                    Column {
                        Chip(colors)
                        Spacer(Modifier.height(14.dp))
                        BasicText(
                            number,
                            style = CardNumberTextStyle.copy(color = colors.content),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = CardNumberTextStyle.fontSize * fontScaleCap()),
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            holder.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(validThruLabel.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = colors.content.copy(alpha = 0.8f))
                            Text(expiry, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
        }
    }
}

/** EMV chip glyph drawn from the card's own colors. */
@Composable
private fun Chip(colors: CardColors) {
    val metal = lerp(colors.container, colors.content, 0.55f)
    val line = lerp(colors.container, colors.content, 0.3f)
    Box(
        Modifier
            .size(width = 42.dp, height = 32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(metal)
            .drawBehind {
                val w = size.width
                val h = size.height
                val stroke = 1.2.dp.toPx()
                drawLine(line, Offset(0f, h / 3), Offset(w, h / 3), stroke)
                drawLine(line, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), stroke)
                drawLine(line, Offset(w / 2, 0f), Offset(w / 2, h), stroke)
            },
    )
}

/** Lets the number grow with the user's font scale up to 1.6x, then auto-size keeps it on one line. */
@Composable
private fun fontScaleCap(): Float = LocalDensity.current.fontScale.coerceAtMost(1.6f) / LocalDensity.current.fontScale
