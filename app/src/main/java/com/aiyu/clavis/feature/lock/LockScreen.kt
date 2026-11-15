package com.aiyu.clavis.feature.lock

import androidx.compose.foundation.layout.Column
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
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedElementOrSelf
import com.aiyu.clavis.ui.message
import com.aiyu.clavis.ui.rememberPinVerifier
import kotlinx.coroutines.launch

/** Lock (screen 4): mark, dots, pad, biometric that prompts on entry. No card data, no hints. */
@Composable
fun LockScreen(session: SessionViewModel, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
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
        Spacer(Modifier.height(32.dp))
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
            header = { ClavisMark(Modifier.size(96.dp).sharedElementOrSelf(SharedKeys.MARK), icon = Icons.Rounded.Lock) },
            startKey = BiometricKey(
                enabled = session.biometricEnabled,
                autoPrompt = !verifier.lockedOut,
                cipher = session::biometricCipher,
                onAuthorized = { if (session.verifyBiometric(it)) onUnlocked() },
            ),
            footer = {
                Spacer(Modifier.height(16.dp))
                if (verifier.busy) LoadingIndicator(Modifier.size(48.dp)) else Spacer(Modifier.height(48.dp))
            },
        )
    }
}
