package com.vwillcox.mytv.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An app's mark lifted off its icon, for skins that draw icons their own way: white where the mark is and clear
 * elsewhere, with its inner shapes (the waves across a disc, say) in fainter white, so a skin can colour it as it
 * likes. [tone] is the colour the icon is known by, or null when it's grey, for a skin to match to its own colours.
 */
class Glyph(val bitmap: Bitmap, val tone: Int?)

internal object Glyphs {
    /**
     * [d]'s mark in a [size]-pixel square, scaled to sit inside a circle that size whatever room the app left round
     * it. The mark is, by what the icon offers:
     *  - the monochrome layer an adaptive icon carries for Android 13's themed icons, drawn by the app for this;
     *  - an adaptive icon's foreground, when that's a shape over the background rather than a picture across it;
     *  - whatever stands out from the plate the artwork sits on (Netflix's N off its black, YouTube's red off its
     *    white, leaving the white play mark as a hole), the plate being the colour just inside its edge;
     *  - or a shape with nothing round it, whole.
     */
    fun lift(d: Drawable, size: Int): Glyph? {
        val n = (size * 2).coerceIn(128, 384)
        val whole = square(n) { drawSquare(d, it, n) }
        val ink = FloatArray(n * n)
        val adaptive = d as? AdaptiveIconDrawable
        val mono = if (Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
        val front = adaptive?.foreground?.let { layer -> square(n) { canvas -> drawLayer(layer, canvas, n) } }
        // Where the mark's detail and colour are read from, and the colour round it.
        var colours = whole
        var plate: Int? = null
        when {
            mono != null -> square(n) { drawLayer(mono, it, n) }.forEachIndexed { i, c -> ink[i] = alpha(c) }
            front != null && ringCover(front, n) < 0.5f -> {
                front.forEachIndexed { i, c -> ink[i] = alpha(c) }
                colours = front
                plate = adaptive.background?.let { layer -> mean(square(n) { drawLayer(layer, it, n) }) }
            }
            else -> plate = keyed(whole, n, ink)
        }
        if (mono == null) { detail(colours, ink); seams(colours, ink, n) }
        val tone = if (mono != null) tone(whole, null) else tone(colours, ink) ?: plate?.takeIf { vividness(it) >= 0.2f }?.let(::pure)
        return fit(ink, n, size)?.let { Glyph(it, tone) }
    }

    /**
     * [banner] in [ramp]'s colours, darkest first, by how light each part is: flat steps with soft edges, like a
     * screen-printed poster. Its levels are stretched first, so a dark banner uses the whole ramp too.
     */
    fun tone(banner: Bitmap, ramp: IntArray): Bitmap {
        val w = banner.width; val h = banner.height
        val px = IntArray(w * h).also { banner.getPixels(it, 0, w, 0, 0, w, h) }
        val hist = IntArray(256)
        for (c in px) hist[(luma(c) * 255).toInt()]++
        val lo = percentile(hist, px.size, 0.03f); val hi = percentile(hist, px.size, 0.97f).coerceAtLeast(lo + 0.1f)
        val steps = ramp.size - 1
        for (i in px.indices) {
            val p = ((luma(px[i]) - lo) / (hi - lo)).coerceIn(0f, 1f) * steps
            val k = p.toInt().coerceAtMost(steps - 1)
            px[i] = (px[i] and 0xFF000000.toInt()) or (mix(ramp[k], ramp[k + 1], smoothstep(0.3f, 0.7f, p - k)) and 0xFFFFFF)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private inline fun square(n: Int, draw: (Canvas) -> Unit): IntArray {
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp))
        return IntArray(n * n).also { bmp.getPixels(it, 0, n, 0, 0, n, n); bmp.recycle() }
    }

    /** One of an adaptive icon's layers, placed as [drawSquare] places them: the middle 72 of its 108 units. */
    private fun drawLayer(layer: Drawable, canvas: Canvas, n: Int) {
        val pad = n / 4
        layer.setBounds(-pad, -pad, n + pad, n + pad)
        layer.draw(canvas)
    }

    /** How much of a ring just inside the icon's circle [px] covers: most of it for a picture, little for a shape. */
    private fun ringCover(px: IntArray, n: Int): Float {
        var count = 0; var covered = 0
        val c = n / 2f
        for (y in 0 until n) for (x in 0 until n) {
            val r = sqrt((x + 0.5f - c).let { it * it } + (y + 0.5f - c).let { it * it }) / c
            if (r in 0.8f..0.96f) { count++; if (px[y * n + x] ushr 24 >= 128) covered++ }
        }
        return if (count == 0) 0f else covered.toFloat() / count
    }

    /**
     * Fills [ink] from artwork [px] that may sit on a plate (a square, a rounded square, a disc): the plate's colour
     * is read in a band just inside the ellipse that fits round the artwork, which lies on any of those shapes, and
     * whatever differs from it is the mark. Returns the plate's colour, or null when there's no plate and the
     * artwork is a shape on its own, which is then the mark whole.
     */
    private fun keyed(px: IntArray, n: Int, ink: FloatArray): Int? {
        var left = n; var top = n; var right = -1; var bottom = -1
        for (y in 0 until n) for (x in 0 until n) if (px[y * n + x] ushr 24 >= 128) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        if (right < 0) return null
        val cx = (left + right + 1) / 2f; val cy = (top + bottom + 1) / 2f
        val hw = (right + 1 - left) / 2f; val hh = (bottom + 1 - top) / 2f
        val band = ArrayList<Int>()
        var count = 0
        for (y in top..bottom) for (x in left..right) {
            val dx = (x + 0.5f - cx) / hw; val dy = (y + 0.5f - cy) / hh
            if (dx * dx + dy * dy in 0.64f..0.88f) {
                count++
                px[y * n + x].let { if (it ushr 24 >= 230) band += it }
            }
        }
        val whole = { for (i in px.indices) ink[i] = alpha(px[i]) }
        if (count == 0 || band.size < count * 0.9f) { whole(); return null }
        val plate = median(band)
        // A gradient plate varies, so a mark has to stand out further from it to count.
        val spread = band.sumOf { distance(it, plate).toDouble() }.toFloat() / band.size
        val from = (0.1f + spread * 1.5f).coerceAtMost(0.4f)
        var area = 0f; var marked = 0f
        for (i in px.indices) {
            val a = alpha(px[i])
            ink[i] = a * smoothstep(from, from + 0.14f, distance(px[i], plate))
            area += a; marked += ink[i]
        }
        // Nothing stood out, or everything did: the plate is the mark after all.
        if (marked < area * 0.02f || marked > area * 0.85f) { whole(); return plate }
        // A mark only a little apart from its plate would come out faint, so its strongest part is made solid.
        val strengths = ink.filter { it > 0.15f }.toFloatArray().apply { sort() }
        val peak = strengths.getOrNull(strengths.size * 95 / 100) ?: 1f
        if (peak < 1f) for (i in ink.indices) ink[i] = (ink[i] / peak).coerceAtMost(1f)
        return plate
    }

    /**
     * Fades the parts of the mark much lighter or darker than most of it, so its inner shapes still show once it's
     * all one colour.
     */
    private fun detail(px: IntArray, ink: FloatArray) {
        val hist = FloatArray(64)
        var total = 0f
        for (i in px.indices) if (ink[i] > 0.5f) { hist[(luma(px[i]) * 63).toInt()] += ink[i]; total += ink[i] }
        if (total == 0f) return
        var m = 0; var below = 0f
        while (m < 63 && below + hist[m] < total / 2) { below += hist[m]; m++ }
        val median = m / 63f
        for (i in px.indices) if (ink[i] > 0f) ink[i] *= 1f - 0.6f * smoothstep(0.2f, 0.36f, abs(luma(px[i]) - median))
    }

    /**
     * Cuts a thin gap along the borders between differently coloured parts of the mark (the quarters of a
     * four-colour pinwheel, say), which would otherwise run together once it's all one colour.
     */
    private fun seams(px: IntArray, ink: FloatArray, n: Int) {
        val r = (n / 56).coerceAtLeast(1)
        val hsv = FloatArray(3)
        val hue = FloatArray(n * n) { i ->
            if (ink[i] < 0.5f) -1f else { Color.colorToHSV(px[i], hsv); if (hsv[1] * hsv[2] >= 0.3f) hsv[0] else -1f }
        }
        fun apart(a: Float, b: Float) = a >= 0f && b >= 0f && abs(a - b).let { minOf(it, 360 - it) } > 45f
        val cut = BooleanArray(n * n)
        for (y in r until n - r) for (x in r until n - r) {
            val i = y * n + x
            if (hue[i] < 0f) continue
            cut[i] = apart(hue[i], hue[i - r]) || apart(hue[i], hue[i + r]) || apart(hue[i], hue[i - r * n]) || apart(hue[i], hue[i + r * n])
        }
        for (i in ink.indices) if (cut[i]) ink[i] = 0f
    }

    /**
     * The colour of the mark's vivid parts, by their average hue, or null when it's mostly grey or its colours are
     * spread round the wheel with none leading.
     */
    private fun tone(px: IntArray, ink: FloatArray?): Int? {
        val hsv = FloatArray(3)
        var sx = 0f; var sy = 0f; var vivid = 0f; var weight = 0f
        for (i in px.indices step 2) {
            val w = (ink?.get(i) ?: 1f) * alpha(px[i])
            if (w < 0.3f) continue
            Color.colorToHSV(px[i], hsv)
            val v = hsv[1] * hsv[2]
            val angle = Math.toRadians(hsv[0].toDouble())
            sx += (cos(angle) * v * w).toFloat(); sy += (sin(angle) * v * w).toFloat()
            vivid += v * w; weight += w
        }
        if (weight == 0f || vivid / weight < 0.2f || sqrt(sx * sx + sy * sy) < vivid * 0.4f) return null
        val hue = ((Math.toDegrees(atan2(sy, sx).toDouble()) + 360) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))
    }

    private fun pure(c: Int): Int {
        val hsv = FloatArray(3).also { Color.colorToHSV(c, it) }
        return Color.HSVToColor(floatArrayOf(hsv[0], 0.85f, 0.95f))
    }

    private fun vividness(c: Int): Float = FloatArray(3).also { Color.colorToHSV(c, it) }.let { it[1] * it[2] }

    /**
     * The [ink] as white of that strength, cut to the mark's box and scaled into [size] pixels: wide marks fill the
     * width, compact ones the same area as each other, and nothing reaches past the circle the size describes.
     */
    private fun fit(ink: FloatArray, n: Int, size: Int): Bitmap? {
        var left = n; var top = n; var right = -1; var bottom = -1
        for (y in 0 until n) for (x in 0 until n) if (ink[y * n + x] > 0.15f) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        if (right < 0) return null
        val src = Bitmap.createBitmap(IntArray(n * n) { ((ink[it].coerceIn(0f, 1f) * 255).toInt() shl 24) or 0xFFFFFF }, n, n, Bitmap.Config.ARGB_8888)
        val gw = (right + 1 - left).toFloat(); val gh = (bottom + 1 - top).toFloat()
        val scale = minOf(0.86f * size / gw, 0.86f * size / gh, 0.66f * size / sqrt(gw * gh))
        val w = gw * scale; val h = gh * scale
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(left, top, right + 1, bottom + 1), RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun alpha(c: Int) = (c ushr 24) / 255f
    private fun luma(c: Int) = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 255_000f

    /** How far apart two colours are, 0 to 1. */
    private fun distance(a: Int, b: Int): Float {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF); val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF); val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.67f
    }

    private fun median(colours: List<Int>): Int {
        fun mid(shift: Int) = colours.map { it shr shift and 0xFF }.sorted()[colours.size / 2]
        return Color.rgb(mid(16), mid(8), mid(0))
    }

    private fun mean(px: IntArray): Int? {
        var r = 0L; var g = 0L; var b = 0L; var count = 0
        for (c in px) if (c ushr 24 >= 128) { r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; count++ }
        return if (count == 0) null else Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun percentile(hist: IntArray, total: Int, p: Float): Float {
        var acc = 0
        for (i in hist.indices) { acc += hist[i]; if (acc >= total * p) return i / 255f }
        return 1f
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    private fun smoothstep(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
