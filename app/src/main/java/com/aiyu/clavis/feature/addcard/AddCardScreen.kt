package com.aiyu.clavis.feature.addcard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.core.card.CardNetwork
import com.aiyu.clavis.core.card.CardType
import com.aiyu.clavis.core.card.Expiry
import com.aiyu.clavis.core.card.NetworkDetector
import com.aiyu.clavis.core.card.cvvLength
import com.aiyu.clavis.core.card.formatPan
import com.aiyu.clavis.core.card.isPanValid
import com.aiyu.clavis.core.card.maxLength
import com.aiyu.clavis.core.card.validLengths
import com.aiyu.clavis.core.data.CardRepository
import com.aiyu.clavis.core.model.CardRecord
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.CardSurface
import com.aiyu.clavis.ui.components.CardTint
import com.aiyu.clavis.ui.faceStyle
import com.aiyu.clavis.ui.theme.cardArtColors
import com.aiyu.clavis.core.card.artBase
import com.aiyu.clavis.core.card.nextArtVariant
import com.aiyu.clavis.ui.components.InnerScreen
import com.aiyu.clavis.ui.components.colors
import com.aiyu.clavis.ui.defaultSeedFor
import com.aiyu.clavis.ui.labelRes
import com.aiyu.clavis.ui.mark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import com.aiyu.clavis.core.card.BinInfo
import com.aiyu.clavis.security.Bins
import com.aiyu.clavis.feature.scan.ScanHandoff
import javax.inject.Inject

@HiltViewModel
class AddCardViewModel @Inject constructor(saved: SavedStateHandle, private val repo: CardRepository, private val bins: Bins) : ViewModel() {
    val editId: String? = saved.get<String>("id")

    var number by mutableStateOf("")
    var expiry by mutableStateOf("")
    var cvv by mutableStateOf("")
    var holder by mutableStateOf("")
    var nickname by mutableStateOf("")
    var issuer by mutableStateOf("")
    var notes by mutableStateOf("")
    var type by mutableStateOf(CardType.UNKNOWN)
    var networkOverride by mutableStateOf<CardNetwork?>(null)

    /** From the offline BIN table; bank, type and country follow it until the user edits them. */
    var bin by mutableStateOf<BinInfo?>(null)
        private set
    var issuerAuto by mutableStateOf(false)
        private set
    var typeAuto by mutableStateOf(false)
        private set
    private var country: String? = null

    fun editIssuer(v: String) { issuer = v; issuerAuto = false }
    fun pickType(t: CardType) { type = if (type == t) CardType.UNKNOWN else t; typeAuto = false }

    private fun applyBin(info: BinInfo?) {
        val old = bin
        bin = info
        if (info == null) {
            if (issuerAuto) { issuer = ""; issuerAuto = false }
            if (typeAuto) { type = CardType.UNKNOWN; typeAuto = false }
            country = null
            return
        }
        if (info.issuer != null && (issuer.isBlank() || issuerAuto || issuer == old?.issuer)) { issuer = info.issuer!!; issuerAuto = true }
        if (info.type != CardType.UNKNOWN && (type == CardType.UNKNOWN || typeAuto)) { type = info.type; typeAuto = true }
        country = info.country
    }
    var seed by mutableStateOf<Int?>(null)
    var hasSavedCvv by mutableStateOf(false)
    var removeCvv by mutableStateOf(false)
    var showErrors by mutableStateOf(false)
    var saving by mutableStateOf(false)

    val network: CardNetwork get() = networkOverride ?: bin?.network?.takeIf { it != CardNetwork.UNKNOWN } ?: NetworkDetector.detect(number)
    val tintSeed: Int get() = seed ?: defaultSeedFor(network)

    /** Bank colour (D25) unless the user picked an app-theme tint. Defaults to the variant least used by the same bank. */
    var useTheme by mutableStateOf(false)
        private set
    private var art by mutableStateOf<Int?>(null)
    private var others by mutableStateOf(emptyList<Triple<String?, CardNetwork, Int?>>())
    val artVariant: Int? get() = if (useTheme) null else art ?: nextArtVariant(others, issuer.trim().ifEmpty { null }, network)

    fun pickArt(v: Int) { art = v; useTheme = false }
    fun pickTint(i: Int) { seed = i; useTheme = true }

    init {
        // Look the BIN up whenever the first six digits change. Runs in memory, nothing leaves the device.
        viewModelScope.launch {
            snapshotFlow { number.take(6) }.distinctUntilChanged().collect { prefix ->
                applyBin(if (prefix.length == 6) bins.lookup(prefix) else null)
            }
        }
        ScanHandoff.take()?.let { r -> number = r.pan; r.expiry?.let { expiry = it }; r.holder?.let { holder = it } }
        viewModelScope.launch {
            others = repo.cards.first { it != null }!!.filter { it.id != editId }.map { Triple(it.record.issuer, it.record.network, it.record.artVariant) }
        }
        if (editId != null) viewModelScope.launch {
            val card = repo.cards.first { !it.isNullOrEmpty() }!!.find { it.id == editId } ?: return@launch
            val r = card.record
            number = r.pan; expiry = "%02d%02d".format(r.expiryMonth, r.expiryYear % 100); holder = r.holderName
            nickname = r.nickname.orEmpty(); issuer = r.issuer.orEmpty(); notes = r.notes.orEmpty(); type = r.type
            networkOverride = r.network.takeIf { it != NetworkDetector.detect(r.pan) }; seed = r.colorSeed
            if (r.artVariant == null) useTheme = true else art = r.artVariant
            hasSavedCvv = card.hasCvv
        }
    }

    /** Luhn shown inline once the number is long enough, or on Save. Never blocks typing. */
    val numberError: Int?
        get() = when {
            number.isEmpty() -> if (showErrors) R.string.error_number_invalid else null
            (showErrors || number.length >= network.validLengths.last) && !isPanValid(number) -> R.string.error_number_invalid
            else -> null
        }

    val expiryError: Int?
        get() = when {
            expiry.length < 4 -> if (showErrors) R.string.error_expiry_invalid else null
            Expiry.parse(expiry) == null -> R.string.error_expiry_invalid
            else -> null
        }

    /** Soft warning: expired cards may still be worth keeping. */
    val expired: Boolean get() = Expiry.parse(expiry)?.isExpired() == true

    val cvvError: Int? get() = if (cvv.isNotEmpty() && cvv.length != network.cvvLength) R.string.error_cvv_invalid else null

    suspend fun save(): String? {
        showErrors = true
        if (numberError != null || expiryError != null || cvvError != null) return null
        val exp = Expiry.parse(expiry)!!
        saving = true
        return try {
            repo.save(
                editId,
                CardRecord(
                    nickname = nickname.trim().ifEmpty { null },
                    holderName = holder.trim(),
                    pan = number,
                    expiryMonth = exp.month,
                    expiryYear = exp.year,
                    network = network,
                    type = type,
                    issuer = issuer.trim().ifEmpty { null },
                    country = country,
                    colorSeed = tintSeed,
                    notes = notes.trim().ifEmpty { null },
                    artVariant = artVariant,
                ),
                cvv = when {
                    cvv.isNotEmpty() -> cvv
                    removeCvv -> ""
                    else -> null
                },
            ).also { cvv = "" }
        } finally {
            saving = false
        }
    }
}

/** Add / edit card (screen 6, manual): live preview on top, fields below, detected chips, Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCardScreen(onBack: () -> Unit, onSaved: (String) -> Unit, previewModifier: Modifier = Modifier, vm: AddCardViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val network = vm.network
    var cvvVisible by remember { mutableStateOf(false) }
    val noLearning = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
    val expiryFocus = remember { FocusRequester() }
    val cvvFocus = remember { FocusRequester() }
    val holderFocus = remember { FocusRequester() }

    InnerScreen(
        title = stringResource(if (vm.editId == null) R.string.add_title else R.string.add_edit_title),
        onBack = onBack,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = { scope.launch { vm.save()?.let(onSaved) } },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        val face = faceStyle(vm.issuer.trim().ifEmpty { null }, network, vm.artVariant, vm.tintSeed)
        CardSurface(
            number = formatPan(vm.number.padEnd(previewLength(network, vm.number.length), '•'), network),
            holder = vm.holder.ifBlank { stringResource(R.string.add_holder_placeholder) },
            expiry = Expiry.parse(vm.expiry)?.short ?: stringResource(R.string.add_expiry_placeholder),
            networkMark = network.mark(),
            validThruLabel = stringResource(R.string.detail_valid_thru),
            description = stringResource(R.string.add_preview_description),
            colors = face.colors,
            pattern = face.pattern,
            title = vm.nickname.ifBlank { vm.issuer },
            modifier = previewModifier,
        )
        Spacer(Modifier.height(12.dp))
        ColorPicker(vm)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = vm.number,
            onValueChange = { v -> val d = v.filter(Char::isDigit).take(network.maxLength); vm.number = d
                // Complete and valid: jump to expiry. Visa may be 13/16/19, so stop at 16 there.
                if (isPanValid(d) && (d.length >= 16 || d.length == NetworkDetector.detect(d).maxLength)) expiryFocus.requestFocus()
            },
            label = { Text(stringResource(R.string.field_number)) },
            visualTransformation = PanTransformation(network),
            keyboardOptions = noLearning,
            isError = vm.numberError != null,
            supportingText = vm.numberError?.let { { ErrorText(it) } },
            trailingIcon = { if (vm.number.isNotEmpty() && vm.numberError == null && isPanValid(vm.number)) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Side by side normally; stacks when the screen is narrow or the font is large.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            OutlinedTextField(
                value = vm.expiry,
                onValueChange = { v -> val d = v.filter(Char::isDigit).take(4); vm.expiry = d; if (Expiry.parse(d) != null) cvvFocus.requestFocus() },
                label = { Text(stringResource(R.string.field_expiry_short)) },
                placeholder = { Text(stringResource(R.string.add_expiry_placeholder)) },
                visualTransformation = ExpiryTransformation,
                keyboardOptions = noLearning,
                isError = vm.expiryError != null,
                supportingText = vm.expiryError?.let { e -> { ErrorText(e) } }
                    ?: if (vm.expired) { { Text(stringResource(R.string.error_expiry_past)) } } else null,
                singleLine = true,
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).focusRequester(expiryFocus),
            )
            OutlinedTextField(
                value = vm.cvv,
                onValueChange = { v -> val d = v.filter(Char::isDigit).take(network.cvvLength); vm.cvv = d; if (d.length == network.cvvLength) holderFocus.requestFocus() },
                label = { Text(stringResource(R.string.detail_cvv)) },
                placeholder = if (vm.hasSavedCvv && !vm.removeCvv) { { Text(stringResource(R.string.add_cvv_saved)) } } else null,
                visualTransformation = if (cvvVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = noLearning,
                isError = vm.cvvError != null,
                supportingText = vm.cvvError?.let { { ErrorText(it) } } ?: { Text(stringResource(R.string.field_optional)) },
                trailingIcon = {
                    IconButton(onClick = { cvvVisible = !cvvVisible }) {
                        Icon(
                            if (cvvVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (cvvVisible) R.string.detail_hide else R.string.detail_reveal),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).focusRequester(cvvFocus),
            )
        }
        if (vm.cvv.isNotEmpty()) InfoNote(stringResource(R.string.cvv_storage_warning))
        if (vm.hasSavedCvv) TextButton(onClick = { vm.removeCvv = !vm.removeCvv; vm.cvv = "" }) {
            Text(stringResource(if (vm.removeCvv) R.string.add_keep_cvv else R.string.add_remove_cvv))
        }
        val textOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        OutlinedTextField(
            value = vm.holder, onValueChange = { vm.holder = it.take(40) },
            label = { Text(stringResource(R.string.field_holder)) },
            keyboardOptions = textOptions.copy(capitalization = KeyboardCapitalization.Characters),
            singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(holderFocus),
        )
        OutlinedTextField(
            value = vm.nickname, onValueChange = { vm.nickname = it.take(30) },
            label = { Text(stringResource(R.string.field_nickname)) },
            keyboardOptions = textOptions, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detected_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetworkChip(network) { vm.networkOverride = it }
            listOf(CardType.CREDIT, CardType.DEBIT, CardType.PREPAID).forEach { t ->
                FilterChip(
                    selected = vm.type == t,
                    onClick = { vm.pickType(t) },
                    label = { Text(stringResource(t.labelRes())) },
                    leadingIcon = if (vm.type == t) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
                )
            }
        }
        OutlinedTextField(
            value = vm.issuer, onValueChange = { vm.editIssuer(it.take(40)) },
            supportingText = if (vm.issuerAuto) { { Text(stringResource(R.string.add_detected_from_number)) } } else null,
            label = { Text(stringResource(R.string.field_bank)) },
            keyboardOptions = textOptions, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.notes, onValueChange = { vm.notes = it.take(500) },
            label = { Text(stringResource(R.string.field_notes)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorText(res: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(res))
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun NetworkChip(network: CardNetwork, onPick: (CardNetwork?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("${stringResource(R.string.field_network)}: ${stringResource(network.labelRes())}") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.add_network_auto)) }, onClick = { onPick(null); open = false })
            CardNetwork.entries.filter { it != CardNetwork.UNKNOWN }.forEach { n ->
                DropdownMenuItem(text = { Text(stringResource(n.labelRes())) }, onClick = { onPick(n); open = false })
            }
        }
    }
}

/** Bank colours (four variants of the bank's colour, D25) above the app-theme tints. One selection across both rows. */
@Composable
private fun ColorPicker(vm: AddCardViewModel) {
    val base = artBase(vm.issuer.trim().ifEmpty { null }, vm.network)
    val artNames = listOf(R.string.art_classic, R.string.art_deep, R.string.art_light, R.string.art_graphite)
    val tintNames = listOf(R.string.tint_primary, R.string.tint_tertiary, R.string.tint_secondary, R.string.tint_night, R.string.tint_soft)
    Text(stringResource(R.string.add_color_bank), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwatchRow {
        artNames.forEachIndexed { i, name ->
            Swatch(cardArtColors(base, i).container, stringResource(name), selected = vm.artVariant == i, onClick = { vm.pickArt(i) })
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.add_color_theme), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwatchRow {
        CardTint.entries.forEachIndexed { i, tint ->
            Swatch(tint.colors().container, stringResource(tintNames[i]), selected = vm.useTheme && vm.tintSeed.mod(CardTint.entries.size) == i, onClick = { vm.pickTint(i) })
        }
    }
}

/** Swatches share the width evenly, so the row never squeezes its label (dense screens, big fonts). */
@Composable
private fun SwatchRow(content: @Composable RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                .padding(if (selected) 5.dp else 0.dp)
                .background(color, CircleShape),
        )
    }
}

/** Groups digits per network as they are typed; the stored value stays digits only. */
private class PanTransformation(private val network: CardNetwork) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = formatPan(digits, network)
        val o2t = IntArray(digits.length + 1)
        var d = 0
        formatted.forEachIndexed { i, c -> if (c != ' ') o2t[d++] = i }
        o2t[digits.length] = formatted.length
        return TransformedText(AnnotatedString(formatted), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int) = formatted.take(offset.coerceIn(0, formatted.length)).count { it != ' ' }
        })
    }

    override fun equals(other: Any?) = other is PanTransformation && other.network == network
    override fun hashCode() = network.hashCode()
}

/** "0829" shows as "08/29". */
private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val out = if (t.length > 2) t.substring(0, 2) + "/" + t.substring(2) else t
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 2 || t.length <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= 2) offset else (offset - 1).coerceAtMost(t.length)
        })
    }
}

/** Placeholder length for the preview: 16 when the network allows it (Amex 15), longer only once typed. */
private fun previewLength(network: CardNetwork, typed: Int): Int {
    val lengths = network.validLengths
    val usual = if (16 in lengths) 16 else lengths.last
    return lengths.firstOrNull { it >= maxOf(typed, usual) } ?: typed
}
