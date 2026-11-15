package com.aiyu.clavis.feature.scan

import androidx.compose.runtime.Composable

/** Card numbers read by the camera, handed to the add form in memory only (never through nav args or saved state). */
object ScanHandoff {
    data class Result(val pan: String, val expiry: String?, val holder: String?)
    @Volatile var result: Result? = null
    fun take(): Result? = result.also { result = null }
}

@Composable
fun ScanScreen(onBack: () -> Unit, onResult: () -> Unit, onManual: () -> Unit) {
    onManual()
}
