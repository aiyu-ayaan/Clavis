package com.aiyu.clavis.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Shared motion tokens. Component springs come from MaterialTheme.motionScheme (expressive). */
object ClavisMotion {
    const val MORPH_MILLIS = 2400
    const val ORBIT_MILLIS = 18_000
    const val STAGGER_MILLIS = 45
}

/** True when the user turned animations off (Accessibility "Remove animations" / developer scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
