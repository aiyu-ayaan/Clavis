package com.aiyu.clavis.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.aiyu.clavis.core.card.CardGrouping
import com.aiyu.clavis.core.card.CardLayout
import com.aiyu.clavis.core.card.CardNetwork
import com.aiyu.clavis.core.card.CardType
import com.aiyu.clavis.core.card.groupForDisplay
import com.aiyu.clavis.core.card.lastFour
import com.aiyu.clavis.core.card.maskPan
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.CardSurface
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.colors
import com.aiyu.clavis.ui.components.sharedBoundsOrSelf
import com.aiyu.clavis.ui.labelRes
import com.aiyu.clavis.ui.mark
import com.aiyu.clavis.ui.maskedDescription
import com.aiyu.clavis.ui.theme.ClavisMotion
import com.aiyu.clavis.ui.theme.rememberReducedMotion
import com.aiyu.clavis.ui.tint
import com.aiyu.clavis.ui.title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One home section: a header label (null when ungrouped), its index-rail letter, and its cards. */
data class Section(val label: String?, val rail: String?, val cards: List<Card>)

/** Splits cards into contact-style sections, A to Z, with the "unknown" bucket last. */
@Composable
fun rememberSections(cards: List<Card>, grouping: CardGrouping): List<Section> {
    val res = LocalResources.current
    return remember(cards, grouping, res) {
        val unknown = when (grouping) {
            CardGrouping.NONE -> return@remember listOf(Section(null, null, cards))
            CardGrouping.NAME -> "#"
            CardGrouping.NETWORK -> res.getString(R.string.network_unknown)
            CardGrouping.BANK -> res.getString(R.string.view_unknown_bank)
            CardGrouping.TYPE -> res.getString(R.string.type_unknown)
            CardGrouping.EXPIRY -> res.getString(R.string.type_unknown)
        }
        groupForDisplay(cards) { c ->
            val r = c.record
            when (grouping) {
                CardGrouping.NAME -> (r.nickname?.takeIf { it.isNotBlank() } ?: r.issuer?.takeIf { it.isNotBlank() } ?: res.getString(r.network.labelRes()))
                    .trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercase()
                CardGrouping.NETWORK -> r.network.takeIf { it != CardNetwork.UNKNOWN }?.let { res.getString(it.labelRes()) }
                CardGrouping.BANK -> r.issuer?.trim()?.takeIf { it.isNotEmpty() }
                CardGrouping.TYPE -> r.type.takeIf { it != CardType.UNKNOWN }?.let { res.getString(it.labelRes()) }
                CardGrouping.EXPIRY -> r.expiryYear.takeIf { it > 0 }?.toString()
                CardGrouping.NONE -> null
            }
        }.map { (label, list) ->
            val shown = label ?: unknown
            val rail = when {
                label == null -> "#"
                grouping == CardGrouping.EXPIRY -> "'" + label.takeLast(2)
                else -> label.take(1).uppercase()
            }
            Section(shown, rail, list)
        }
    }
}

/**
 * Home card list in the chosen [layout]. With sections, headers stick to the top and a contacts-style
 * index rail on the right jumps between them (tap or drag).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardCollection(
    sections: List<Section>,
    layout: CardLayout,
    listState: LazyListState,
    bottomPadding: Dp,
    onOpenCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = sections.firstOrNull()?.label != null
    // Wallet: the card pulled forward. The last card of each section is always fully shown.
    var front by rememberSaveable { mutableStateOf<String?>(null) }
    val rail = remember(sections) {
        var index = 0
        buildList<Pair<String, Int>> {
            sections.forEach { s ->
                if (s.rail != null && none { it.first == s.rail }) add(s.rail to index)
                index += 1 + s.cards.size
            }
        }
    }
    val showRail = grouped && rail.size > 1
    val gutter = PaddingValues(start = 16.dp, end = if (showRail) 44.dp else 16.dp)
    // Entrance plays only while the list first appears. Items composed later by scrolling show at once:
    // replaying it on a fast fling left invisible gaps in the wallet stack. Saveable, so coming back from
    // a card does not replay it either: the shared card would land, drop to the fade's alpha and flicker.
    var entered by rememberSaveable { mutableStateOf(false) }
    val entering = !entered
    LaunchedEffect(Unit) {
        delay(ENTRANCE_WINDOW_MILLIS)
        entered = true
    }

    val collapsible = layout == CardLayout.WALLET && front != null
    // Back or a tap on empty space puts the forward card back in the stack. Taps on cards are consumed first.
    BackHandler(enabled = collapsible) { front = null }
    Box(
        modifier
            .fillMaxSize()
            .then(if (collapsible) Modifier.pointerInput(Unit) { detectTapGestures { front = null } } else Modifier),
    ) {
        LazyColumn(
            state = listState,
            // Side gutters live on each item, not here, so sticky headers can span the full width and hide what scrolls under them.
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(
                when (layout) {
                    CardLayout.CARDS -> 16.dp
                    CardLayout.LIST -> 4.dp
                    CardLayout.WALLET -> 0.dp
                },
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            sections.forEach { section ->
                if (section.label != null) {
                    stickyHeader(key = "header-${section.label}", contentType = "header") {
                        SectionHeader(section.label, section.cards.size, gutter)
                    }
                }
                when (layout) {
                    CardLayout.CARDS -> itemsIndexed(section.cards, key = { _, c -> c.id }, contentType = { _, _ -> "card" }) { index, card ->
                        CardFace(
                            card,
                            onClick = { onOpenCard(card.id) },
                            modifier = Modifier
                                .padding(gutter)
                                .widthIn(max = 520.dp)
                                .animateItem()
                                .staggeredEntrance(index, entering)
                                .sharedBoundsOrSelf(SharedKeys.card(card.id), MaterialTheme.shapes.extraLarge),
                        )
                    }
                    CardLayout.LIST -> items(section.cards, key = { it.id }, contentType = { "row" }) { card ->
                        CardRow(card, onClick = { onOpenCard(card.id) }, modifier = Modifier.padding(gutter).widthIn(max = 520.dp).animateItem())
                    }
                    CardLayout.WALLET -> itemsIndexed(section.cards, key = { _, c -> c.id }, contentType = { _, _ -> "wallet" }) { index, card ->
                        val expanded = index == section.cards.lastIndex || front == card.id
                        WalletCard(
                            card,
                            expanded = expanded,
                            onClick = { if (expanded) onOpenCard(card.id) else front = card.id },
                            modifier = Modifier.padding(gutter).widthIn(max = 520.dp).animateItem().staggeredEntrance(index, entering),
                        )
                    }
                }
            }
        }
        if (showRail) {
            val scope = rememberCoroutineScope()
            val active by remember(rail) {
                derivedStateOf { rail.indexOfLast { it.second <= listState.firstVisibleItemIndex }.coerceAtLeast(0) }
            }
            SectionRail(
                letters = rail.map { it.first },
                active = active,
                onJump = { i -> scope.launch { listState.scrollToItem(rail[i].second) } },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun CardFace(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = card.record
    CardSurface(
        number = maskPan(r.pan, r.network),
        holder = r.holderName,
        expiry = r.expiry.short,
        networkMark = r.network.mark(),
        validThruLabel = stringResource(R.string.detail_valid_thru),
        description = r.maskedDescription(),
        tint = r.tint,
        title = r.title(),
        onClick = onClick,
        modifier = modifier,
    )
}

/** Compact row: mini card swatch, title, last four and expiry, credit/debit. */
@Composable
private fun CardRow(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val r = card.record
    val colors = r.tint.colors()
    val description = r.maskedDescription()
    ListItem(
        headlineContent = { Text(r.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("•••• ${lastFour(r.pan)}  ·  ${r.expiry.short}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")) },
        leadingContent = {
            Box(
                Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .sharedBoundsOrSelf(SharedKeys.card(card.id), MaterialTheme.shapes.small)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.container)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                BasicText(
                    r.network.mark(),
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.content, fontWeight = FontWeight.Black),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 11.sp),
                )
            }
        },
        trailingContent = r.type.takeIf { it != CardType.UNKNOWN }?.let { { Text(stringResource(it.labelRes()), style = MaterialTheme.typography.labelMedium) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

/**
 * Wallet stack: a collapsed card shows only its top strip and the next card overlaps the rest.
 * Tapping brings it forward (the strip springs open to the full card); tapping again opens it.
 */
@Composable
private fun WalletCard(card: Card, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fraction by animateFloatAsState(if (expanded) 1f else 0f, if (reduced) androidx.compose.animation.core.snap() else spec, label = "wallet")
    val shape = MaterialTheme.shapes.extraLarge
    val shadowColor = MaterialTheme.colorScheme.scrim
    CardFace(
        card,
        onClick = onClick,
        modifier = modifier
            .peek(fraction = { fraction }, peek = 72.dp, gap = 16.dp)
            .shadow(6.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .sharedBoundsOrSelf(SharedKeys.card(card.id), shape),
    )
}

/** Measures the card at full height but reports only [peek] (collapsed) up to full height + [gap] (expanded). */
private fun Modifier.peek(fraction: () -> Float, peek: Dp, gap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = lerp(peek.roundToPx().toFloat(), (placeable.height + gap.roundToPx()).toFloat(), fraction()).toInt().coerceAtLeast(peek.roundToPx())
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/** Opaque and full width, so the card scrolling under it (and its shadow) never shows at the edges. */
@Composable
private fun SectionHeader(label: String, count: Int, gutter: PaddingValues) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(gutter)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Contacts-style index: tap a letter or drag along the rail to jump to that section. */
@Composable
private fun SectionRail(letters: List<String>, active: Int, onJump: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val jumpLabels = letters.map { stringResource(R.string.view_jump_to, it) }
    Column(
        modifier
            .width(32.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(letters) {
                awaitEachGesture {
                    var last = -1
                    fun pick(y: Float) {
                        val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        if (i != last) {
                            last = i
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onJump(i)
                        }
                    }
                    val down = awaitFirstDown()
                    pick(down.position.y)
                    drag(down.id) { change -> pick(change.position.y); change.consume() }
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, letter ->
            Text(
                letter,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (i == active) FontWeight.Black else FontWeight.Medium),
                color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .semantics { contentDescription = jumpLabels[i]; onClick { onJump(i); true } },
            )
        }
    }
}

/** Long enough for the first screenful to start its stagger (8 steps of 45 ms). */
private const val ENTRANCE_WINDOW_MILLIS = 500L

/**
 * Rises and fades in, each card 45 ms after the one above (stagger-sequence). Once per composition,
 * and only if [enabled] when the item is first composed.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, enabled: Boolean): Modifier {
    val animate = remember { enabled }
    if (!animate || rememberReducedMotion()) return this
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * ClavisMotion.STAGGER_MILLIS.toLong())
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 48.dp.toPx()
    }
}
