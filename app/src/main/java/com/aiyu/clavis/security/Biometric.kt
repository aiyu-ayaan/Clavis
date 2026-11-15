package com.aiyu.clavis.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aiyu.clavis.ui.R
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** Class 3 biometric enrolled and usable. Device credential is never accepted (D4). */
fun Context.canUseBiometric(): Boolean =
    BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** Hardware present (maybe nothing enrolled yet): show the toggle, explain if it cannot be enabled. */
fun Context.hasBiometricHardware(): Boolean =
    BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

/**
 * Shows BiometricPrompt bound to [cipher] (CryptoObject). Returns the authorized cipher, or null when the user
 * cancelled, chose PIN, or the sensor locked out. A UI bypass yields no usable cipher.
 */
suspend fun FragmentActivity.authenticate(cipher: Cipher, subtitle: String = getString(R.string.biometric_prompt_subtitle)): Cipher? =
    suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(result.cryptoObject?.cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resume(null)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(subtitle)
            .setNegativeButtonText(getString(R.string.biometric_prompt_use_pin))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
