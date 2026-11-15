package com.aiyu.clavis.feature.detail

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.clavis.core.model.Card
import com.aiyu.clavis.feature.addcard.InfoNote
import com.aiyu.clavis.feature.share.ShareColors
import com.aiyu.clavis.feature.share.ShareLabels
import com.aiyu.clavis.feature.share.ShareOptions
import com.aiyu.clavis.feature.share.ShareRenderer
import com.aiyu.clavis.feature.share.ShareStyle
import com.aiyu.clavis.feature.share.Sharer
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.mark
import com.aiyu.clavis.ui.faceStyle
import com.aiyu.clavis.ui.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Copy step by step" (guided paste): each tap copies the next field and ticks the previous one. */
@Composable
fun CopySheet(card: Card, onCopy: (CopyField) -> Unit, onDismiss: () -> Unit) {
    val steps = buildList {
        add(CopyField.NUMBER to R.string.field_number)
        add(CopyField.EXPIRY to R.string.detail_expiry)
        if (card.hasCvv) add(CopyField.CVV to R.string.detail_cvv)
        if (card.record.holderName.isNotBlank()) add(CopyField.HOLDER to R.string.field_holder)
    }
    var next by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.copy_step_by_step), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Text(stringResource(R.string.copy_step_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            steps.forEachIndexed { i, (field, label) ->
                val done = i < next
                val current = i == next
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    supportingContent = { Text(stringResource(if (done) R.string.copy_step_done else if (current) R.string.copy_step_tap else R.string.copy_step_waiting)) },
                    leadingContent = {
                        Icon(
                            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) MaterialTheme.colorScheme.tertiary else if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.large)
                        .then(Modifier.clickableRow { onCopy(field); next = maxOf(next, i + 1) }),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onCopy(CopyField.ALL) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Notes, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_all))
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/**
 * Share options (05-SHARE-AND-COPY.md): CVV off by default and reset every time (state lives in this sheet only),
 * name on, image style, live preview of exactly what is sent, warning, Share / Share text only.
 */
@Composable
fun ShareSheet(card: Card, vm: DetailViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(ShareOptions()) }
    var cvv by remember { mutableStateOf<String?>(null) }
    val r = card.record
    val cardColors = r.faceStyle().colors
    val scheme = MaterialTheme.colorScheme
    val colors = ShareColors(cardColors.container.toArgb(), cardColors.content.toArgb(), scheme.surface.toArgb(), scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb())
    val text = shareText(options.includeCvv, options.includeHolder)
    val title = r.title()
    val mark = r.network.mark()
    val validThru = stringResource(R.string.detail_valid_thru)
    val cvvLabel = stringResource(R.string.detail_cvv)
    val chooser = stringResource(R.string.share_chooser_title)
    val payload = text(card, cvv)
    val labels = ShareLabels(title, mark, validThru, cvvLabel, null, null, payload.lines())

    val preview by produceState<Bitmap?>(null, options, cvv, payload, colors) {
        value = withContext(Dispatchers.Default) { ShareRenderer.render(r, cvv, options, colors, labels) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.share_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            preview?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
                )
            }
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShareStyle.entries.forEachIndexed { i, style ->
                    SegmentedButton(
                        selected = options.style == style,
                        onClick = { options = options.copy(style = style) },
                        shape = SegmentedButtonDefaults.itemShape(i, ShareStyle.entries.size),
                    ) { Text(stringResource(if (style == ShareStyle.CARD) R.string.share_style_card else R.string.share_style_minimal)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (card.hasCvv) SwitchRow(stringResource(R.string.share_include_cvv), options.includeCvv) { on ->
                scope.launch {
                    if (on) {
                        cvv = vm.loadCvv() ?: return@launch
                    } else cvv = null
                    options = options.copy(includeCvv = on)
                }
            }
            SwitchRow(stringResource(R.string.share_include_holder), options.includeHolder) { options = options.copy(includeHolder = it) }
            AnimatedVisibility(options.includeCvv) {
                Text(
                    stringResource(R.string.share_cvv_included),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            InfoNote(stringResource(R.string.share_warning))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (!vm.requireFresh()) return@launch
                        Sharer.share(context, payload, preview, chooser)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.share_action), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = { scope.launch { if (vm.requireFresh()) { Sharer.share(context, payload, null, chooser); onDismiss() } } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.share_text_only)) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
