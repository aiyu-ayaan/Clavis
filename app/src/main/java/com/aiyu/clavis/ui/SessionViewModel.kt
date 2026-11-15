package com.aiyu.clavis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.core.crypto.UnlockResult
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.settings.Settings
import com.aiyu.clavis.core.settings.SettingsStore
import com.aiyu.clavis.security.AuthGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.crypto.Cipher

/** Vault state, settings and the fresh-auth prompt, shared by the nav host and the activity. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val vault: Vault,
    settingsStore: SettingsStore,
    val gate: AuthGate,
) : ViewModel() {
    val state = vault.state
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val biometricEnabled: Boolean get() = vault.biometricEnabled

    fun lock() = vault.lock()
    fun lockoutRemaining() = vault.lockoutRemaining()
    suspend fun verifyPin(pin: String): UnlockResult = vault.unlockWithPin(pin)
    fun biometricCipher(): Cipher? = vault.biometricUnlockCipher()
    fun verifyBiometric(cipher: Cipher) = vault.unlockWithBiometric(cipher)
}
