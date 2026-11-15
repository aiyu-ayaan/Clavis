package com.aiyu.clavis.feature.lock

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.aiyu.clavis.security.confirmScreenLock
import com.aiyu.clavis.security.hasSecureLockScreen
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiyu.clavis.core.crypto.PinPolicy
import com.aiyu.clavis.ui.BiometricKey
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.SessionViewModel
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.PinEntry
import com.aiyu.clavis.ui.components.pinKeySize
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedElementOrSelf
import com.aiyu.clavis.ui.message
import com.aiyu.clavis.ui.rememberPinVerifier
import kotlinx.coroutines.launch

/** Lock (screen 4): mark, dots, pad, biometric that prompts on entry. No card data, no hints. */
@Composable
fun LockScreen(session: SessionViewModel, onUnlocked: () -> Unit, onRecovered: () -> Unit) {
    val scope = rememberCoroutineScope()
    var forgot by remember { mutableStateOf(false) }
    val verifier = rememberPinVerifier(session::verifyPin, onUnlocked, initialLockoutMs = session.lockoutRemaining())
    val (message, isError) = verifier.message(stringResource(R.string.lock_enter_pin))

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp).sharedElementOrSelf(SharedKeys.TITLE),
        )
        Spacer(Modifier.height(if (pinKeySize() < 72.dp) 12.dp else 32.dp))
        PinEntry(
            title = stringResource(R.string.lock_title),
            message = message,
            isError = isError,
            errorTick = verifier.errorTick,
            entered = verifier.digits.length,
            length = PinPolicy.LENGTH,
            onDigit = { scope.launch { verifier.type(it) } },
            onDelete = verifier::delete,
            enabled = !verifier.busy && !verifier.lockedOut,
            header = { ClavisMark(Modifier.size(if (pinKeySize() < 72.dp) 64.dp else 96.dp).sharedElementOrSelf(SharedKeys.MARK), icon = Icons.Rounded.Lock) },
            startKey = BiometricKey(
                enabled = session.biometricEnabled,
                autoPrompt = !verifier.lockedOut,
                cipher = session::biometricCipher,
                onAuthorized = { if (session.verifyBiometric(it)) onUnlocked() },
            ),
            footer = {
                Spacer(Modifier.height(16.dp))
                if (verifier.busy) LoadingIndicator(Modifier.size(48.dp)) else Spacer(Modifier.height(48.dp))
                TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.recovery_forgot)) }
            },
        )
    }
    if (forgot) ForgotPinSheet(session, onDismiss = { forgot = false }, onRecovered = onRecovered)
}

/** "Forgot PIN?": reset with the phone's screen lock (cards kept) or erase and start over (D13 revised). */
@Composable
private fun ForgotPinSheet(session: SessionViewModel, onDismiss: () -> Unit, onRecovered: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var confirmErase by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val canRecover = remember { session.recoveryAvailable && context.hasSecureLockScreen() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Text(stringResource(R.string.recovery_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.recovery_screen_lock)) },
                supportingContent = { Text(stringResource(if (canRecover) R.string.recovery_screen_lock_body else R.string.recovery_screen_lock_unavailable)) },
                leadingContent = { Icon(Icons.Rounded.PhonelinkLock, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(enabled = canRecover) {
                        scope.launch {
                            failed = !recoverWithScreenLock(activity, session)
                            if (!failed) { onDismiss(); onRecovered() }
                        }
                    }
                    .alpha(if (canRecover) 1f else 0.5f),
            )
            if (failed) Text(stringResource(R.string.recovery_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.recovery_erase), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.recovery_erase_body)) },
                leadingContent = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { confirmErase = true },
            )
        }
    }
    if (confirmErase) AlertDialog(
        onDismissRequest = { confirmErase = false },
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
        text = { Text(stringResource(R.string.settings_wipe_body)) },
        confirmButton = {
            TextButton(onClick = { confirmErase = false; scope.launch { session.eraseAll() } }) {
                Text(stringResource(R.string.settings_wipe), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** API 30+: the prompt authorizes the key itself. API 28–29: prompt first, then use the key within its 30 s window. */
private suspend fun recoverWithScreenLock(activity: FragmentActivity, session: SessionViewModel): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val cipher = session.recoveryCipher() ?: return false
        activity.confirmScreenLock(cipher) ?: return false
        return session.unlockWithRecovery(cipher)
    }
    activity.confirmScreenLock(null) ?: return false
    return session.recoveryCipher()?.let(session::unlockWithRecovery) == true
}
