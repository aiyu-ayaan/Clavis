package com.aiyu.clavis.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.aiyu.clavis.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every copy is flagged sensitive (keyboards and the clipboard preview hide it) and cleared after the chosen delay.
 * shortcut: the clear timer lives in the process; if Android kills it first the clip stays (documented in 02-SECURITY.md).
 */
@Singleton
class SecureClipboard @Inject constructor(
    @ApplicationContext context: Context,
    @AppScope private val scope: CoroutineScope,
) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    fun copy(value: String, clearAfterSec: Int) {
        val clip = ClipData.newPlainText(LABEL, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        cm.setPrimaryClip(clip)
        clearJob?.cancel()
        if (clearAfterSec > 0) clearJob = scope.launch {
            delay(clearAfterSec * 1000L)
            clear()
        }
    }

    fun clear() {
        clearJob?.cancel()
        // Leave the user's own later copies alone when we can tell (label readable only while focused).
        val label = runCatching { cm.primaryClipDescription?.label }.getOrNull()
        if (label == null || label == LABEL) cm.clearPrimaryClip()
    }

    private companion object {
        const val LABEL = "Clavis"
    }
}
