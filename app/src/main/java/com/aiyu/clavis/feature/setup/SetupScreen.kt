package com.aiyu.clavis.feature.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aiyu.clavis.core.crypto.PinPolicy
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.security.authenticate
import com.aiyu.clavis.security.canUseBiometric
import com.aiyu.clavis.security.hasSecureLockScreen
import com.aiyu.clavis.ui.R
import com.aiyu.clavis.ui.components.ClavisMark
import com.aiyu.clavis.ui.components.PinEntry
import com.aiyu.clavis.ui.components.pinKeySize
import com.aiyu.clavis.ui.components.SharedKeys
import com.aiyu.clavis.ui.components.sharedElementOrSelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(private val vault: Vault) : ViewModel() {
    suspend fun create(pin: String) = vault.create(pin)
    fun enrollCipher() = vault.biometricEnrollCipher()
    fun enable(cipher: javax.crypto.Cipher) = vault.enableBiometric(cipher)
}

private enum class Step { CREATE, CONFIRM, CREATING, BIOMETRIC }

/** Mandatory security setup (screen 3): create PIN, confirm, optional biometric. Cannot be skipped. */
@Composable
fun SetupScreen(onBack: () -> Unit, onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.CREATE) }
    var first by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var errorTick by remember { mutableIntStateOf(0) }
    val hasBio = remember { context.canUseBiometric() }
    val totalSteps = if (hasBio) 3 else 2

    BackHandler(step == Step.CONFIRM) { step = Step.CREATE; digits = ""; first = "" }
    BackHandler(step == Step.CREATE) { onBack() }
    BackHandler(step == Step.CREATING || step == Step.BIOMETRIC) { /* vault exists now; finish forward only */ }

    fun fail(msg: Int) { error = msg; errorTick++; digits = "" }

    fun onDigit(c: Char) {
        if (digits.length >= PinPolicy.LENGTH) return
        digits += c
        if (digits.length < PinPolicy.LENGTH) return
        when (step) {
            Step.CREATE -> if (PinPolicy.isTrivial(digits)) fail(R.string.setup_pin_too_simple) else {
                first = digits; digits = ""; error = null; step = Step.CONFIRM
            }
            Step.CONFIRM -> if (digits != first) {
                fail(R.string.setup_pin_mismatch); first = ""; step = Step.CREATE
            } else {
                step = Step.CREATING
                scope.launch {
                    vm.create(first)
                    first = ""; digits = ""
                    if (hasBio) step = Step.BIOMETRIC else onDone()
                }
            }
            else -> Unit
        }
    }

    val index = when (step) { Step.CREATE -> 1; Step.CONFIRM, Step.CREATING -> 2; Step.BIOMETRIC -> 3 }
    val progress by animateFloatAsState(index / totalSteps.toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "progress")

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_step_of, index, totalSteps), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp))
        Spacer(Modifier.height(if (pinKeySize() < 72.dp) 12.dp else 28.dp))
        val mark: @Composable () -> Unit = {
            ClavisMark(Modifier.size(if (pinKeySize() < 72.dp) 64.dp else 88.dp).sharedElementOrSelf(SharedKeys.MARK), icon = if (step == Step.BIOMETRIC) Icons.Rounded.Fingerprint else Icons.Rounded.Key)
        }
        AnimatedContent(
            step == Step.BIOMETRIC,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "step",
        ) { bio ->
            if (!bio) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinEntry(
                    title = stringResource(if (step == Step.CREATE) R.string.setup_create_pin_title else R.string.setup_confirm_pin_title),
                    message = error?.let { stringResource(it) } ?: stringResource(R.string.setup_create_pin_body, PinPolicy.LENGTH),
                    isError = error != null,
                    errorTick = errorTick,
                    entered = digits.length,
                    length = PinPolicy.LENGTH,
                    onDigit = ::onDigit,
                    onDelete = { digits = digits.dropLast(1) },
                    enabled = step != Step.CREATING,
                    header = mark,
                )
                Spacer(Modifier.height(20.dp))
                Box(Modifier.heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                    if (step == Step.CREATING) LoadingIndicator() else NoRecoveryNote()
                }
            } else BiometricStep(
                mark = mark,
                onEnable = {
                    scope.launch {
                        val cipher = vm.enrollCipher()
                        activity.authenticate(cipher)?.let { vm.enable(it) }
                        onDone()
                    }
                },
                onSkip = onDone,
            )
        }
    }
}

@Composable
private fun NoRecoveryNote() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(stringResource(if (LocalContext.current.hasSecureLockScreen()) R.string.setup_no_recovery_v2 else R.string.setup_no_recovery), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun BiometricStep(mark: @Composable () -> Unit, onEnable: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        mark()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_biometric_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_biometric_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.setup_biometric_enable), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.setup_biometric_not_now)) }
    }
}
