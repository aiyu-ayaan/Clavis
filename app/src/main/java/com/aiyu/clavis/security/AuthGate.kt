package com.aiyu.clavis.security

import com.aiyu.clavis.core.crypto.Vault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthGate.requireFresh (03-ARCHITECTURE.md): reveal, copy-CVV, copy-number and share call it. Returns at once
 * when the last PIN/biometric success is under 60 s old, otherwise asks [FreshAuthHost] to prompt and suspends.
 */
@Singleton
class AuthGate @Inject constructor(private val vault: Vault) {
    private val _pending = MutableStateFlow<CompletableDeferred<Boolean>?>(null)
    val pending: StateFlow<CompletableDeferred<Boolean>?> = _pending.asStateFlow()

    suspend fun requireFresh(): Boolean {
        if (vault.isFresh()) return true
        val request = _pending.value ?: CompletableDeferred<Boolean>().also { _pending.value = it }
        return try {
            request.await()
        } finally {
            if (_pending.value === request) _pending.value = null
        }
    }

    fun finish(success: Boolean) {
        _pending.value?.complete(success)
        _pending.value = null
    }
}
