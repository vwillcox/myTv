package com.vwillcox.mytv.ui.lcars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vwillcox.mytv.ui.Type

private val Upright = 24.dp
private val PanelArm = 18.dp

/**
 * A side panel as an LCARS screen of its own: an elbow down its left whose upright is cut into blocks that flicker,
 * the title set into the top arm, and a thin arm along the foot. The arms run out each time a page opens.
 */
@Composable
internal fun LcarsPanelFace(title: String, subtitle: String?, group: Modifier, list: Modifier, state: LazyListState, content: LazyListScope.() -> Unit) {
    val time = LocalLcarsTime.current
    val motion = LocalLcarsMotion.current
    val open = remember { Animatable(1f) }
    LaunchedEffect(title) { if (motion) { open.snapTo(0f); open.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) } }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), Alignment.CenterEnd) {
        Box(Modifier.width(440.dp).fillMaxHeight().background(Color.Black).then(group).padding(start = 12.dp, end = 22.dp, top = 14.dp, bottom = 14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val side = Upright.toPx(); val arm = PanelArm.toPx(); val slice = 4.dp.toPx()
                val topBlock = 56.dp.toPx(); val footBlock = 40.dp.toPx(); val foot = 8.dp.toPx()
                val reach = side + 40.dp.toPx()
                drawPath(elbow(topBlock, side, arm, 26.dp.toPx(), 12.dp.toPx(), reach, bottom = false), Lcars.Orange)
                translate(top = size.height - footBlock) {
                    drawPath(elbow(footBlock, side, foot, 20.dp.toPx(), 10.dp.toPx(), reach, bottom = true), Lcars.Lilac)
                }
                clipRect(right = reach + (size.width - reach) * open.value) {
                    bar(reach + slice, size.width - 150.dp.toPx(), 0f, arm, slice, listOf(Piece(60.dp.toPx(), Lcars.Peach), Piece(0f, Lcars.Orange)))
                    bar(reach + slice, size.width, size.height - foot, foot, slice, listOf(Piece(0f, Lcars.BlueBell), Piece(70.dp.toPx(), Lcars.Peach), Piece(28.dp.toPx(), Lcars.Orange)))
                }
                // The upright between the elbows, in blocks of a few sizes, any of which may light up for a moment.
                val t = time.value
                val colours = listOf(Lcars.BlueBell, Lcars.Peach, Lcars.Lilac, Lcars.Tangerine, Lcars.Periwinkle)
                val heights = listOf(64f, 28f, 96f, 40f, 22f, 72f, 36f)
                var y = topBlock + slice
                var i = 0
                while (y < size.height - footBlock - slice) {
                    val h = minOf(heights[i % heights.size].dp.toPx(), size.height - footBlock - slice - y)
                    val lit = chance(40 + i, t, 520L + i * 61L) > 0.9f
                    drawRect(if (lit) Lcars.Lit else colours[i % colours.size], Offset(0f, y), Size(side, h), alpha = open.value)
                    y += h + slice; i++
                }
            }
            Text(
                "  ${title.uppercase()}  ", style = Type.panelTitle, color = Lcars.Orange, maxLines = 1, textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 0.dp).background(Color.Black).graphicsLayer { translationY = -6.dp.toPx() },
            )
            Column(Modifier.fillMaxSize().padding(start = Upright + 14.dp, top = PanelArm + 18.dp, bottom = 22.dp)) {
                subtitle?.let { Text(it, style = Type.itemSub, color = Lcars.Peach.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).then(list),
                    state = state, contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { content() }
            }
        }
    }
}

/**
 * A panel item as an LCARS button: a cap of its colour at the left, its name in peach beside it. Focused, the whole
 * row fills with the cap's colour after a quick flash and the words turn black.
 */
@Composable
internal fun LcarsItemFace(
    title: String, sub: String?, icon: ImageVector?, trailing: (@Composable (focused: Boolean) -> Unit)?, chevron: Boolean,
    focused: Boolean, control: Modifier,
) {
    val flash by rememberFlash(focused)
    val colour = pick(title, listOf(Lcars.Orange, Lcars.Peach, Lcars.Lilac, Lcars.BlueBell, Lcars.Tangerine, Lcars.Sunflower))
    val ink = if (focused) Color.Black else Lcars.Peach
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).then(control).drawBehind {
            if (focused) capsule(lerp(colour.dim(0.4f), colour, flash), Offset.Zero, size, left = true, right = false)
            else capsule(colour, Offset.Zero, Size(12.dp.toPx(), size.height), left = true, right = false)
        }.padding(start = 22.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (focused) Color.Black else colour, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = Type.item, color = ink)
            sub?.let { Text(it, style = Type.itemSub, color = if (focused) Color.Black.copy(alpha = 0.72f) else Lcars.BlueBell) }
        }
        trailing?.invoke(focused)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = if (focused) Color.Black else colour)
    }
}

/** On and off as an indicator block: lit with ON, or dark with OFF. */
@Composable
internal fun LcarsSwitch(on: Boolean, focused: Boolean) {
    val fill = when {
        on && focused -> Color.Black
        on -> Lcars.Orange
        else -> Color.Transparent
    }
    val ink = when {
        on && focused -> Lcars.Orange
        on -> Color.Black
        focused -> Color.Black.copy(alpha = 0.6f)
        else -> Lcars.Faint
    }
    Box(
        Modifier.padding(start = 8.dp).size(width = 52.dp, height = 22.dp).clip(RoundedCornerShape(11.dp)).background(fill)
            .then(if (on) Modifier else Modifier.border(2.dp, if (focused) Color.Black.copy(alpha = 0.5f) else Lcars.Faint, RoundedCornerShape(11.dp))),
        Alignment.Center,
    ) { Text(if (on) "ON" else "OFF", style = Type.badge, color = ink) }
}

/** A choice as a small square light: filled when chosen. */
@Composable
internal fun LcarsChoice(selected: Boolean, focused: Boolean) {
    val colour = if (focused) Color.Black else Lcars.Orange
    Box(
        Modifier.padding(start = 8.dp).size(18.dp).clip(RoundedCornerShape(3.dp))
            .then(if (selected) Modifier.background(colour) else Modifier.border(2.dp, if (focused) colour else Lcars.Faint, RoundedCornerShape(3.dp))),
    )
}

/** A heading in a panel: orange capitals, then a thin bar to the edge. */
@Composable
internal fun LcarsHeading(text: String) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = LcarsText.label, color = Lcars.Orange)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Lcars.Orange.copy(alpha = 0.6f)))
    }
}

@Composable
internal fun LcarsNote(text: String) =
    Text(text, style = Type.itemSub, color = Lcars.Peach.copy(alpha = 0.78f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
