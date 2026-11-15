package com.aiyu.clavis.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.clavis.core.crypto.PinPolicy
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.PinEntry
import kotlinx.coroutines.launch

/** Bottom sheet that answers [com.aiyu.clavis.security.AuthGate.requireFresh]: biometric first if on, PIN always. */
@Composable
fun FreshAuthHost(session: SessionViewModel) {
    val pending by session.gate.pending.collectAsStateWithLifecycle()
    if (pending == null) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val done: (Boolean) -> Unit = { ok -> scope.launch { sheet.hide(); session.gate.finish(ok) } }
    val verifier = rememberPinVerifier(session::verifyPin, onSuccess = { done(true) }, initialLockoutMs = session.lockoutRemaining())
    val (message, isError) = verifier.message(stringResource(R.string.auth_required_body))

    ModalBottomSheet(onDismissRequest = { session.gate.finish(false) }, sheetState = sheet) {
        PinEntry(
            title = stringResource(R.string.auth_required_title),
            message = message,
            isError = isError,
            errorTick = verifier.errorTick,
            entered = verifier.digits.length,
            length = PinPolicy.LENGTH,
            onDigit = { scope.launch { verifier.type(it) } },
            onDelete = verifier::delete,
            enabled = !verifier.busy && !verifier.lockedOut,
            header = { ClavisMark(Modifier.size(56.dp), icon = Icons.Rounded.Shield) },
            startKey = BiometricKey(
                enabled = session.biometricEnabled,
                autoPrompt = true,
                cipher = session::biometricCipher,
                onAuthorized = { if (session.verifyBiometric(it)) done(true) },
            ),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
        )
    }
}
