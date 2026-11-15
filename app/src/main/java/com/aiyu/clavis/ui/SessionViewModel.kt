package com.aiyu.clavis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.clavis.core.crypto.UnlockResult
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.data.CardRepository
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
    private val settingsStore: SettingsStore,
    private val repo: CardRepository,
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

    val recoveryAvailable: Boolean get() = vault.recoveryAvailable
    fun recoveryCipher(): Cipher? = vault.recoveryCipher()
    fun unlockWithRecovery(cipher: Cipher) = vault.unlockWithRecovery(cipher)
    suspend fun resetPin(pin: String) = vault.resetPin(pin)

    /** "Forgot PIN" without the phone lock: everything goes, back to the landing. */
    suspend fun eraseAll() {
        repo.deleteAll()
        settingsStore.clear()
        vault.wipe()
    }
}
