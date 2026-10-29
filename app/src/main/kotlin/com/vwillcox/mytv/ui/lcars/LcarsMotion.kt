package com.vwillcox.mytv.ui.lcars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vwillcox.mytv.data.Look
import com.vwillcox.mytv.ui.LocalSounds
import com.vwillcox.mytv.ui.Silent
import kotlinx.coroutines.delay

/**
 * The panels' clock, in milliseconds, for the flashing and the readouts. It's read while drawing, never while
 * composing, so as it runs only drawing repeats. It stands still when motion is off or the screen isn't showing.
 */
internal val LocalLcarsTime = staticCompositionLocalOf<State<Long>> { mutableLongStateOf(0L) }

/** Whether the owner wants the panels moving by themselves. */
internal val LocalLcarsMotion = staticCompositionLocalOf { true }

/** The skin's sounds and clock, round everything. */
@Composable
internal fun LcarsProvide(look: Look, content: @Composable () -> Unit) {
    val sounds = remember(look.sounds) { if (look.sounds) LcarsSounds() else null }
    DisposableEffect(sounds) { onDispose { sounds?.release() } }
    val time = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(look.motion, lifecycle) {
        if (!look.motion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // About 30 times a second, which is plenty for blinking and keeps the TV's GPU free for scrolling.
            while (true) { withFrameMillis { time.longValue = it }; delay(30) }
        }
    }
    CompositionLocalProvider(LocalSounds provides (sounds ?: Silent), LocalLcarsTime provides time, LocalLcarsMotion provides look.motion, content = content)
}

/** A steady pseudo-random number from 0 to 1 for [seed], new for each [period]-long slot of [time]. */
internal fun chance(seed: Int, time: Long, period: Long): Float {
    var h = seed * -0x61c88647 xor (time / period).toInt() * -0x7a143589
    h = h xor (h ushr 15); h *= 0x2C1B3C6D
    h = h xor (h ushr 12); h *= 0x297A2D39
    h = h xor (h ushr 15)
    return (h ushr 8) / 16_777_216f
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * 1 while focused, reached through a quick double flash the way a panel acknowledges a touch; 0 otherwise. Read it
 * while drawing where possible.
 */
@Composable
internal fun rememberFlash(focused: Boolean): State<Float> {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (!focused) { flash.snapTo(0f); return@LaunchedEffect }
        flash.animateTo(1f, keyframes {
            durationMillis = 300
            1f at 0; 0.15f at 70; 1f at 130; 0.4f at 190; 1f at 250
        })
    }
    return flash.asState()
}

/**
 * Columns of numbers that keep changing, the readout every TNG panel has running in a corner. Each column ticks at
 * its own pace, a row behind the one above, and now and then a cell is picked out in a block. As many rows as fit,
 * down at the foot of the space.
 */
@Composable
internal fun DataCascade(modifier: Modifier, seed: Int, colours: List<Color> = listOf(Lcars.Peach, Lcars.Orange, Lcars.BlueBell, Lcars.Lilac, Lcars.Peach)) {
    val time = LocalLcarsTime.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val digits = remember(measurer) { (0..9).map { measurer.measure("$it", LcarsText.data) } }
    // How many digits each column holds, a fixed jumble.
    val widths = remember(seed) { IntArray(16) { 1 + (chance(seed * 31 + it, 0, 1) * 5).toInt() } }
    Canvas(modifier) {
        val t = time.value
        val cell = digits.maxOf { it.size.width }.toFloat()
        val line = digits[0].size.height.toFloat()
        val rows = (size.height / line).toInt().coerceAtLeast(1)
        val top = size.height - rows * line
        val gap = 10.dp.toPx()
        var x = 0f
        var col = 0
        while (col < widths.size) {
            val w = widths[col] * cell
            if (x + w > size.width) break
            val period = 260L + (chance(seed + col, 0, 1) * 900).toLong()
            for (row in 0 until rows) {
                val s = seed * 977 + col * 131 + row * 17
                val slot = t + row * 140L
                val y = top + row * line
                val picked = chance(s + 5, slot, period * 3) > 0.9f
                if (picked) drawRect(Lcars.Orange, Offset(x - 2.dp.toPx(), y + 1.dp.toPx()), Size(w + 4.dp.toPx(), line - 2.dp.toPx()))
                val colour = if (picked) Color.Black else colours[(col + row) % colours.size]
                for (d in 0 until widths[col]) {
                    val digit = (chance(s + d * 7, slot, period) * 10).toInt().coerceAtMost(9)
                    drawText(digits[digit], colour, Offset(x + d * cell, y))
                }
            }
            x += w + gap
            col++
        }
    }
}

/** A cluster of small lights blinking out of step. */
@Composable
internal fun Blinkers(modifier: Modifier, seed: Int, columns: Int = 4, rows: Int = 3) {
    val time = LocalLcarsTime.current
    val colours = listOf(Lcars.Orange, Lcars.Lilac, Lcars.Peach, Lcars.BlueBell, Lcars.Mars)
    Canvas(modifier) {
        val t = time.value
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (columns - 1)) / columns
        val h = (size.height - gap * (rows - 1)) / rows
        for (r in 0 until rows) for (c in 0 until columns) {
            val i = r * columns + c
            val on = chance(seed * 53 + i, t + i * 77L, 420L + i * 37L) > 0.45f
            val colour = colours[(i * 3 + seed) % colours.size]
            drawRoundRect(if (on) colour else colour.dim(0.22f), Offset(c * (w + gap), r * (h + gap)), Size(w, h), CornerRadius(h / 2))
        }
    }
}
