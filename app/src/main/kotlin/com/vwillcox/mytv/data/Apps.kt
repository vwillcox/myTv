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
import android.graphics.Color
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
import org.xmlpull.v1.XmlPullParser
import kotlin.math.hypot

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

private const val AndroidNs = "http://schemas.android.com/apk/res/android"

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
    private val glyphs = LruCache<String, Glyph>(120)

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
                // Artwork is kept per size, under "package@size".
                intent.data?.schemeSpecificPart?.let { pkg ->
                    for (cache in listOf(icons, banners, glyphs)) cache.snapshot().keys.filter { it.startsWith("$pkg@") }.forEach(cache::remove)
                }
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
     * The app's icon filling a circle of [size] pixels, the way Google TV shows Your apps. An adaptive icon, or the
     * round icon an app declares, is cut to the circle. Anything else is skinned to match: artwork that's solid across
     * the middle (a square, a rounded square, a circle with room round it, a banner) is enlarged until that fills the
     * circle, and a shape on its own is set on a plain disc.
     */
    fun roundIcon(pkg: String, size: Int): Bitmap? {
        icons.get("$pkg@$size")?.let { return it }
        val d = runCatching { iconDrawable(pkg) }.getOrNull() ?: return null
        val half = size / 2f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Drawn once to see what the artwork covers, then again for real, moved and scaled to suit.
        drawSquare(d, Canvas(bmp), size)
        val art = coverage(bmp)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })
        if (art != null) {
            // Shows through wherever the artwork is see-through, like the waves cut out of Spotify's.
            canvas.drawColor(if (art.light) 0xFF2B2D31.toInt() else Color.WHITE)
            when {
                art.solid -> canvas.centre(art, half / art.radius, half)
                // An adaptive icon's foreground is already placed for a circle; an old-style shape is fitted inside it.
                d !is AdaptiveIconDrawable -> canvas.centre(art, size * 0.8f / hypot(art.width.toFloat(), art.height.toFloat()), half)
            }
        }
        drawSquare(d, canvas, size)
        icons.put("$pkg@$size", bmp)
        return bmp
    }

    /**
     * What an icon's artwork covers: the box round it, the biggest circle across that box, whether that circle's rim
     * is solid (holes inside it are fine), and whether the artwork is mostly light.
     */
    private class Coverage(val cx: Float, val cy: Float, val width: Int, val height: Int, val radius: Float, val solid: Boolean, val light: Boolean)

    private fun coverage(bmp: Bitmap): Coverage? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        // The box ignores anything faint, like the drop shadow under an old round icon.
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] ushr 24 >= 128) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        if (right < 0) return null
        val width = right + 1 - left; val height = bottom + 1 - top
        val cx = (left + right + 1) / 2f; val cy = (top + bottom + 1) / 2f
        // A shade inside the edge, so a round icon's soft rim doesn't show as a ring.
        val r = minOf(width, height) / 2f * 0.98f
        var inside = 0; var solid = 0; var light = 0f; var weight = 0f
        for (y in top..bottom) for (x in left..right) {
            val c = px[y * w + x]; val a = c ushr 24
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val d2 = dx * dx + dy * dy
            if (d2 <= r * r && d2 >= r * r * 0.64f) { inside++; if (a >= 200) solid++ }
            light += a * ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 255_000f
            weight += a
        }
        return Coverage(cx, cy, width, height, r, solid >= inside * 0.98f, light / weight > 0.75f)
    }

    /** Moves [art]'s centre to the middle of a canvas [half] × 2 across, scaled by [scale]. */
    private fun Canvas.centre(art: Coverage, scale: Float, half: Float) {
        translate(half, half); scale(scale, scale); translate(-art.cx, -art.cy)
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

    /** The app's mark alone in a [size]-pixel square, for a skin to colour its own way: see [Glyph]. */
    fun glyph(pkg: String, size: Int): Glyph? {
        glyphs.get("$pkg@$size")?.let { return it }
        val d = runCatching { iconDrawable(pkg) }.getOrNull() ?: return null
        return runCatching { Glyphs.lift(d, size) }.getOrNull()?.also { glyphs.put("$pkg@$size", it) }
    }

    /** The app's TV banner in [ramp]'s colours, darkest first, or null when it only has an icon. */
    fun tonedBanner(pkg: String, width: Int, ramp: IntArray): Bitmap? {
        val key = "$pkg@$width#${ramp.contentHashCode()}"
        banners.get(key)?.let { return it }
        return banner(pkg, width)?.let { Glyphs.tone(it, ramp) }?.also { banners.put(key, it) }
    }

    /** The launcher activity's icon, unless that's old-style and the app declares a round one, which TVs leave unused. */
    private fun iconDrawable(pkg: String): Drawable {
        val component = find(pkg)?.component
        val icon = component?.let { runCatching { pm.getActivityIcon(it) }.getOrNull() } ?: pm.getApplicationIcon(pkg)
        if (icon is AdaptiveIconDrawable) return icon
        return runCatching { declaredRoundIcon(pkg, component?.className) }.getOrNull() ?: icon
    }

    /** The `android:roundIcon` in [pkg]'s manifest: [activity]'s own, else the app's. */
    private fun declaredRoundIcon(pkg: String, activity: String?): Drawable? {
        val app = pm.getApplicationInfo(pkg, 0)
        val assets = pm.getResourcesForApplication(app).assets
        // The app's resources carry the system's manifest and its split APKs' too; its own names it and no split.
        for (cookie in 1..32) {
            val xml = runCatching { assets.openXmlResourceParser(cookie, "AndroidManifest.xml") }.getOrNull() ?: continue
            var forApp = 0; var forActivity = 0
            try {
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType != XmlPullParser.START_TAG) continue
                    val round = xml.getAttributeResourceValue(AndroidNs, "roundIcon", 0)
                    when (xml.name) {
                        "manifest" -> if (xml.getAttributeValue(null, "package") != pkg || xml.getAttributeValue(null, "split") != null) break
                        "application" -> forApp = round
                        "activity", "activity-alias" -> {
                            val name = xml.getAttributeValue(AndroidNs, "name")?.let { if (it.startsWith(".")) pkg + it else if ('.' !in it) "$pkg.$it" else it }
                            if (round != 0 && name == activity) forActivity = round
                        }
                    }
                }
            } finally { xml.close() }
            val id = forActivity.takeIf { it != 0 } ?: forApp
            if (id != 0) return pm.getDrawable(pkg, id, app)
        }
        return null
    }

    fun hasPermission(pkg: String, permission: String) = pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
}

/** Draws [d] into a [size]-pixel square: an adaptive icon's layers whole, anything else at its own shape, centred. */
internal fun drawSquare(d: Drawable, canvas: Canvas, size: Int) {
    if (d is AdaptiveIconDrawable) {
        // The layers are 108 units across of which the middle 72 are the icon proper.
        val pad = size / 4
        for (layer in listOf(d.background, d.foreground)) layer?.let {
            it.setBounds(-pad, -pad, size + pad, size + pad)
            it.draw(canvas)
        }
    } else {
        // Some apps give their banner as their icon, which mustn't be squashed square.
        val w = d.intrinsicWidth; val h = d.intrinsicHeight
        val (bw, bh) = when {
            w <= 0 || h <= 0 || w == h -> size to size
            w > h -> size to size * h / w
            else -> size * w / h to size
        }
        d.setBounds((size - bw) / 2, (size - bh) / 2, (size + bw) / 2, (size + bh) / 2)
        d.draw(canvas)
    }
}
