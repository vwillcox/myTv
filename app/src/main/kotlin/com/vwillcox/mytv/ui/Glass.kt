package com.vwillcox.mytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** The page behind the top bar, which its glass blurs. Set by the home screen round everything the bar floats over. */
val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

/**
 * Frosted glass: whatever is behind, blurred and lightly darkened so white words stay readable over bright
 * artwork, with a sheen that is brighter at the top and a hairline edge lit from above. Without a [haze] (or
 * before Android 12, which has no blur to give) it is a plain translucent pill.
 */
fun Modifier.glass(haze: HazeState?, shape: Shape = CircleShape): Modifier {
    val clipped = clip(shape)
    val frosted = if (haze == null) clipped.background(Color(0x99202124)) else clipped.hazeBlur(HazeInput.Sources(haze), GlassStyle)
    return frosted
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.5f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.03f)))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f))), shape)
}

private val GlassStyle = HazeBlurStyle {
    blurRadius(26.dp)
    backgroundColor(Color(0xFF111215))
    colorEffects(listOf(HazeColorEffect.tint(Color(0xFF16171B).copy(alpha = 0.38f))))
    noiseFactor(0.05f)
    fallbackColorEffect(HazeColorEffect.tint(Color(0xB3202124)))
}
