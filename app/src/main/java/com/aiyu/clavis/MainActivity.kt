package com.aiyu.clavis

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.clavis.core.settings.ThemeMode
import com.aiyu.clavis.feature.share.ShareCache
import com.aiyu.clavis.ui.ClavisNavHost
import com.aiyu.clavis.ui.SessionViewModel
import com.aiyu.clavis.ui.theme.ClavisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val session: SessionViewModel by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Always on: blocks screenshots, screen recording and the recents thumbnail (D12).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        setContent {
            val settings by session.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ClavisTheme(dark = dark, dynamicColor = settings.dynamicColor) {
                ClavisNavHost(session)
            }
        }
    }

    // Auto-lock: background longer than the chosen delay (0 = immediately) ends the session (02-SECURITY.md).
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        stoppedAt = SystemClock.elapsedRealtime()
        if (session.settings.value.autoLockMs == 0L) session.lock()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= session.settings.value.autoLockMs) session.lock()
        stoppedAt = 0
        // Back from the share sheet: the target has read the image by now (05-SHARE-AND-COPY.md).
        if (ShareCache.pending) { ShareCache.pending = false; ShareCache.clear(this) }
    }
}
