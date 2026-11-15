package com.aiyu.clavis.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Non-sensitive preferences only (02-SECURITY.md). */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoLockMs: Long = 30_000,
    val clipboardClearSec: Int = 30,
    val neverShare: Boolean = false,
) {
    companion object {
        val AUTO_LOCK_CHOICES = listOf(0L, 30_000L, 60_000L, 300_000L)
        val CLIPBOARD_CHOICES = listOf(10, 30, 60, 0) // 0 = never
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val autoLock = longPreferencesKey("auto_lock_ms")
        val clipboard = intPreferencesKey("clipboard_clear_sec")
        val neverShare = booleanPreferencesKey("never_share")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.theme] = s.theme.name
            prefs[K.dynamic] = s.dynamicColor
            prefs[K.autoLock] = s.autoLockMs
            prefs[K.clipboard] = s.clipboardClearSec
            prefs[K.neverShare] = s.neverShare
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[K.dynamic] ?: true,
        autoLockMs = this[K.autoLock] ?: 30_000,
        clipboardClearSec = this[K.clipboard] ?: 30,
        neverShare = this[K.neverShare] ?: false,
    )
}
