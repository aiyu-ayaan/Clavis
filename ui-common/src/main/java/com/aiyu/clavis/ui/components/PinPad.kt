package com.aiyu.clavis.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.theme.rememberReducedMotion

/** Filled dots for digits entered. Shakes when [errorTick] changes (wrong PIN), also announced via [message]. */
@Composable
fun PinDots(entered: Int, length: Int, errorTick: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        if (!reduced) shake.animateTo(0f, keyframes {
            durationMillis = 420
            -18f at 60; 16f at 120; -12f at 180; 9f at 240; -5f at 300; 2f at 360
        })
    }
    val desc = stringResource(R.string.pin_digits_entered, entered, length)
    Row(
        modifier
            .graphicsLayer { translationX = shake.value * density }
            .clearAndSetSemantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { i ->
            val filled = i < entered
            val scale by animateFloatAsState(if (filled) 1f else 0.62f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            val color by animateColorAsState(
                when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "dotColor",
            )
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(color, CircleShape),
            )
        }
    }
}

/** 3×4 number pad with 72 dp keys. Keys morph from circle to squircle while pressed (expressive shape). */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startKey: (@Composable () -> Unit)? = null,
) {
    val key = pinKeySize()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (key < KEY) 8.dp else 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d -> DigitKey(d, enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(key), contentAlignment = Alignment.Center) { startKey?.invoke() }
            DigitKey('0', enabled) { onDigit('0') }
            PadKey(enabled, onDelete, filled = false, label = stringResource(R.string.pin_key_delete)) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
            }
        }
    }
}

private val KEY = 72.dp

/** 72 dp keys (06-UX-DESIGN.md); 60 dp on short windows (dense screens, big fonts) so the pad fits without scrolling. */
@Composable
fun pinKeySize(): androidx.compose.ui.unit.Dp {
    val heightDp = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    return if (heightDp < 720.dp || fontScale > 1.25f) 60.dp else KEY
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    PadKey(enabled, onClick, filled = true, label = digit.toString()) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, filled: Boolean, label: String, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(if (pressed) 28 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    Surface(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = if (filled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = Modifier.size(pinKeySize()).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Full PIN screen body: header slot (the mark), title, status line, dots and pad. State lives with the caller.
 * [message] is a live region so wrong-PIN and lockout text is read out.
 */
@Composable
fun PinEntry(
    title: String,
    message: String?,
    isError: Boolean,
    errorTick: Int,
    entered: Int,
    length: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    header: @Composable () -> Unit = {},
    startKey: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        header()
        Spacer(Modifier.height(if (pinKeySize() < KEY) 8.dp else 20.dp))
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(title, transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) }, label = "title") {
            Text(it, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .heightIn(min = 40.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        PinDots(entered, length, errorTick, isError)
        Spacer(Modifier.height(if (pinKeySize() < KEY) 16.dp else 28.dp))
        PinPad(onDigit, onDelete, enabled = enabled, startKey = startKey)
        footer()
    }
}
