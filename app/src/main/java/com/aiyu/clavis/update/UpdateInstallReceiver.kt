package com.aiyu.clavis.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.aiyu.clavis.ui.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Answers an install session: hands back the system's confirm dialog, or says why it failed. Not exported. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var updater: AppUpdater

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { updater.fail(it.message ?: context.getString(R.string.update_err_screen)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.dismiss()
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updater.fail(context.getString(R.string.update_err_signature))
            else -> updater.fail(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: context.getString(R.string.update_err_install))
        }
    }

    companion object { const val ACTION_STATUS = "com.aiyu.clavis.UPDATE_INSTALL_STATUS" }
}
