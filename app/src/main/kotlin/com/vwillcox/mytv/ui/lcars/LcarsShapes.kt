package com.vwillcox.mytv.ui.lcars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * An LCARS elbow, [height] tall: a [sidebar]-wide upright turning through a rounded corner into an arm [bar] thick
 * along its top (or, if [bottom], its bottom), the arm reaching [reach] from the left. [outer] and [inner] are the
 * corner's radii outside and in.
 */
internal fun elbow(height: Float, sidebar: Float, bar: Float, outer: Float, inner: Float, reach: Float, bottom: Boolean): Path = Path().apply {
    fun y(v: Float) = if (bottom) height - v else v
    moveTo(0f, y(height))
    lineTo(0f, y(outer))
    arcTo(Rect(0f, if (bottom) height - 2 * outer else 0f, 2 * outer, if (bottom) height else 2 * outer), 180f, if (bottom) -90f else 90f, false)
    lineTo(reach, y(0f))
    lineTo(reach, y(bar))
    lineTo(sidebar + inner, y(bar))
    val top = if (bottom) height - bar - 2 * inner else bar
    arcTo(Rect(sidebar, top, sidebar + 2 * inner, top + 2 * inner), if (bottom) 90f else 270f, if (bottom) 90f else -90f, false)
    lineTo(sidebar, y(height))
    close()
}

/** A piece of an LCARS bar: [width] long, or whatever is left over if 0. */
internal class Piece(val width: Float, val colour: Color)

/**
 * A bar [thickness] thick from [from] to [to] at [y], cut into [pieces] with black slices of [slice] between them,
 * the last rounded off at the end. Returns where each piece starts and ends, for drawing over them.
 */
internal fun DrawScope.bar(from: Float, to: Float, y: Float, thickness: Float, slice: Float, pieces: List<Piece>, alpha: Float = 1f): List<ClosedFloatingPointRange<Float>> {
    val fixed = pieces.sumOf { it.width.toDouble() }.toFloat() + slice * (pieces.size - 1)
    val flex = pieces.count { it.width == 0f }
    val spare = ((to - from - fixed) / flex.coerceAtLeast(1)).coerceAtLeast(0f)
    var x = from
    return pieces.mapIndexed { i, p ->
        val w = if (p.width == 0f) spare else p.width
        val end = minOf(x + w, to)
        if (i == pieces.lastIndex) {
            val r = thickness / 2
            drawRoundRect(p.colour, Offset(x, y), Size(end - x, thickness), CornerRadius(r, r), alpha = alpha)
            // Square off the start, so only the far end is round.
            drawRect(p.colour, Offset(x, y), Size(minOf(r, end - x), thickness), alpha = alpha)
        } else drawRect(p.colour, Offset(x, y), Size((end - x).coerceAtLeast(0f), thickness), alpha = alpha)
        (x..end).also { x = end + slice }
    }
}

/** A block with its left (or right) end rounded right off, like the ends of LCARS buttons. */
internal fun DrawScope.capsule(colour: Color, offset: Offset, size: Size, left: Boolean = true, right: Boolean = true) {
    val r = CornerRadius(size.height / 2)
    val path = Path().apply {
        addRoundRect(RoundRect(Rect(offset, size), topLeft = if (left) r else CornerRadius.Zero, bottomLeft = if (left) r else CornerRadius.Zero,
            topRight = if (right) r else CornerRadius.Zero, bottomRight = if (right) r else CornerRadius.Zero))
    }
    drawPath(path, colour)
}

/**
 * LCARS focus round a card: a thick upright each side, rounded on the outside, joined by thin rails cut into slices,
 * a little way out from the card. [strength] fades it from unlit (0) to lit (1).
 */
internal fun DrawScope.bracket(colour: Color, strength: Float) {
    val gap = 5.dp.toPx(); val upright = 7.dp.toPx(); val rail = 2.5.dp.toPx(); val slice = 5.dp.toPx()
    val c = lerp(colour.dim(0.35f), colour, strength)
    val w = size.width; val h = size.height
    val top = -gap; val height = h + 2 * gap
    capsule(c, Offset(-gap - upright, top), Size(upright, height), left = true, right = false)
    capsule(c, Offset(w + gap, top), Size(upright, height), left = false, right = true)
    // The rails, sliced differently top and bottom.
    fun rail(y: Float, cuts: List<Float>) {
        var x = -gap
        for (cut in cuts + 1f) {
            val end = if (cut >= 1f) w + gap else w * cut
            if (end > x) drawRect(c, Offset(x, y), Size(end - x, rail))
            x = end + slice
        }
    }
    rail(top, listOf(0.42f, 0.56f))
    rail(top + height - rail, listOf(0.18f, 0.27f))
}

/** Grows a focused card by [scale] and puts an LCARS bracket round it, after a quick flash. */
@Composable
internal fun Modifier.lcarsFocus(focused: Boolean, scale: Float, colour: Color = Lcars.Orange): Modifier {
    val flash by rememberFlash(focused)
    return graphicsLayer { scaleX = scale; scaleY = scale }.drawWithContent {
        drawContent()
        if (focused) bracket(colour, flash)
    }
}
