package com.aiyu.clavis.feature.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPasteOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.BuildConfig
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.data.CardRepository
import com.aiyu.clavis.core.settings.Settings
import com.aiyu.clavis.core.settings.SettingsStore
import com.aiyu.clavis.core.settings.ThemeMode
import com.aiyu.clavis.feature.detail.Group
import com.aiyu.clavis.security.AuthGate
import com.aiyu.clavis.security.authenticate
import com.aiyu.clavis.security.canUseBiometric
import com.aiyu.clavis.security.hasBiometricHardware
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.InnerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val vault: Vault,
    private val repo: CardRepository,
    private val gate: AuthGate,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    var biometric by mutableStateOf(vault.biometricEnabled)
        private set

    fun update(t: (Settings) -> Settings) = viewModelScope.launch { store.update(t) }

    suspend fun requireFresh() = gate.requireFresh()
    fun enrollCipher(): Cipher = vault.biometricEnrollCipher()
    fun enableBiometric(c: Cipher) { biometric = vault.enableBiometric(c) }
    fun disableBiometric() { vault.disableBiometric(); biometric = false }

    /** Erase: cards, wrapped keys, Keystore entries, settings. Fresh auth first. */
    suspend fun wipe(): Boolean {
        if (!gate.requireFresh()) return false
        repo.deleteAll()
        store.clear()
        vault.wipe()
        return true
    }
}

/** Settings (screen 9): Security, Privacy, Appearance, About. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onChangePin: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }

    InnerScreen(title = stringResource(R.string.settings_title), onBack = onBack) {
        Group(stringResource(R.string.settings_section_security)) {
            Row(Icons.Rounded.Password, stringResource(R.string.settings_change_pin), onClick = onChangePin)
            if (context.hasBiometricHardware()) {
                ToggleRow(
                    Icons.Rounded.Fingerprint,
                    stringResource(R.string.settings_biometric),
                    if (context.canUseBiometric()) null else stringResource(R.string.settings_biometric_none),
                    checked = vm.biometric,
                    enabled = vm.biometric || context.canUseBiometric(),
                ) { on ->
                    scope.launch {
                        if (!vm.requireFresh()) return@launch
                        if (on) activity.authenticate(vm.enrollCipher())?.let(vm::enableBiometric) else vm.disableBiometric()
                    }
                }
            }
            Row(Icons.Rounded.Timer, stringResource(R.string.settings_auto_lock), autoLockLabel(s.autoLockMs)) { dialog = "lock" }
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.settings_section_privacy)) {
            Row(Icons.Rounded.ContentPasteOff, stringResource(R.string.settings_clipboard_clear), clipboardLabel(s.clipboardClearSec)) { dialog = "clip" }
            ToggleRow(Icons.Rounded.Block, stringResource(R.string.settings_never_share), stringResource(R.string.settings_never_share_body), s.neverShare) { on ->
                vm.update { it.copy(neverShare = on) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.settings_section_appearance)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                leadingContent = { Icon(Icons.Rounded.Brightness6, contentDescription = null) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = s.theme == m,
                                onClick = { vm.update { it.copy(theme = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) {
                                Text(stringResource(when (m) { ThemeMode.SYSTEM -> R.string.settings_theme_system; ThemeMode.LIGHT -> R.string.settings_theme_light; ThemeMode.DARK -> R.string.settings_theme_dark }))
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleRow(Icons.Rounded.Palette, stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), s.dynamicColor) { on ->
                    vm.update { it.copy(dynamicColor = on) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        UpdatesGroup(s.updatesEnabled, onEnabled = { on -> vm.update { it.copy(updatesEnabled = on) } })
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.settings_section_about)) {
            Row(Icons.Rounded.Info, stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            Row(Icons.Rounded.CloudOff, stringResource(R.string.settings_no_internet), stringResource(R.string.settings_no_internet_body))
            Row(Icons.Rounded.AccountBalance, stringResource(R.string.settings_bin_data), stringResource(R.string.settings_bin_data_body))
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.settings_section_danger)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_wipe), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.settings_wipe_body)) },
                leadingContent = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { dialog = "wipe" },
            )
        }
    }

    when (dialog) {
        "lock" -> ChoiceDialog(stringResource(R.string.settings_auto_lock), Settings.AUTO_LOCK_CHOICES, s.autoLockMs, { autoLockLabel(it) }, onDismiss = { dialog = null }) { v ->
            vm.update { it.copy(autoLockMs = v) }
        }
        "clip" -> ChoiceDialog(stringResource(R.string.settings_clipboard_clear), Settings.CLIPBOARD_CHOICES, s.clipboardClearSec, { clipboardLabel(it) }, onDismiss = { dialog = null }) { v ->
            vm.update { it.copy(clipboardClearSec = v) }
        }
        "wipe" -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wipe_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; scope.launch { vm.wipe() } }) {
                    Text(stringResource(R.string.settings_wipe), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun autoLockLabel(ms: Long) = stringResource(
    when (ms) { 0L -> R.string.settings_auto_lock_immediately; 30_000L -> R.string.settings_auto_lock_30s; 60_000L -> R.string.settings_auto_lock_1m; else -> R.string.settings_auto_lock_5m },
)

@Composable
private fun clipboardLabel(sec: Int) = stringResource(
    when (sec) { 0 -> R.string.settings_clipboard_never; 10 -> R.string.settings_clipboard_10s; 60 -> R.string.settings_clipboard_60s; else -> R.string.settings_clipboard_30s },
)

@Composable
private fun Row(icon: ImageVector, title: String, value: String? = null, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, body: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<T>, selected: T, label: @Composable (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { c ->
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(c == selected, role = Role.RadioButton) { onPick(c); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == selected, onClick = null)
                        Spacer(Modifier.padding(start = 12.dp))
                        Text(label(c), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun UpdatesGroup(enabled: Boolean, onEnabled: (Boolean) -> Unit, vm: com.aiyu.clavis.update.UpdateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Group(stringResource(R.string.settings_section_updates)) {
        ToggleRow(Icons.Rounded.Info, stringResource(R.string.settings_updates_auto), stringResource(R.string.settings_updates_auto_body), enabled, onChange = onEnabled)
        ListItem(
            headlineContent = { Text(com.aiyu.clavis.update.updateStatusText(state) ?: stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    com.aiyu.clavis.update.UpdateActions(state, vm)
                    TextButton(onClick = { scope.launch { vm.updater.check(manual = true) } }, enabled = state !is com.aiyu.clavis.update.UpdateState.Checking && state !is com.aiyu.clavis.update.UpdateState.Downloading) {
                        Text(stringResource(R.string.update_check))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}
