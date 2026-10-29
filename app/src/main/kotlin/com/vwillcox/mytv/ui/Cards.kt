package com.vwillcox.mytv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vwillcox.mytv.app
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.TvApp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Grows the focused element and rings it, a little way out, in white: Google TV's focus. */
fun Modifier.focusRing(focused: Boolean, scale: Float, corner: Dp, round: Boolean = false): Modifier = graphicsLayer {
    scaleX = scale; scaleY = scale
}.drawWithContent {
    drawContent()
    if (focused) {
        val gap = 4.dp.toPx(); val stroke = 2.5.dp.toPx()
        val inset = -(gap + stroke / 2)
        val r = if (round) size.minDimension / 2 + gap + stroke / 2 else corner.toPx() + gap
        drawRoundRect(
            Color.White, topLeft = Offset(inset, inset), size = GSize(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(r, r), style = Stroke(stroke),
        )
    }
}

@Composable
fun MediaCardView(
    card: MediaCard,
    width: Dp,
    showText: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "scale")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val tones = LocalTones.current
    val shape = RoundedCornerShape(Size.corner)
    Column(modifier.width(width)) {
        Box(
            Modifier.size(width, width * 9 / 16)
                .focusRing(focused, scale, Size.corner)
                .tvClickable(interaction, onLongClick, onClick)
                .clip(shape)
                .background(tones.surface),
        ) {
            if (card.image != null) {
                AsyncImage(card.image, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(Feed.tint(card.title)), tones.surface))), Alignment.Center) {
                    Text(card.title, style = Type.cardTitle, color = tones.text, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(12.dp))
                }
            }
            val progress = card.progress
            if (card.badge != null && (card.badge != "NEW" || focused)) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (progress != null) 18.dp else 10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xB3202124)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text(card.badge, style = Type.badge, color = Color.White) }
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 9.dp).fillMaxWidth().height(3.dp)
                    .clip(CircleShape).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(3.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        if (showText) {
            Text(
                card.title, style = Type.cardTitle, color = tones.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            card.subtitle?.let { Text(it, style = Type.cardSub, color = tones.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** A genre: a coloured tile with its name. */
@Composable
fun TileView(card: MediaCard, width: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "scale")
    val tones = LocalTones.current
    Box(
        Modifier.size(width, width * 9 / 16).focusRing(focused, scale, Size.corner).tvClickable(interaction, onClick = onClick)
            .clip(RoundedCornerShape(Size.corner))
            .background(Brush.linearGradient(listOf(Color(Feed.tint(card.title)), Color(Feed.tint(card.title)).copy(alpha = 0.55f)))),
        Alignment.BottomStart,
    ) { Text(card.title, style = Type.rowSmall, color = tones.text, modifier = Modifier.padding(14.dp)) }
}

@Composable
private fun rememberIcon(app: TvApp, px: Int, banner: Boolean): ImageBitmap? {
    val repo = LocalContext.current.app.apps
    val changed by repo.changed.collectAsState()
    return produceState<ImageBitmap?>(null, app.pkg, px, banner, changed) {
        value = withContext(Dispatchers.IO) { (if (banner) repo.banner(app.pkg, px) else repo.roundIcon(app.pkg, px))?.asImageBitmap() }
    }.value
}

/**
 * One app in Your apps or the app grid: a round icon in Google TV's style, or the app's TV banner. [moving] marks
 * the app being moved, which arrows then shift along the row.
 */
@Composable
fun AppTile(
    app: TvApp,
    banner: Boolean,
    moving: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(160), label = "scale")
    LaunchedEffect(focused) { if (focused) onFocused() }
    val tones = LocalTones.current
    val density = LocalDensity.current
    val width = if (banner) Size.bannerWidth else Size.appCircle
    val height = if (banner) Size.bannerWidth * 9 / 16 else Size.appCircle
    val bannerArt = if (banner) rememberIcon(app, with(density) { width.roundToPx() }, banner = true) else null
    // An app without a banner shows its icon on a card instead.
    val icon = if (!banner || bannerArt == null) rememberIcon(app, with(density) { height.roundToPx() }, banner = false) else null
    Column(modifier.width(width + if (banner) 0.dp else 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width, height)
                .focusRing(focused || moving, if (moving) 1.14f else scale, Size.corner, round = !banner)
                .tvClickable(interaction, onLongClick, onClick)
                .clip(if (banner) RoundedCornerShape(Size.corner) else CircleShape)
                .background(tones.surface),
            Alignment.Center,
        ) {
            when {
                bannerArt != null -> Image(bannerArt, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                icon != null && banner -> Image(icon, app.label, Modifier.size(height * 0.7f))
                icon != null -> Image(icon, app.label, Modifier.fillMaxSize())
            }
            if (moving) Box(Modifier.fillMaxSize().background(Color(0x99000000)), Alignment.Center) {
                Icon(Icons.Rounded.SwapHoriz, "Moving", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            app.label, style = Type.cardSub, color = if (focused || moving) tones.text else Color.Transparent,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
    }
}
