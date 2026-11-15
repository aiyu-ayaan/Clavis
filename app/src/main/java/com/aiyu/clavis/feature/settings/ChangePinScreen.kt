package com.aiyu.clavis.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aiyu.clavis.core.crypto.PinPolicy
import com.aiyu.clavis.core.crypto.UnlockResult
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.InnerScreen
import com.aiyu.clavis.ui.components.PinEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangePinViewModel @Inject constructor(private val vault: Vault) : ViewModel() {
    suspend fun change(old: String, new: String) = vault.changePin(old, new)
}

private enum class PinStep { CURRENT, NEW, CONFIRM, SAVING }

/** Change PIN: current, new, confirm. Re-wraps the DEK only (02-SECURITY.md). */
@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    reset: (suspend (String) -> Boolean)? = null, // set after "Forgot PIN": no current PIN asked, no way back
    vm: ChangePinViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (reset != null) PinStep.NEW else PinStep.CURRENT) }
    var digits by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val wrong = stringResource(R.string.lock_wrong_pin)
    val simple = stringResource(R.string.setup_pin_too_simple)
    val mismatch = stringResource(R.string.setup_pin_mismatch)
    val lockedOut = stringResource(R.string.change_pin_locked_out)

    fun fail(msg: String) { error = msg; tick++; digits = "" }

    fun onDigit(c: Char) {
        if (digits.length >= PinPolicy.LENGTH) return
        digits += c
        if (digits.length < PinPolicy.LENGTH) return
        when (step) {
            PinStep.CURRENT -> { current = digits; digits = ""; error = null; step = PinStep.NEW }
            PinStep.NEW -> if (PinPolicy.isTrivial(digits)) fail(simple) else { fresh = digits; digits = ""; error = null; step = PinStep.CONFIRM }
            PinStep.CONFIRM -> if (digits != fresh) { fail(mismatch); step = PinStep.NEW } else {
                step = PinStep.SAVING
                scope.launch {
                    if (reset != null) {
                        if (reset(fresh)) onDone() else { step = PinStep.NEW; fail(wrong) }
                        return@launch
                    }
                    when (vm.change(current, fresh)) {
                        UnlockResult.Success -> onDone()
                        is UnlockResult.Wrong -> { step = PinStep.CURRENT; fail(wrong) }
                        is UnlockResult.LockedOut -> { step = PinStep.CURRENT; fail(lockedOut) }
                    }
                    current = ""; fresh = ""
                }
            }
            PinStep.SAVING -> Unit
        }
    }

    if (reset != null) BackHandler { /* a new PIN must be set before anything else */ }
    InnerScreen(title = stringResource(if (reset != null) R.string.reset_pin_title else R.string.settings_change_pin), onBack = if (reset != null) null else onBack) {
        PinEntry(
            title = stringResource(when (step) { PinStep.CURRENT -> R.string.change_pin_current; PinStep.NEW -> R.string.change_pin_new; else -> R.string.setup_confirm_pin_title }),
            message = error ?: if (step == PinStep.CURRENT) stringResource(R.string.lock_enter_pin) else stringResource(R.string.setup_create_pin_body, PinPolicy.LENGTH),
            isError = error != null,
            errorTick = tick,
            entered = digits.length,
            length = PinPolicy.LENGTH,
            onDigit = ::onDigit,
            onDelete = { digits = digits.dropLast(1) },
            enabled = step != PinStep.SAVING,
            header = { ClavisMark(Modifier.size(72.dp), icon = Icons.Rounded.Password) },
            footer = { Spacer(Modifier.height(16.dp)); if (step == PinStep.SAVING) LoadingIndicator() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
