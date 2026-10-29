@file:OptIn(ExperimentalFoundationApi::class)

package com.vwillcox.mytv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Keeps the focused card at the start of its row as focus moves along it, the way Google TV's rows scroll:
 * the row slides under a fixed focus rather than focus wandering to the edge.
 */
class PivotSpec(private val leadingPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - leadingPx
}

/** For the page itself, which scrolls row by row from focus changes rather than chasing every card. */
object NoScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

fun KeyEvent.isSelect() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.ButtonA

private class Press { var down = false; var long = false }

/**
 * Focusable and chosen with OK. Holding OK calls [onLongClick] instead, once. A press only counts if it started
 * here, so the OK that closes a panel doesn't also choose whatever gets focus after it.
 */
fun Modifier.tvClickable(
    interaction: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val press = remember { Press() }
    onKeyEvent { e ->
        if (!e.isSelect()) return@onKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) { press.down = true; press.long = false }
                else if (press.down && !press.long && onLongClick != null) { press.long = true; onLongClick() }
                true
            }
            KeyEventType.KeyUp -> {
                if (press.down && !press.long) onClick()
                press.down = false
                true
            }
            else -> false
        }
    }.focusable(interactionSource = interaction ?: remember { MutableInteractionSource() })
}
