package com.aiyu.clavis.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.clavis.core.card.CardType
import com.aiyu.clavis.core.card.formatPan
import com.aiyu.clavis.core.card.maskPan
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.CardSurface
import com.aiyu.clavis.ui.components.InnerScreen
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedBoundsOrSelf
import com.aiyu.clavis.ui.labelRes
import com.aiyu.clavis.ui.mark
import com.aiyu.clavis.ui.maskedDescription
import com.aiyu.clavis.ui.tint
import com.aiyu.clavis.ui.title
import kotlinx.coroutines.launch

private const val REVEAL_MS = 20_000

/** Card detail (screen 7): masked card, Reveal / Copy / Share / Delete, field rows with copy, metadata. */
@Composable
fun CardDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val card by vm.card.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    var copySheet by remember { mutableStateOf(false) }
    var shareSheet by remember { mutableStateOf(false) }
    val countdown = remember { Animatable(1f) }
    val texts = copyMessages()
    val clearNow = stringResource(R.string.copy_clear_now)
    val clearsIn = stringResource(R.string.copy_clears_in)
    val allText = shareText(includeCvv = false, includeHolder = true)

    // Auto-mask after 20 s and whenever the screen goes to the background.
    LaunchedEffect(vm.revealed) {
        if (vm.revealed) {
            countdown.snapTo(1f)
            countdown.animateTo(0f, tween(REVEAL_MS, easing = LinearEasing))
            vm.hide()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.hide() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); vm.hide() }
    }

    fun copy(field: CopyField) = scope.launch {
        val delay = vm.copy(field) { allText(it, null) } ?: return@launch
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val msg = texts.getValue(field) + if (delay > 0) " · " + clearsIn.format(delay) else ""
        if (snackbar.showSnackbar(msg, actionLabel = clearNow, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.clearClipboard()
    }

    val c = card ?: run {
        // Deleted, or still decrypting: an empty frame with the shared bounds keeps the transition target.
        InnerScreen(title = "", onBack = onBack) {}
        return
    }
    val r = c.record

    InnerScreen(
        title = r.title(),
        subtitle = listOfNotNull(stringResource(r.network.labelRes()), r.type.takeIf { it != CardType.UNKNOWN }?.let { stringResource(it.labelRes()) }).joinToString(" · "),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { onEdit(c.id) }) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit)) }
        },
    ) {
        CardSurface(
            number = if (vm.revealed) formatPan(r.pan, r.network) else maskPan(r.pan, r.network),
            holder = r.holderName,
            expiry = r.expiry.short,
            networkMark = r.network.mark(),
            validThruLabel = stringResource(R.string.detail_valid_thru),
            description = r.maskedDescription(),
            tint = r.tint,
            title = r.title(),
            modifier = Modifier.sharedBoundsOrSelf(SharedKeys.card(c.id), MaterialTheme.shapes.extraLarge),
        )
        AnimatedVisibility(vm.revealed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            LinearProgressIndicator(progress = { countdown.value }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
        }
        val revealState = stringResource(if (vm.revealed) R.string.detail_revealed_announce else R.string.detail_hidden_announce)
        Text(revealState, modifier = Modifier.size(0.dp).semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(
                if (vm.revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                stringResource(if (vm.revealed) R.string.detail_hide else R.string.detail_reveal),
            ) { scope.launch { if (vm.revealed) vm.hide() else vm.reveal() } }
            ActionButton(Icons.Rounded.ContentCopy, stringResource(R.string.detail_copy)) { copySheet = true }
            if (!settings.neverShare) ActionButton(Icons.Rounded.Share, stringResource(R.string.detail_share)) {
                scope.launch { if (vm.requireFresh()) shareSheet = true }
            }
            ActionButton(Icons.Rounded.Delete, stringResource(R.string.action_delete), destructive = true) { confirmDelete = true }
        }
        Spacer(Modifier.height(20.dp))

        Group(stringResource(R.string.detail_section_card)) {
            FieldRow(stringResource(R.string.field_number), if (vm.revealed) formatPan(r.pan, r.network) else maskPan(r.pan, r.network),
                onClick = { copy(CopyField.NUMBER) }, onLongClick = { copy(CopyField.NUMBER_SPACED) }, copyLabel = stringResource(R.string.copy_number))
            FieldRow(stringResource(R.string.detail_expiry), r.expiry.short,
                onClick = { copy(CopyField.EXPIRY) }, onLongClick = { copy(CopyField.EXPIRY_LONG) }, copyLabel = stringResource(R.string.copy_expiry))
            if (r.holderName.isNotBlank()) FieldRow(stringResource(R.string.field_holder), r.holderName,
                onClick = { copy(CopyField.HOLDER) }, copyLabel = stringResource(R.string.copy_holder))
            if (c.hasCvv) FieldRow(stringResource(R.string.detail_cvv), vm.cvv ?: "•••",
                onClick = { copy(CopyField.CVV) }, copyLabel = stringResource(R.string.copy_cvv))
        }
        Text(stringResource(R.string.detail_copy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
        Group(stringResource(R.string.detected_label)) {
            InfoRow(stringResource(R.string.field_network), stringResource(r.network.labelRes()))
            InfoRow(stringResource(R.string.field_type), stringResource(r.type.labelRes()))
            r.issuer?.let { InfoRow(stringResource(R.string.field_bank), it) }
        }
        r.notes?.let {
            Spacer(Modifier.height(12.dp))
            Group(stringResource(R.string.field_notes)) { InfoRow(it, null) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.detail_delete_title)) },
        text = { Text(stringResource(R.string.detail_delete_body)) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; scope.launch { if (vm.delete()) onBack() } }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (copySheet) CopySheet(c, onCopy = { copy(it) }, onDismiss = { copySheet = false })
    if (shareSheet) ShareSheet(c, vm, onDismiss = { shareSheet = false })
}

@Composable
private fun copyMessages() = mapOf(
    CopyField.NUMBER to stringResource(R.string.copy_done_number),
    CopyField.NUMBER_SPACED to stringResource(R.string.copy_done_number),
    CopyField.EXPIRY to stringResource(R.string.copy_done_expiry),
    CopyField.EXPIRY_LONG to stringResource(R.string.copy_done_expiry),
    CopyField.HOLDER to stringResource(R.string.copy_done_holder),
    CopyField.CVV to stringResource(R.string.copy_done_cvv),
    CopyField.ALL to stringResource(R.string.copy_done_all),
)

/** Builds the plain-text payload (05-SHARE-AND-COPY.md text format). CVV line absent, not blank, when off. */
@Composable
fun shareText(includeCvv: Boolean, includeHolder: Boolean): (Card, String?) -> String {
    val cardL = stringResource(R.string.share_text_card)
    val nameL = stringResource(R.string.share_text_name)
    val numL = stringResource(R.string.share_text_number)
    val expL = stringResource(R.string.share_text_expiry)
    val cvvL = stringResource(R.string.share_text_cvv)
    val network = mapOf(*com.aiyu.clavis.core.card.CardNetwork.entries.map { it to stringResource(it.labelRes()) }.toTypedArray())
    val types = mapOf(*CardType.entries.map { it to stringResource(it.labelRes()) }.toTypedArray())
    return { card, cvv ->
        val r = card.record
        val name = listOfNotNull(r.issuer, network[r.network], r.type.takeIf { it != CardType.UNKNOWN }?.let { types[it] }).joinToString(" ")
        listOfNotNull(
            cardL.format(name),
            if (includeHolder && r.holderName.isNotBlank()) nameL.format(r.holderName) else null,
            numL.format(formatPan(r.pan, r.network)),
            expL.format(r.expiry.short),
            if (includeCvv && cvv != null) cvvL.format(cvv) else null,
        ).joinToString("\n")
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(width = 72.dp, height = 56.dp),
            shapes = IconButtonDefaults.shapes(),
            colors = if (destructive) IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) else IconButtonDefaults.filledTonalIconButtonColors(),
        ) { Icon(icon, contentDescription = null) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Rounded group of rows, the expressive settings-list look. */
@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit, copyLabel: String, onLongClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(value, style = com.aiyu.clavis.ui.theme.CardNumberTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize)) },
        overlineContent = { Text(label) },
        trailingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = copyLabel)
            .semantics { },
    )
}

@Composable
private fun InfoRow(label: String, value: String?) {
    ListItem(
        headlineContent = { Text(value ?: label) },
        overlineContent = value?.let { { Text(label) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

