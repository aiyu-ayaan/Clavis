package com.aiyu.clavis.update

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.clavis.BuildConfig
import com.aiyu.clavis.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(val updater: AppUpdater) : ViewModel() {
    val state: StateFlow<UpdateState> = updater.state
}

/** Text for the action a state offers: shared by the dialog and the Settings row. */
@Composable
fun updateStatusText(s: UpdateState): String? = when (s) {
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
    is UpdateState.Available -> stringResource(R.string.update_available_body, s.release.name, BuildConfig.VERSION_NAME)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt())
    is UpdateState.Ready -> stringResource(R.string.update_available_body, s.release.name, BuildConfig.VERSION_NAME)
    is UpdateState.Failed -> s.message
    UpdateState.Idle -> null
}

/** The primary action for a state, or null. [permission] is shown first when installing is not yet allowed. */
@Composable
fun UpdateActions(s: UpdateState, vm: UpdateViewModel, onDone: () -> Unit = {}) {
    val u = vm.updater
    val context = LocalContext.current
    when (s) {
        is UpdateState.Available -> TextButton(onClick = { u.download(s.release, s.apk) }) { Text(stringResource(R.string.update_download)) }
        is UpdateState.Ready ->
            if (!u.canInstall()) TextButton(onClick = { context.startActivity(u.installPermissionIntent()) }) { Text(stringResource(R.string.update_allow_install)) }
            else TextButton(onClick = { u.install(s.file); onDone() }) { Text(stringResource(R.string.update_install)) }
        else -> Unit
    }
}

/** A quiet check at launch; asks only when something newer exists, and never while a card is on screen locked out. */
@Composable
fun UpdateHost(unlocked: Boolean, vm: UpdateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.updater.check() }
    if (!unlocked || state !is UpdateState.Available && state !is UpdateState.Downloading && state !is UpdateState.Ready) return
    AlertDialog(
        onDismissRequest = vm.updater::dismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(updateStatusText(state).orEmpty())
                (state as? UpdateState.Downloading)?.let { LinearProgressIndicator(progress = { it.progress }) }
            }
        },
        confirmButton = { UpdateActions(state, vm, onDone = vm.updater::dismiss) },
        dismissButton = { TextButton(onClick = vm.updater::dismiss) { Text(stringResource(R.string.update_later)) } },
    )
}
