package com.aiyu.clavis.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.aiyu.clavis.BuildConfig
import com.aiyu.clavis.core.settings.SettingsStore
import com.aiyu.clavis.core.update.Release
import com.aiyu.clavis.core.update.ReleaseAsset
import com.aiyu.clavis.core.update.Releases
import com.aiyu.clavis.core.update.Version
import com.aiyu.clavis.di.AppScope
import com.aiyu.clavis.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app current from the GitHub releases it was built by (07-DECISIONS.md D24). Nothing is sent but one
 * unauthenticated GET of the public release list; no card data, no identifiers. The last screen is always Android's
 * installer, and a network security config trusts only GitHub hosts, so nothing else can be reached.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val downloads = File(context.cacheDir, "updates")
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    val installed: Version? by lazy { Version.parse(BuildConfig.VERSION_NAME.removeSuffix("-dev")) }

    /** [manual] is the Check button: it reports "up to date" and ignores the setting and the hourly gap. */
    suspend fun check(manual: Boolean = false): UpdateState = withContext(Dispatchers.IO) {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Ready) return@withContext busy
        val s = store.settings.first()
        if (!manual && (!s.updatesEnabled || busy is UpdateState.Available || System.currentTimeMillis() - s.updateLastChecked < MIN_GAP)) return@withContext busy
        _state.value = UpdateState.Checking
        val next = runCatching {
            val c = open(Releases.API)
            try {
                if (c.responseCode !in 200..299) error(context.getString(R.string.update_err_github, c.responseCode))
                val pick = Releases.pick(Releases.parse(c.inputStream.bufferedReader().use { it.readText() }), installed, s.updateChannel.takeIf { it >= 0 })
                val apk = pick?.let(Releases::apkFor)
                if (pick == null || apk == null) UpdateState.UpToDate else UpdateState.Available(pick, apk)
            } finally { c.disconnect() }
        }.getOrElse { UpdateState.Failed(it.message ?: context.getString(R.string.update_err_check)) }
        store.update { it.copy(updateLastChecked = System.currentTimeMillis()) }
        _state.value = next
        next
    }

    fun download(release: Release, asset: ReleaseAsset) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch(Dispatchers.IO) {
            runCatching { fetch(release, asset) }
                .onFailure { _state.value = UpdateState.Failed(it.message ?: context.getString(R.string.update_err_download)) }
        }
    }

    private fun fetch(release: Release, asset: ReleaseAsset) {
        downloads.mkdirs()
        downloads.listFiles()?.forEach { if (it.name != asset.name) it.delete() }
        val target = File(downloads, asset.name)
        if (asset.size > 0 && target.length() == asset.size && verified(target, asset)) { _state.value = UpdateState.Ready(release, target); return }
        _state.value = UpdateState.Downloading(release, 0f)
        val c = open(asset.url)
        try {
            if (c.responseCode !in 200..299) error(context.getString(R.string.update_err_download_code, c.responseCode))
            val total = if (asset.size > 0) asset.size else c.contentLengthLong
            var written = 0L
            var last = 0f
            c.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        written += n
                        val f = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (f - last >= 0.01f) { last = f; _state.value = UpdateState.Downloading(release, f) }
                    }
                }
            }
        } finally { c.disconnect() }
        if (!verified(target, asset)) { target.delete(); error(context.getString(R.string.update_err_checksum)) }
        _state.value = UpdateState.Ready(release, target)
    }

    /** SHA-256 when GitHub records one; Android's signature check is the backstop either way. */
    private fun verified(file: File, asset: ReleaseAsset): Boolean {
        val expected = asset.sha256 ?: return true
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n == -1) break; d.update(b, 0, n) } }
        return d.digest().joinToString("") { "%02x".format(it) } == expected
    }

    /** "Install unknown apps" is a per-app setting, opened in settings; it cannot be asked for with a dialog. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent() =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A session, so a refusal (usually a different signing key) comes back to [UpdateInstallReceiver] with a reason. */
    fun install(apk: File) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("clavis.apk", 0, apk.length()).use { sink ->
                        apk.inputStream().use { it.copyTo(sink) }
                        session.fsync(sink)
                    }
                    val status = PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_STATUS).setPackage(context.packageName),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    session.commit(status.intentSender)
                }
            }.onFailure { fail(it.message ?: context.getString(R.string.update_err_start)) }
        }
    }

    fun dismiss() { _state.value = UpdateState.Idle }
    fun fail(message: String) { _state.value = UpdateState.Failed(message) }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Clavis/${BuildConfig.VERSION_NAME}") // GitHub rejects requests without one
    }

    private companion object { const val MIN_GAP = 60L * 60 * 1000 }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val apk: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
