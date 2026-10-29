package com.vwillcox.mytv.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.vwillcox.mytv.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Installs APKs sent from the phone page or fetched from an address. Android asks on screen before installing
 * anything new; updates to apps this one installed go through without asking.
 */
class Installer(private val context: Context, private val http: OkHttpClient) {
    /** The latest install's progress, for the screen: null when there is none. */
    val status = MutableStateFlow<String?>(null)

    suspend fun install(file: File, name: String) = withContext(Dispatchers.IO) { file.inputStream().use { install(it, file.length(), name) } }

    suspend fun install(input: InputStream, size: Long, name: String) = withContext(Dispatchers.IO) {
        status.value = "Installing $name…"
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (size > 0) setSize(size)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, size.takeIf { it > 0 } ?: -1).use { out ->
                input.copyTo(out, 1 shl 16)
                session.fsync(out)
            }
            val done = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallReceiver::class.java).putExtra("name", name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(done.intentSender)
        }
    }

    /** Downloads an APK from [url] and installs it. */
    suspend fun installFrom(url: String) = withContext(Dispatchers.IO) {
        status.value = "Downloading…"
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "app.apk" }
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("The download failed (${res.code})")
            install(res.body.byteStream(), res.body.contentLength(), name)
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "The app"
        val status = context.app.installer.status
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                status.value = "Confirm on the TV to install $name"
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> status.value = "$name is installed"
            else -> status.value = "$name didn't install: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason")
        }
    }
}
