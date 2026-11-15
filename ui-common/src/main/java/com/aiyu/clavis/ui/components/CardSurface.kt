package com.aiyu.clavis.ui.components

import androidx.compose.foundation.background
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
    tint: CardTint,
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = tint.colors()
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
                    drawCircle(colors.content.copy(alpha = 0.07f), radius = size.width * 0.55f, center = Offset(size.width * 1.02f, size.height * -0.05f))
                    drawCircle(colors.content.copy(alpha = 0.05f), radius = size.width * 0.38f, center = Offset(size.width * 0.92f, size.height * 1.1f))
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
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            networkMark,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 0.5.sp),
                        )
                    }
                    Column {
                        Chip(colors)
                        Spacer(Modifier.height(14.dp))
                        Text(number, style = CardNumberTextStyle, maxLines = 2)
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
