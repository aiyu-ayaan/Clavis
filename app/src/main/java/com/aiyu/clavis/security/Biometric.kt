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

/** The phone has a PIN, pattern or password lock (needed for "Forgot PIN"). */
fun Context.hasSecureLockScreen(): Boolean = getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure

/**
 * Asks for the phone's own screen lock. API 30+: bound to [cipher] as a CryptoObject, which it authorizes.
 * API 28–29: plain prompt; the caller builds the cipher afterwards (the key allows 30 s after auth). Null on cancel.
 */
suspend fun FragmentActivity.confirmScreenLock(cipher: Cipher?): Boolean? =
    suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) cont.resume(null)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.recovery_prompt_title))
            .setSubtitle(getString(R.string.recovery_prompt_subtitle))
            .setAllowedAuthenticators(
                if (android.os.Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        if (cipher != null && android.os.Build.VERSION.SDK_INT >= 30) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        else prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
