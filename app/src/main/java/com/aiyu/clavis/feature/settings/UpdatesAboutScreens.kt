package com.aiyu.clavis.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.clavis.BuildConfig
import com.aiyu.clavis.core.update.Releases
import com.aiyu.clavis.core.update.Version
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.InnerScreen
import com.aiyu.clavis.update.UpdateActions
import com.aiyu.clavis.update.UpdateState
import com.aiyu.clavis.update.UpdateViewModel
import com.aiyu.clavis.update.updateStatusText
import com.aiyu.clavis.feature.detail.Group
import kotlinx.coroutines.launch

private const val DEVELOPER = "aiyu-ayaan"
private const val GITHUB_URL = "https://github.com/aiyu-ayaan"
private const val PORTFOLIO_URL = "https://aiyu.co.in"

/** Settings > Updates: automatic check, release channel (alpha / beta / stable), and a manual check. */
@Composable
fun UpdatesScreen(onBack: () -> Unit, settingsVm: SettingsViewModel = hiltViewModel(), vm: UpdateViewModel = hiltViewModel()) {
    val s by settingsVm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val channel = s.updateChannel.takeIf { it >= 0 } ?: vm.updater.installed?.stage ?: Version.STABLE
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    InnerScreen(title = stringResource(R.string.settings_section_updates), onBack = onBack) {
        Group(stringResource(R.string.settings_section_updates)) {
            ToggleRow(Icons.Rounded.SystemUpdate, stringResource(R.string.settings_updates_auto), stringResource(R.string.settings_updates_auto_body), s.updatesEnabled) { on ->
                settingsVm.update { it.copy(updatesEnabled = on) }
            }
            ListItem(
                headlineContent = { Text(updateStatusText(state) ?: stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        UpdateActions(state, vm)
                        TextButton(onClick = { scope.launch { vm.updater.check(manual = true) } }, enabled = !busy) { Text(stringResource(R.string.update_check)) }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.update_channel)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.update_channel_hint)) },
                supportingContent = {
                    val items = listOf(Version.STABLE to R.string.update_channel_stable, Version.BETA to R.string.update_channel_beta, Version.ALPHA to R.string.update_channel_alpha)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        items.forEachIndexed { i, (stage, label) ->
                            SegmentedButton(
                                selected = channel == stage,
                                onClick = { scope.launch { settingsVm.update { it.copy(updateChannel = stage) }.join(); vm.updater.check(manual = true) } },
                                shape = SegmentedButtonDefaults.itemShape(i, items.size),
                                enabled = state !is UpdateState.Downloading,
                            ) { Text(stringResource(label)) }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
        val release = (state as? UpdateState.Available)?.release ?: (state as? UpdateState.Ready)?.release
        if (release != null && release.notes.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Group(stringResource(R.string.update_whats_new, release.name)) {
                Text(release.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/** Settings > About: app, privacy facts, developer, source. */
@Composable
fun AboutScreen(onBack: () -> Unit, onAcknowledgements: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val external = @Composable { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

    InnerScreen(title = stringResource(R.string.settings_section_about), onBack = onBack) {
        Group(stringResource(R.string.about_section_app)) {
            Row(Icons.Rounded.Info, stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            Row(Icons.Rounded.CloudOff, stringResource(R.string.settings_no_internet), stringResource(R.string.settings_no_internet_body))
            Row(Icons.Rounded.AccountBalance, stringResource(R.string.settings_bin_data), stringResource(R.string.settings_bin_data_body))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_source_code)) },
                supportingContent = { Text("github.com/${Releases.REPOSITORY}") },
                leadingContent = { Icon(Icons.Rounded.Code, null) },
                trailingContent = external,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { open("https://github.com/${Releases.REPOSITORY}") },
            )
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.about_section_open_source)) {
            Row(Icons.Rounded.Description, stringResource(R.string.about_acknowledgements), stringResource(R.string.about_acknowledgements_body), onClick = onAcknowledgements)
        }
        Spacer(Modifier.height(16.dp))
        Group(stringResource(R.string.about_section_developer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(DEVELOPER.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text(DEVELOPER, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.about_developer_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_github)) },
                supportingContent = { Text("github.com/$DEVELOPER") },
                leadingContent = { Icon(Icons.Rounded.Code, null) },
                trailingContent = external,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { open(GITHUB_URL) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_portfolio)) },
                supportingContent = { Text("aiyu.co.in") },
                leadingContent = { Icon(Icons.Rounded.Language, null) },
                trailingContent = external,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { open(PORTFOLIO_URL) },
            )
        }
    }
}

private data class Library(val name: String, val url: String, val license: String)

/** Keep in sync with gradle/libs.versions.toml and the bundled BIN data. Proper names, so not in strings.xml. */
private val libraries = listOf(
    Library("Kotlin", "https://github.com/JetBrains/kotlin", "Apache-2.0"),
    Library("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines", "Apache-2.0"),
    Library("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization", "Apache-2.0"),
    Library("Jetpack Compose", "https://developer.android.com/jetpack/compose", "Apache-2.0"),
    Library("Material 3 for Compose", "https://developer.android.com/jetpack/androidx/releases/compose-material3", "Apache-2.0"),
    Library("AndroidX (Core, Lifecycle, Activity, Fragment, Navigation, DataStore, Splashscreen)", "https://developer.android.com/jetpack/androidx", "Apache-2.0"),
    Library("AndroidX Room", "https://developer.android.com/jetpack/androidx/releases/room", "Apache-2.0"),
    Library("AndroidX Biometric", "https://developer.android.com/jetpack/androidx/releases/biometric", "Apache-2.0"),
    Library("AndroidX Graphics Shapes", "https://developer.android.com/jetpack/androidx/releases/graphics", "Apache-2.0"),
    Library("CameraX", "https://developer.android.com/jetpack/androidx/releases/camera", "Apache-2.0"),
    Library("ML Kit Text Recognition", "https://developers.google.com/ml-kit/vision/text-recognition/v2", "ML Kit Terms"),
    Library("Tink", "https://github.com/tink-crypto/tink-java", "Apache-2.0"),
    Library("Dagger Hilt", "https://github.com/google/dagger", "Apache-2.0"),
    Library("BIN List Data (Venelin Kochev)", "https://github.com/venelinkochev/bin-list-data", "CC BY 4.0"),
)

/** Settings > About > Acknowledgements: open-source libraries and data this app uses. */
@Composable
fun AcknowledgementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InnerScreen(title = stringResource(R.string.about_acknowledgements), onBack = onBack) {
        Group(stringResource(R.string.about_section_open_source)) {
            libraries.forEach { lib ->
                ListItem(
                    headlineContent = { Text(lib.name) },
                    supportingContent = { Text("${lib.license} · ${lib.url.removePrefix("https://")}") },
                    leadingContent = { Icon(Icons.Rounded.Description, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                )
            }
        }
    }
}
