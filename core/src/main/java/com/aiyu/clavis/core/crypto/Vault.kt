package com.aiyu.clavis.core.crypto

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher

enum class VaultState { NOT_SET_UP, LOCKED, UNLOCKED }

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class Wrong(val attemptsLeft: Int, val lockoutMs: Long) : UnlockResult
    data class LockedOut(val remainingMs: Long) : UnlockResult
}

/** Wrapped key material and attempt counters. No plaintext secret in here. */
@Serializable
private data class VaultFile(
    val salt: ByteArray,
    val iterations: Int,
    val pinBlob: ByteArray,          // K_device( AES-GCM(KEK_pin, DEK) )
    val bioBlob: ByteArray? = null,  // K_bio(DEK), iv || ct
    val recoveryBlob: ByteArray? = null, // K_recovery.public(DEK), opened only after the phone's screen lock
    val failures: Int = 0,
    val lockUntil: Long = 0,
    val lockDuration: Long = 0,
)

/**
 * Owns the DEK while unlocked (VaultSession in 03-ARCHITECTURE.md). Locked means no DEK in memory.
 * shortcut: counters live in the same file without a MAC; a rooted attacker can roll them back. Add an HMAC under K_device if that matters.
 */
class Vault(
    context: Context,
    private val keys: KeyManager = KeyManager(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "vault.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(if (read() != null) VaultState.LOCKED else VaultState.NOT_SET_UP)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    @Volatile private var dek: ByteArray? = null
    @Volatile private var authAt = 0L
    @Volatile var cipher: VaultCipher? = null
        private set

    val biometricEnabled: Boolean get() = read()?.bioBlob != null

    /** Within [maxAgeMs] of the last PIN or biometric success (reveal, copy CVV, share). */
    fun isFresh(maxAgeMs: Long = FRESH_MS): Boolean = cipher != null && clock() - authAt <= maxAgeMs

    suspend fun create(pin: String) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val salt = Gcm.randomBytes(16)
            val newDek = Gcm.randomBytes(32)
            write(VaultFile(salt, PinKdf.ITERATIONS, wrapForPin(pin, salt, PinKdf.ITERATIONS, newDek)))
            open(newDek)
        }
    }

    fun lockoutRemaining(): Long {
        val f = read() ?: return 0
        val until = Throttle.normalizedLockUntil(f.lockUntil, f.lockDuration, clock())
        if (until != f.lockUntil) write(f.copy(lockUntil = until))
        return (until - clock()).coerceAtLeast(0)
    }

    suspend fun unlockWithPin(pin: String): UnlockResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val f = read() ?: return@withContext UnlockResult.Wrong(0, 0)
            lockoutRemaining().takeIf { it > 0 }?.let { return@withContext UnlockResult.LockedOut(it) }
            val opened = tryPin(f, pin)
            if (opened != null) {
                write(f.copy(failures = 0, lockUntil = 0, lockDuration = 0))
                open(opened)
                UnlockResult.Success
            } else {
                val n = f.failures + 1
                val lock = Throttle.lockoutAfter(n)
                write(f.copy(failures = n, lockUntil = clock() + lock, lockDuration = lock))
                UnlockResult.Wrong(Throttle.attemptsLeft(n), lock)
            }
        }
    }

    /** Cipher for BiometricPrompt, or null when biometric is off or was invalidated (then it is switched off). */
    fun biometricUnlockCipher(): Cipher? {
        val blob = read()?.bioBlob ?: return null
        return try {
            keys.bioDecryptCipher(blob.copyOfRange(0, 12))
        } catch (_: KeyPermanentlyInvalidatedException) {
            disableBiometric(); null
        }
    }

    /** [authorized] came back from BiometricPrompt's CryptoObject. */
    fun unlockWithBiometric(authorized: Cipher): Boolean {
        val blob = read()?.bioBlob ?: return false
        return try {
            open(authorized.doFinal(blob, 12, blob.size - 12)); true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /** "Forgot PIN" with the phone's screen lock is possible: a wrap exists and the key was not invalidated. */
    val recoveryAvailable: Boolean get() = read()?.recoveryBlob != null

    /** API 30+: cipher for a device-credential BiometricPrompt. Null (and the wrap dropped) after the screen lock was removed. */
    fun recoveryCipher(): Cipher? = try {
        keys.recoveryCipher()
    } catch (_: KeyPermanentlyInvalidatedException) {
        dropRecovery(); null
    } catch (_: android.security.keystore.UserNotAuthenticatedException) {
        null // below API 30: ask for the screen lock first, then call again
    }

    /** [authorized] came back from the screen-lock prompt. Opens the vault; the caller must then set a new PIN. */
    fun unlockWithRecovery(authorized: Cipher): Boolean {
        val f = read() ?: return false
        val blob = f.recoveryBlob ?: return false
        return try {
            val d = authorized.doFinal(blob)
            write(f.copy(failures = 0, lockUntil = 0, lockDuration = 0))
            open(d); true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /** New PIN without the old one: only while unlocked (after recovery). Re-wraps the same DEK. */
    suspend fun resetPin(new: String): Boolean = mutex.withLock {
        withContext(Dispatchers.Default) {
            val d = dek ?: return@withContext false
            val salt = Gcm.randomBytes(16)
            write(read()!!.copy(salt = salt, iterations = PinKdf.ITERATIONS, pinBlob = wrapForPin(new, salt, PinKdf.ITERATIONS, d)))
            true
        }
    }

    private fun dropRecovery() {
        read()?.let { write(it.copy(recoveryBlob = null)) }
        keys.deleteRecoveryKey()
    }

    fun biometricEnrollCipher(): Cipher = keys.newBioEncryptCipher()

    fun enableBiometric(authorized: Cipher): Boolean {
        val d = dek ?: return false
        val f = read() ?: return false
        return try {
            write(f.copy(bioBlob = authorized.iv + authorized.doFinal(d))); true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    fun disableBiometric() {
        read()?.let { write(it.copy(bioBlob = null)) }
        keys.deleteBioKey()
    }

    /** Re-wraps the same DEK under the new PIN; card data is not re-encrypted. */
    suspend fun changePin(old: String, new: String): UnlockResult {
        val result = unlockWithPin(old)
        if (result != UnlockResult.Success) return result
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val d = dek ?: return@withContext
                val salt = Gcm.randomBytes(16)
                write(read()!!.copy(salt = salt, iterations = PinKdf.ITERATIONS, pinBlob = wrapForPin(new, salt, PinKdf.ITERATIONS, d)))
            }
        }
        return UnlockResult.Success
    }

    fun lock() {
        dek?.fill(0)
        dek = null
        cipher = null
        authAt = 0
        if (_state.value == VaultState.UNLOCKED) _state.value = VaultState.LOCKED
    }

    /** Deletes wrapped keys and Keystore entries. Card rows are cleared by the caller. */
    fun wipe() {
        lock()
        file.delete()
        keys.deleteAll()
        _state.value = VaultState.NOT_SET_UP
    }

    private fun wrapForPin(pin: String, salt: ByteArray, iterations: Int, d: ByteArray): ByteArray {
        val kek = PinKdf.derive(pin.toCharArray(), salt, iterations)
        try {
            return Gcm.seal(keys.deviceKey(), Gcm.seal(Gcm.key(kek), d))
        } finally {
            kek.fill(0)
        }
    }

    private fun tryPin(f: VaultFile, pin: String): ByteArray? {
        val kek = PinKdf.derive(pin.toCharArray(), f.salt, f.iterations)
        return try {
            Gcm.open(Gcm.key(kek), Gcm.open(keys.deviceKey(), f.pinBlob))
        } catch (_: GeneralSecurityException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    private fun open(d: ByteArray) {
        if (dek !== d) dek?.fill(0)
        dek = d
        cipher = VaultCipher(d)
        authAt = clock()
        refreshRecovery(d)
        _state.value = VaultState.UNLOCKED
    }

    /** Keeps the screen-lock wrap present and valid; silently skipped when the phone has no secure lock screen. */
    private fun refreshRecovery(d: ByteArray) {
        val f = read() ?: return
        val valid = try {
            keys.recoveryCipher() != null
        } catch (_: KeyPermanentlyInvalidatedException) {
            keys.deleteRecoveryKey(); false
        } catch (_: android.security.keystore.UserNotAuthenticatedException) {
            true
        } catch (_: Exception) {
            false
        }
        if (valid && f.recoveryBlob != null) return
        val blob = runCatching { keys.recoveryWrap(d) }.getOrNull()
        if (blob != null || f.recoveryBlob != null) write(f.copy(recoveryBlob = blob))
    }

    private fun read(): VaultFile? = try {
        if (file.baseFile.exists()) json.decodeFromString<VaultFile>(file.readFully().decodeToString()) else null
    } catch (_: Exception) {
        null
    }

    private fun write(f: VaultFile) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(f).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out); throw e
        }
    }

    companion object {
        const val FRESH_MS = 60_000L
    }
}
