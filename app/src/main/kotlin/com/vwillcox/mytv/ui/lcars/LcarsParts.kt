package com.vwillcox.mytv.ui.lcars

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.TvApp
import com.vwillcox.mytv.ui.Size as Sizes
import com.vwillcox.mytv.ui.TitleKind
import com.vwillcox.mytv.ui.Type
import com.vwillcox.mytv.ui.rememberArtwork

/**
 * Headings as LCARS headers: a rounded cap, the words, then a bar running on to a number at the far end. A focused
 * row's header is bigger and lit, and light runs along its bar.
 */
@Composable
internal fun LcarsTitle(text: String, kind: TitleKind, focused: Boolean, modifier: Modifier) {
    val colour = pick(text)
    val time = LocalLcarsTime.current
    val lit by animateFloatAsState(if (focused || kind == TitleKind.PAGE) 1f else 0f, tween(200), label = "header")
    val (fontSize, height, start) = when (kind) {
        TitleKind.ROW -> Triple(16f + 6f * lit, 26.dp, Sizes.gutter)
        TitleKind.PICKS -> Triple(15f, 22.dp, Sizes.gutter)
        TitleKind.SECTION -> Triple(17f, 24.dp, 10.dp)
        TitleKind.PAGE -> Triple(26f, 32.dp, Sizes.gutter)
    }
    val tone = lerp(colour.dim(0.7f), colour, lit)
    Row(
        modifier.fillMaxWidth().padding(start = start, end = Sizes.gutter, top = if (kind == TitleKind.SECTION) 8.dp else 0.dp, bottom = if (kind == TitleKind.ROW) 10.dp else 8.dp)
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(16.dp).fillMaxHeight()) { capsule(tone, Offset.Zero, size, left = true, right = false) }
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = Type.rowBig.copy(fontSize = fontSize.sp), color = tone, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(if (kind == TitleKind.PAGE) 8.dp else 3.dp + 3.dp * lit)) {
            drawRect(tone)
            if (lit > 0.5f) {
                val t = time.value % 4200L / 4200f
                val w = 80.dp.toPx()
                val x = -w + (size.width + w) * t
                drawRect(Lcars.Lit, Offset(x.coerceAtLeast(0f), 0f), Size((x + w).coerceAtMost(size.width) - x.coerceAtLeast(0f), size.height), alpha = 0.8f * lit)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(code(text), style = LcarsText.code, color = tone)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.width(10.dp).fillMaxHeight()) { capsule(tone, Offset.Zero, size, left = false, right = true) }
    }
}

/**
 * The spotlight's picture as a viewscreen inside the frame: set to the right, falling away into black for the words
 * and the rows, with faint scan lines across it.
 */
@Composable
internal fun LcarsHeroBackdrop(card: MediaCard?, scrolled: Float) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - scrolled * 1.6f).coerceIn(0f, 1f) }) {
        Crossfade(card?.backdrop, animationSpec = tween(700), label = "viewscreen") { url ->
            if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black, 0.36f to Color.Black.copy(alpha = 0.82f), 0.72f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.2f to Color.Transparent, 0.48f to Color.Transparent, 0.8f to Color.Black)))
        Canvas(Modifier.fillMaxSize()) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) { drawRect(Color.Black, Offset(0f, y), Size(size.width, 1.dp.toPx()), alpha = 0.16f); y += step }
        }
    }
}

/**
 * The words over the banner, as a readout: the source in a lit block with its number, the title in tall capitals
 * (or its logo, recoloured to match), the details in a line divided by small blocks, and the summary.
 */
@Composable
internal fun LcarsHeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier) {
    AnimatedContent(card, modifier, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, contentKey = { it?.key }, label = "readout") { c ->
        Column(Modifier.fillMaxWidth(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            if (c == null) {
                Text(emptyTitle.uppercase(), style = Type.heroTitle, color = Lcars.Orange)
                Text(emptyHint, style = Type.body, color = Lcars.Peach.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            // Where it's from is shown in the frame, under the top arm's end.
            if (c.logo != null) {
                AsyncImage(
                    c.logo, c.title, Modifier.heightIn(max = 72.dp).widthIn(max = 360.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                    colorFilter = ColorFilter.tint(Lcars.Peach),
                )
            } else {
                Text(c.title.uppercase(), style = if (c.title.length > 26) Type.heroTitle.copy(fontSize = 32.sp, lineHeight = 36.sp) else Type.heroTitle,
                    color = Lcars.Peach, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            c.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    meta.split(" · ", " • ").filter { it.isNotBlank() }.forEachIndexed { i, part ->
                        if (i > 0) Box(Modifier.padding(horizontal = 8.dp).size(6.dp).background(Lcars.Orange))
                        Text(part.uppercase(), style = Type.heroMeta, color = if (i == 0) Lcars.Sunflower else Lcars.BlueBell)
                    }
                }
            }
            c.overview?.takeIf { it.isNotBlank() }?.let {
                // It gets what room the rest leaves: a line or two, or none under a tall logo.
                val line = with(LocalDensity.current) { Type.body.lineHeight.toDp() } + 6.dp
                Text(
                    it, style = Type.body, color = Lcars.Peach.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wholeLines(line).padding(top = 6.dp),
                )
            }
        }
    }
}

/** Left out altogether when not even its first line (of height [line]) fits, rather than cut through. */
private fun Modifier.wholeLines(line: Dp) = layout { measurable, constraints ->
    if (constraints.maxHeight < line.roundToPx()) layout(0, 0) {}
    else measurable.measure(constraints).let { p -> layout(p.width, p.height) { p.place(0, 0) } }
}

/**
 * A title's card: its picture squared off in a dark well, an LCARS bracket round it when focused, its badge in a lit
 * block and its progress as a bar of segments.
 */
@Composable
internal fun LcarsCardFace(card: MediaCard, width: Dp, showText: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) {
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "scale")
    Column(modifier.width(width)) {
        Box(Modifier.size(width, width * 9 / 16).lcarsFocus(focused, scale).then(control).clip(RoundedCornerShape(3.dp)).background(Lcars.Well)) {
            if (card.image != null) {
                AsyncImage(card.image, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Placeholder(card.title, Color(Feed.tint(card.title)))
            card.badge?.takeIf { it != "NEW" || focused }?.let { badge ->
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 8.dp)
                        .clip(RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)).background(BadgeColours[badge] ?: Lcars.Orange)
                        .padding(start = 10.dp, end = 6.dp),
                ) { Text(badge, style = Type.badge, color = Color.Black) }
            }
            // In a strip of its own along the foot, clear of any title the artwork carries there.
            card.progress?.let {
                Segments(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(13.dp).background(Color.Black).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        if (showText) {
            Text(card.title.uppercase(), style = Type.cardTitle, color = Lcars.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            card.subtitle?.let { Text(it, style = Type.cardSub, color = Lcars.BlueBell, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp)) }
        }
    }
}

private val BadgeColours = mapOf("RESUME" to Lcars.Sunflower, "WATCH" to Lcars.BlueBell, "NEW" to Lcars.Mars)

/** A card with no picture: its name in a dark well, with a strip of its colour down the side. */
@Composable
private fun Placeholder(title: String, tint: Color) {
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(lerp(Lcars.Well, tint, 0.25f))
        capsule(pick(title), Offset.Zero, Size(10.dp.toPx(), size.height), left = false, right = false)
    }, Alignment.CenterStart) {
        Text(title.uppercase(), style = Type.cardTitle, color = Lcars.Peach, maxLines = 3, modifier = Modifier.padding(start = 20.dp, end = 10.dp))
    }
}

/** Progress as a bar of segments, the way LCARS shows a quantity. */
@Composable
private fun Segments(progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val count = 14
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        val filled = progress.coerceIn(0.02f, 1f) * count
        for (i in 0 until count) {
            val x = i * (w + gap)
            drawRect(Color(0xCC1A1520), Offset(x, 0f), Size(w, size.height))
            val part = (filled - i).coerceIn(0f, 1f)
            if (part > 0f) drawRect(Lcars.Orange, Offset(x, 0f), Size(w * part, size.height))
        }
    }
}

/** A genre as an LCARS button: a block rounded off on the left, its name in black at its foot. */
@Composable
internal fun LcarsTileFace(card: MediaCard, width: Dp, focused: Boolean, control: Modifier) {
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(160), label = "scale")
    val flash by rememberFlash(focused)
    val colour = pick(card.title)
    Box(
        Modifier.size(width, width * 9 / 16).graphicsLayer { scaleX = scale; scaleY = scale }.then(control).drawBehind {
            val fill = if (focused) lerp(colour.dim(0.4f), Lcars.Lit, flash) else colour
            val r = size.height * 0.32f
            drawRoundRect(fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            drawRect(fill, Offset(size.width - r, 0f), Size(r, size.height))
            // A black slice across the top corner, as on the big panel buttons.
            drawRect(Color.Black, Offset(size.width * 0.62f, size.height * 0.28f), Size(size.width * 0.38f, 3.dp.toPx()))
        },
        Alignment.BottomEnd,
    ) {
        Text(card.title.uppercase(), style = Type.rowSmall, color = Color.Black, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.padding(12.dp))
        Text(code(card.key), style = LcarsText.code, color = Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp))
    }
}

/**
 * An app as an LCARS button: a capsule in the colour nearest the app's own, its name in black, and its mark lifted
 * off its icon and set in that colour in a black well at the left end. As a banner, the banner is printed in shades
 * of that colour, labelled underneath.
 */
@Composable
internal fun LcarsAppFace(app: TvApp, banner: Boolean, moving: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) {
    val density = LocalDensity.current
    val markPx = with(density) { 34.dp.roundToPx() }
    val glyph = rememberArtwork(app.pkg, markPx) { glyph(app.pkg, markPx) }
    val mark = remember(glyph) { glyph?.bitmap?.asImageBitmap() }
    val colour = appColour(app.pkg, glyph?.tone)
    val flash by rememberFlash(focused || moving)
    val time = LocalLcarsTime.current
    val scale by animateFloatAsState(if (focused || moving) 1.06f else 1f, tween(160), label = "scale")
    val label = if (moving) "◀  MOVE  ▶" else app.label.uppercase()

    if (banner) {
        val bannerPx = with(density) { Sizes.bannerWidth.roundToPx() }
        val ramp = remember(colour) { intArrayOf(Color.Black.toArgb(), colour.dim(0.32f).toArgb(), colour.toArgb(), lerp(colour, Color.White, 0.55f).toArgb()) }
        val art = rememberArtwork(app.pkg, bannerPx, ramp.contentHashCode()) { tonedBanner(app.pkg, bannerPx, ramp)?.asImageBitmap() }
        Column(modifier.width(Sizes.bannerWidth)) {
            Box(
                Modifier.size(Sizes.bannerWidth, Sizes.bannerWidth * 9 / 16).lcarsFocus(focused || moving, scale, colour).then(control)
                    .clip(RoundedCornerShape(3.dp)).background(Lcars.Well),
                Alignment.Center,
            ) {
                when {
                    art != null -> Image(art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    mark != null -> Image(mark, app.label, Modifier.size(48.dp), colorFilter = ColorFilter.tint(colour))
                }
            }
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth().height(20.dp)
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(if (focused || moving) Lcars.Lit else colour),
                Alignment.CenterEnd,
            ) { Text(label, style = LcarsText.code.copy(fontSize = 12.sp), color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp)) }
        }
        return
    }

    Box(
        modifier.width(Sizes.appWidth).height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(control).drawBehind {
            val fill = when {
                // Pulses while it's being carried along the row.
                moving -> lerp(colour, Lcars.Lit, 0.5f + 0.5f * kotlin.math.sin(time.value / 160.0).toFloat())
                focused -> lerp(colour.dim(0.4f), Lcars.Lit, flash)
                else -> colour
            }
            capsule(fill, Offset.Zero, size)
            drawCircle(Color.Black, radius = 23.dp.toPx(), center = Offset(28.dp.toPx(), size.height / 2))
        },
    ) {
        if (mark != null) Image(
            mark, app.label, Modifier.align(Alignment.CenterStart).padding(start = 11.dp).size(34.dp),
            colorFilter = ColorFilter.tint(if (focused) Lcars.Lit else colour),
        )
        Text(
            label, style = fitWords(label, LcarsText.label.copy(fontSize = 15.sp, lineHeight = 15.sp), Sizes.appWidth - 74.dp), color = Color.Black,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.CenterEnd).padding(start = 58.dp, end = 16.dp),
        )
    }
}

/**
 * [style], made smaller if need be so that [text]'s longest word fits in [room]: a name then wraps between its words
 * and never through one.
 */
@Composable
private fun fitWords(text: String, style: TextStyle, room: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val px = with(LocalDensity.current) { room.toPx() }
    return remember(text, style, px) {
        val widest = text.split(' ').maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
        if (widest <= px) style
        else (style.fontSize.value * px / widest).coerceAtLeast(11f).sp.let { style.copy(fontSize = it, lineHeight = it) }
    }
}

/** One of the Apps tab's shortcuts as an LCARS button, rounded on the left. */
@Composable
internal fun LcarsActionFace(icon: ImageVector, title: String, sub: String, focused: Boolean, control: Modifier) {
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(160), label = "scale")
    val flash by rememberFlash(focused)
    val colour = pick(title, listOf(Lcars.Orange, Lcars.Peach, Lcars.Lilac, Lcars.BlueBell, Lcars.Tangerine))
    Row(
        Modifier.width(174.dp).height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(control).drawBehind {
            capsule(if (focused) lerp(colour.dim(0.4f), Lcars.Lit, flash) else colour, Offset.Zero, size, left = true, right = false)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.Black, modifier = Modifier.padding(start = 16.dp).size(22.dp))
        Column(Modifier.padding(start = 10.dp, end = 10.dp)) {
            Text(title.uppercase(), style = LcarsText.label, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = LcarsText.code.copy(fontSize = 12.sp), color = Color.Black.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Search's box as an LCARS input: a capped bar each end and rails along a black field. */
@Composable
internal fun LcarsSearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit) {
    val flash by rememberFlash(focused)
    val cap = if (focused) lerp(Lcars.Orange.dim(0.4f), Lcars.Lit, flash) else Lcars.Orange
    Row(Modifier.width(400.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp)).background(cap), Alignment.Center) {
            Icon(Icons.Rounded.Search, null, tint = Color.Black, modifier = Modifier.padding(start = 6.dp).size(24.dp))
        }
        Box(
            Modifier.padding(horizontal = 4.dp).weight(1f).fillMaxHeight().drawBehind {
                val rail = 3.dp.toPx()
                drawRect(cap, Offset.Zero, Size(size.width, rail))
                drawRect(cap, Offset(0f, size.height - rail), Size(size.width, rail))
            }.padding(horizontal = 14.dp),
            Alignment.CenterStart,
        ) {
            if (placeholder != null) Text(placeholder.uppercase(), style = Type.rowSmall, color = Lcars.Faint)
            field()
        }
        Box(Modifier.width(18.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)).background(cap))
    }
}

/** A button with an icon as an LCARS capsule, labelled. */
@Composable
internal fun LcarsButtonFace(icon: ImageVector, label: String, focused: Boolean, control: Modifier) {
    val flash by rememberFlash(focused)
    val colour = pick(label, listOf(Lcars.Lilac, Lcars.BlueBell, Lcars.Peach))
    Row(
        Modifier.height(52.dp).then(control).drawBehind { capsule(if (focused) lerp(colour.dim(0.4f), Lcars.Lit, flash) else colour, Offset.Zero, size) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), style = LcarsText.label, color = Color.Black, maxLines = 1)
    }
}
