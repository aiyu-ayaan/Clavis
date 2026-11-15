package com.aiyu.clavis

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.aiyu.clavis.core.crypto.Vault
import com.aiyu.clavis.core.crypto.VaultState
import com.aiyu.clavis.di.AppScope
import com.aiyu.clavis.feature.share.ShareCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClavisApp : Application() {
    @Inject lateinit var vault: Vault
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        ShareCache.clear(this) // leftovers from a killed process
        // Screen off locks at once, whatever the auto-lock delay (02-SECURITY.md, T1).
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = vault.lock()
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
        scope.launch {
            vault.state.filter { it != VaultState.UNLOCKED }.collect { ShareCache.clear(this@ClavisApp) }
        }
    }
}
