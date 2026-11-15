package com.aiyu.clavis.ui

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.fragment.app.FragmentActivity
import com.aiyu.clavis.core.crypto.PinPolicy
import com.aiyu.clavis.core.crypto.UnlockResult
import com.aiyu.clavis.security.authenticate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "1 min 05 s" style countdown text for lockouts. */
@Composable
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val m = total / 60
    val s = total % 60
    return if (m > 0) stringResource(R.string.duration_min_sec, m, s) else stringResource(R.string.duration_sec, s)
}

/**
 * PIN verification UI state shared by the lock screen and the fresh-auth sheet: digits, wrong-PIN shake,
 * lockout countdown and an optional biometric shortcut.
 */
class PinVerifier(
    private val verify: suspend (String) -> UnlockResult,
    private val onSuccess: () -> Unit,
) {
    var digits by mutableStateOf("")
    var errorTick by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var attemptsLeft by mutableIntStateOf(-1)
    var lockoutUntil by mutableLongStateOf(0L)
    val lockedOut: Boolean get() = lockoutUntil > SystemClock.elapsedRealtime()

    suspend fun type(c: Char) {
        if (busy || lockedOut || digits.length >= PinPolicy.LENGTH) return
        digits += c
        if (digits.length < PinPolicy.LENGTH) return
        busy = true
        val result = verify(digits)
        busy = false
        when (result) {
            UnlockResult.Success -> { attemptsLeft = -1; onSuccess() }
            is UnlockResult.Wrong -> {
                errorTick++
                attemptsLeft = result.attemptsLeft
                if (result.lockoutMs > 0) lockoutUntil = SystemClock.elapsedRealtime() + result.lockoutMs
            }
            is UnlockResult.LockedOut -> lockoutUntil = SystemClock.elapsedRealtime() + result.remainingMs
        }
        digits = ""
    }

    fun delete() {
        if (!busy) digits = digits.dropLast(1)
    }
}

/** Status line for [PinVerifier]: lockout countdown, wrong PIN with attempts left, or [idle]. Ticks each second. */
@Composable
fun PinVerifier.message(idle: String): Pair<String, Boolean> {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > SystemClock.elapsedRealtime()) {
            now = SystemClock.elapsedRealtime(); delay(250)
        }
        now = SystemClock.elapsedRealtime()
    }
    val remaining = lockoutUntil - now
    return when {
        remaining > 0 -> stringResource(R.string.lock_locked_out, formatDuration(remaining)) to true
        attemptsLeft in 1 until 5 -> "${stringResource(R.string.lock_wrong_pin)}. ${stringResource(R.string.lock_attempts_left, attemptsLeft)}" to true
        attemptsLeft >= 0 -> stringResource(R.string.lock_wrong_pin) to true
        else -> idle to false
    }
}

/** Fingerprint key for the pad; shown only when biometric unlock is on. Auto-prompts once when [autoPrompt]. */
@Composable
fun BiometricKey(
    enabled: Boolean,
    autoPrompt: Boolean,
    cipher: () -> javax.crypto.Cipher?,
    onAuthorized: (javax.crypto.Cipher) -> Unit,
): (@Composable () -> Unit)? {
    if (!enabled) return null
    val activity = LocalActivity.current as? FragmentActivity ?: return null
    val scope = rememberCoroutineScope()
    val prompt: () -> Unit = {
        scope.launch { cipher()?.let { c -> activity.authenticate(c)?.let(onAuthorized) } }
    }
    LaunchedEffect(Unit) { if (autoPrompt) prompt() }
    return {
        FilledTonalIconButton(onClick = prompt, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = stringResource(R.string.lock_use_biometric))
        }
    }
}

@Composable
fun rememberPinVerifier(verify: suspend (String) -> UnlockResult, onSuccess: () -> Unit, initialLockoutMs: Long = 0): PinVerifier {
    return remember {
        PinVerifier(verify, onSuccess).apply {
            if (initialLockoutMs > 0) lockoutUntil = SystemClock.elapsedRealtime() + initialLockoutMs
        }
    }
}
