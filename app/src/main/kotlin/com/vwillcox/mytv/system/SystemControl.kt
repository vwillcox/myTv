package com.vwillcox.mytv.system

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.vwillcox.mytv.data.AppRepository
import com.vwillcox.mytv.data.ConfigStore
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.Open
import com.vwillcox.mytv.data.READ_TV_LISTINGS

data class DreamInfo(val component: ComponentName, val label: String)

/** One of the TV's own settings screens, opened from the settings panel. */
data class SystemPage(val label: String, val action: String)

val SystemPages = listOf(
    SystemPage("All Android settings", Settings.ACTION_SETTINGS),
    SystemPage("Network and internet", Settings.ACTION_WIFI_SETTINGS),
    SystemPage("Display and sound", "android.settings.DISPLAY_SETTINGS"),
    SystemPage("Remotes and accessories", Settings.ACTION_BLUETOOTH_SETTINGS),
    SystemPage("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    SystemPage("Screensaver (system)", Settings.ACTION_DREAM_SETTINGS),
    SystemPage("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SystemPage("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS),
    SystemPage("Date and time", Settings.ACTION_DATE_SETTINGS),
    SystemPage("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SystemPage("About", Settings.ACTION_DEVICE_INFO_SETTINGS),
)

/**
 * The parts of the TV this app changes beyond its own screen: the screensaver and its timing, the Home button
 * service, and opening titles in other apps. Most of it needs WRITE_SECURE_SETTINGS, which only adb can grant.
 */
class SystemControl(private val context: Context, private val apps: AppRepository, private val store: ConfigStore) {
    private val resolver get() = context.contentResolver

    val canWriteSecure get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    val canReadListings get() = context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED
    val canInstall get() = context.packageManager.canRequestPackageInstalls()

    // ---- Screensaver ------------------------------------------------------------------------

    fun dreams(): List<DreamInfo> = context.packageManager
        .queryIntentServices(Intent("android.service.dreams.DreamService"), PackageManager.GET_META_DATA)
        .map { DreamInfo(ComponentName(it.serviceInfo.packageName, it.serviceInfo.name), it.loadLabel(context.packageManager).toString()) }
        .sortedBy { it.label.lowercase() }

    val dream: ComponentName?
        get() = Settings.Secure.getString(resolver, "screensaver_components")?.split(',')?.firstOrNull()?.let(ComponentName::unflattenFromString)

    fun setDream(component: ComponentName): Boolean = putSecure("screensaver_components", component.flattenToString()) &&
        putSecure("screensaver_enabled", "1")

    var screensaverEnabled: Boolean
        get() = Settings.Secure.getInt(resolver, "screensaver_enabled", 1) == 1
        set(on) { putSecure("screensaver_enabled", if (on) "1" else "0") }

    /** How long the TV waits, idle, before the screensaver starts. Android keeps this as the screen-off timeout. */
    var screensaverAfterMs: Long
        get() = Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000L)
        set(ms) { runCatching { Settings.System.putLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, ms) }.onFailure { warn("screen_off_timeout", it) } }

    /** How long after that the TV goes to sleep; 0 or less is never. */
    var sleepAfterMs: Long
        get() = Settings.Secure.getLong(resolver, "sleep_timeout", -1L)
        set(ms) { putSecure("sleep_timeout", "$ms") }

    // ---- Home button ------------------------------------------------------------------------

    private val homeService get() = ComponentName(context, HomeButtonService::class.java).flattenToString()

    val homeServiceOn: Boolean
        get() = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').any { it.equals(homeService, true) }

    /** Switches the Home button service on or off, keeping any other accessibility services as they were. */
    fun setHomeService(on: Boolean): Boolean {
        val others = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && !it.equals(homeService, true) }
        val list = if (on) others + homeService else others
        return putSecure(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":")) &&
            putSecure(Settings.Secure.ACCESSIBILITY_ENABLED, if (list.isEmpty()) "0" else "1")
    }

    private fun putSecure(name: String, value: String): Boolean =
        runCatching { Settings.Secure.putString(resolver, name, value) }.onFailure { warn(name, it) }.getOrDefault(false)

    private fun warn(name: String, e: Throwable) = Log.w("myTvSystem", "Couldn't set $name: $e")

    fun openSystem(page: SystemPage) {
        if (!apps.start(Intent(page.action))) apps.start(Intent(Settings.ACTION_SETTINGS))
    }

    fun openInstallPermission() = apps.start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))

    // ---- Opening titles ---------------------------------------------------------------------

    /** Opens a card's title in the app it belongs to. Browse cards are the home screen's own and aren't handled here. */
    fun open(card: MediaCard): Boolean = when (val o = card.open) {
        is Open.Link -> runCatching {
            val intent = Intent.parseUri(o.intentUri, Intent.URI_INTENT_SCHEME)
            if (intent.`package` == null && intent.component == null) intent.setPackage(o.pkg)
            apps.start(intent)
        }.getOrDefault(false) || apps.launch(o.pkg)
        is Open.AllMedia -> openAllMedia(if (o.playFirst) o.play else o.page)
        is Open.Search -> search(o.query)
        is Open.Browse, is Open.Settings -> false
    }

    /** Library titles open in AllMedia through the link it gave: straight into playback, or at the title's page. */
    fun openAllMedia(link: String): Boolean =
        apps.start(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(ALLMEDIA)) || apps.launch(ALLMEDIA)

    /** Hands a title to the TV's own search (Google TV's, which looks across every service). */
    fun search(query: String): Boolean =
        apps.start(Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, query)) ||
            apps.start(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            apps.start(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")))

    /** The TV's voice and text search, for the Search tab's "search everywhere". */
    fun assistant(): Boolean = apps.start(Intent(Intent.ACTION_ASSIST)) || apps.start(Intent("android.search.action.GLOBAL_SEARCH"))

    companion object {
        const val ALLMEDIA = "app.allmedia"
    }
}
