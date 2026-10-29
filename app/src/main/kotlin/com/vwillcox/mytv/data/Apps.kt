package com.vwillcox.mytv.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvApp(
    val pkg: String,
    val component: ComponentName,
    val label: String,
    /** Made for TV: it has a TV launcher entry, not just a phone one. */
    val leanback: Boolean,
    /** Installed from the Play Store, rather than sideloaded or built in. */
    val fromPlay: Boolean,
    val system: Boolean,
    /** Sideloaded: put on the TV by its owner rather than bought into from a store or shipped with the TV. */
    val own: Boolean,
    val installedAt: Long,
)

/** The Play Store, which this app opens for anything to do with getting apps. */
const val PLAY_STORE = "com.android.vending"

/** Installers that mean the owner sideloaded the app: adb, Android's own installer, or this app. */
private val Sideloaders = setOf("com.android.shell", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.vwillcox.mytv")

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Waits for the first reading of what's installed. */
    suspend fun ready() = loaded.await()

    /** Ticks when an app is installed, updated or removed, so artwork caches can be dropped. */
    val changed = MutableStateFlow(0)

    private val icons = LruCache<String, Bitmap>(120)
    private val banners = LruCache<String, Bitmap>(80)

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                intent.data?.schemeSpecificPart?.let { icons.remove(it); banners.remove(it) }
                changed.value++
                refresh()
            }
        }, filter, Context.RECEIVER_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch { _apps.value = withContext(Dispatchers.IO) { load() }; loaded.complete(Unit) }
    }

    private fun load(): List<TvApp> {
        fun query(category: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<TvApp>()
        for ((list, leanback) in listOf(tv to true, phone to false)) for (ri in list) {
            val ai = ri.activityInfo
            if (ai.packageName == context.packageName || !seen.add(ai.packageName)) continue
            val installer = installer(ai.packageName)
            val app = ai.applicationInfo
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            out += TvApp(
                pkg = ai.packageName,
                component = ComponentName(ai.packageName, ai.name),
                label = ri.loadLabel(pm).toString(),
                leanback = leanback,
                fromPlay = installer == PLAY_STORE,
                system = system,
                own = !system && (installer == null || installer in Sideloaders),
                installedAt = runCatching { pm.getPackageInfo(ai.packageName, 0).firstInstallTime }.getOrDefault(0L),
            )
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun find(pkg: String): TvApp? = _apps.value.firstOrNull { it.pkg == pkg }

    fun label(pkg: String): String = find(pkg)?.label
        ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun installer(pkg: String) = runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull()

    /**
     * Whether [pkg] counts as the owner's own, which is what its home screen sources default to: sideloaded apps
     * yes; Play Store apps and the TV's built-in ones (where the paid services live) no.
     */
    fun isOwn(pkg: String): Boolean = find(pkg)?.own ?: runCatching {
        val system = pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
        val by = installer(pkg)
        !system && (by == null || by in Sideloaders)
    }.getOrDefault(false)

    fun launch(app: TvApp): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            component = app.component
            addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        })
    }.isSuccess

    fun launch(pkg: String): Boolean = find(pkg)?.let(::launch) ?: runCatching {
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun uninstall(pkg: String) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    fun info(pkg: String) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    fun store(pkg: String? = null) = if (pkg == null) launch(PLAY_STORE) else
        start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage(PLAY_STORE))

    fun start(intent: Intent): Boolean = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    // ---- Artwork ----------------------------------------------------------------------------

    /**
     * The app's icon filling a circle of [size] pixels, the way Google TV shows Your apps. An adaptive icon's two
     * layers are drawn whole and cut to the circle; an old-style icon sits inside a plain disc.
     */
    fun roundIcon(pkg: String, size: Int): Bitmap? {
        icons.get("$pkg@$size")?.let { return it }
        val d = runCatching { iconDrawable(pkg) }.getOrNull() ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val clip = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        canvas.clipPath(clip)
        if (d is AdaptiveIconDrawable) {
            // The layers are 108 units across of which the middle 72 are the icon proper.
            val pad = size / 4
            for (layer in listOf(d.background, d.foreground)) layer?.let {
                it.setBounds(-pad, -pad, size + pad, size + pad)
                it.draw(canvas)
            }
        } else {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2D31.toInt() })
            val inset = (size * 0.18f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(canvas)
        }
        icons.put("$pkg@$size", bmp)
        return bmp
    }

    /** The app's TV banner, or null when it only has an icon. */
    fun banner(pkg: String, width: Int): Bitmap? {
        banners.get("$pkg@$width")?.let { return it }
        val app = find(pkg)
        val d: Drawable = runCatching {
            app?.component?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(pkg)
        }.getOrNull() ?: return null
        val height = width * 9 / 16
        return d.toBitmap(width, height).also { banners.put("$pkg@$width", it) }
    }

    private fun iconDrawable(pkg: String): Drawable =
        find(pkg)?.component?.let { runCatching { pm.getActivityIcon(it) }.getOrNull() } ?: pm.getApplicationIcon(pkg)

    fun hasPermission(pkg: String, permission: String) = pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
}
