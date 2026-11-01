package com.vwillcox.mytv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The colours a picture throws on the screen round it: [deep], what it mostly is, darkened, to wash the dark under the
 * words and the rows with; and [glow], its brightest colour, to light the card it's on and the open tab.
 */
@Immutable
data class ArtTones(val deep: Color, val glow: Color)

/** The colours pictures give, each worked out once from a thumbnail of it. */
object ArtColours {
    private val known = LruCache<String, ArtTones>(400)
    /** A picture with no colour to speak of, which leaves the screen as it is. */
    private val None = ArtTones(Color.Unspecified, Color.Unspecified)

    fun cached(url: String?): ArtTones? = url?.let { known.get(it) }?.takeIf { it !== None }

    /** Null for a grey picture, or one that won't load (which is tried again next time). */
    suspend fun of(context: Context, url: String): ArtTones? {
        known.get(url)?.let { return it.takeIf { t -> t !== None } }
        val request = ImageRequest.Builder(context).data(url).size(64, 64).allowHardware(false)
            .memoryCachePolicy(CachePolicy.READ_ONLY).build()
        val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return null
        val tones = withContext(Dispatchers.Default) { tonesOf(image.toBitmap()) }
        known.put(url, tones ?: None)
        return tones
    }

    /**
     * Sorts a thumbnail's colourful pixels by hue. The hue covering most of it, however dark, makes [ArtTones.deep];
     * the one that catches the eye (bright and saturated) makes [ArtTones.glow]. Both are set to a fixed brightness,
     * so the words over them read the same whatever the picture.
     */
    internal fun tonesOf(bitmap: Bitmap): ArtTones? {
        val small = if (bitmap.width > 48 || bitmap.height > 48) Bitmap.createScaledBitmap(bitmap, 48, 27, true) else bitmap
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val area = FloatArray(Bins); val vivid = FloatArray(Bins); val sat = FloatArray(Bins)
        val x = FloatArray(Bins); val y = FloatArray(Bins)
        val hsv = FloatArray(3)
        for (p in px) {
            android.graphics.Color.colorToHSV(p, hsv)
            val (h, s, v) = hsv
            if (s < 0.12f || v < 0.08f) continue
            val b = (h / 360f * Bins).toInt().coerceIn(0, Bins - 1)
            val a = s * min(v * 2.5f, 1f)
            area[b] += a; vivid[b] += s * v * v; sat[b] += s * a
            val r = Math.toRadians(h.toDouble())
            x[b] += (cos(r) * a).toFloat(); y[b] += (sin(r) * a).toFloat()
        }
        // How much of the picture has colour at all: a grey one gives a faint tint, or none.
        val colourful = area.sum() / px.size
        if (colourful < 0.03f) return null
        fun peak(w: FloatArray) = (0 until Bins).maxBy { w[(it + Bins - 1) % Bins] + 2 * w[it] + w[(it + 1) % Bins] }
        fun hueAt(b: Int): Float {
            var sx = 0f; var sy = 0f
            for (i in -1..1) { val j = (b + i + Bins) % Bins; sx += x[j]; sy += y[j] }
            return ((Math.toDegrees(atan2(sy, sx).toDouble()).toFloat() + 360f) % 360f)
        }
        val strength = (colourful / 0.3f).coerceIn(0.35f, 1f)
        val d = peak(area); val g = peak(vivid)
        val gSat = (sat[g] / area[g].coerceAtLeast(1e-3f)).coerceIn(0.45f, 0.8f)
        return ArtTones(
            deep = hsv(hueAt(d), 0.66f * strength, 0.2f),
            glow = hsv(hueAt(g), gSat * (0.5f + 0.5f * strength), 1f),
        )
    }

    private const val Bins = 24

    private fun hsv(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
}

/**
 * The colours of the title in the banner: [target], the title's own, and [deep] and [glow] moving smoothly from one
 * title's to the next. One for the life of the screen, so a new title redraws what reads it rather than recomposing
 * everything under it; read [deep] and [glow] while drawing.
 */
@Stable
class Ambience internal constructor(
    private val deepState: State<Color>, private val glowState: State<Color>, private val targetState: State<ArtTones?>,
) {
    val deep: Color get() = deepState.value
    val glow: Color get() = glowState.value
    /** Null while the banner's picture has no colour to give, or there's no banner. */
    val target: ArtTones? get() = targetState.value
}

/** The banner's colours, when the owner has the screen take them (Look.ambient); null otherwise. */
val LocalAmbience = staticCompositionLocalOf<Ambience?> { null }

/** Gives [content] the colours of the picture at [url], or the skin's own dark while there's none. */
@Composable
fun ProvideAmbience(url: () -> String?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tones = LocalTones.current
    val target = remember { mutableStateOf<ArtTones?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow(url).distinctUntilChanged().collectLatest { u -> target.value = u?.let { ArtColours.of(context, it) } }
    }
    val deep = animateColorAsState(target.value?.deep ?: tones.bg, tween(900), label = "deep")
    // Without a colour the light fades out rather than going white.
    val glow = animateColorAsState(target.value?.glow ?: tones.focus.copy(alpha = 0f), tween(900), label = "glow")
    val ambience = remember { Ambience(deep, glow, target) }
    CompositionLocalProvider(LocalAmbience provides ambience, content = content)
}

/**
 * How brightly a focused card or tile glows, and in what colour: its picture's at [url], or [tint] without one.
 * Nothing is worked out for a picture until its card first has focus. Null while the screen doesn't take colours.
 */
@Composable
fun rememberGlow(url: String?, tint: Color, focused: Boolean): Pair<Color, State<Float>>? {
    if (LocalAmbience.current == null) return null
    val context = LocalContext.current
    var tones by remember(url) { mutableStateOf(ArtColours.cached(url)) }
    if (focused && tones == null && url != null) LaunchedEffect(url) { tones = ArtColours.of(context, url) }
    val colour = tones?.glow ?: if (url == null) lerp(tint, Color.White, 0.35f) else null
    val on = animateFloatAsState(if (focused && colour != null) 1f else 0f, tween(if (focused) 320 else 160), label = "glow")
    return (colour ?: Color.White) to on
}

/** A card's glow: its own picture's colour, or the colour its name is tiled in when it has none. */
@Composable
fun rememberGlow(card: MediaCard, focused: Boolean) = rememberGlow(card.image, Color(Feed.tint(card.title)), focused)

/**
 * Light spilling from behind a focused card in [colour], at [level] (0 to 1), blurred out past its rounded edges.
 * Android before 9 can't blur this on the GPU, so it goes without.
 */
fun Modifier.glow(colour: Color, level: State<Float>, corner: Dp, spread: Dp = 20.dp): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) this else drawBehind {
        val l = level.value
        if (l <= 0.01f) return@drawBehind
        val radius = spread.toPx()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colour.copy(alpha = 0.85f * l).toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        val inset = 2.dp.toPx()
        val r = corner.toPx() + inset
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(-inset, inset, size.width + inset, size.height + inset * 3, r, r, paint) }
    }
