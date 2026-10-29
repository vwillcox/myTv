package com.vwillcox.mytv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwillcox.mytv.R

/** Google Sans Flex, the open-source cut of the face Google TV is set in. One variable file gives every weight. */
private fun sans(weight: Int) = Font(
    R.font.google_sans_flex, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", 0f)),
)

val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700))

/** The home screen's colours. [accent] is the owner's choice; the rest follow Google TV's dark theme. */
@Immutable
data class Tones(
    val accent: Color,
    val onAccent: Color = Color(0xFF0B1D3A),
    val bg: Color = Color(0xFF111215),
    val surface: Color = Color(0xFF1F2024),
    val pill: Color = Color(0xE62A2B2F),
    val text: Color = Color(0xFFE8EAED),
    val dim: Color = Color(0xFFB4B8BE),
    val faint: Color = Color(0xFF80868B),
    val focus: Color = Color.White,
)

val LocalTones = staticCompositionLocalOf { Tones(Color(0xFFD3E3FD)) }

/** Sizes measured off the Google TV home screen at 1080p (960 × 540 dp). */
object Size {
    val gutter = 58.dp
    val pickWidth = 153.dp
    val wideWidth = 196.dp
    val appCircle = 74.dp
    val bannerWidth = 160.dp
    val cardGap = 20.dp
    val corner = 12.dp
}

object Type {
    val tab = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight(500))
    val heroTitle = TextStyle(fontFamily = Sans, fontSize = 34.sp, fontWeight = FontWeight(400), lineHeight = 40.sp)
    val heroMeta = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight(500))
    val body = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(400), lineHeight = 19.sp)
    val rowSmall = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(400))
    val rowBig = TextStyle(fontFamily = Sans, fontSize = 30.sp, fontWeight = FontWeight(400))
    val cardTitle = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(500))
    val cardSub = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight(400))
    val badge = TextStyle(fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight(700), letterSpacing = 0.8.sp)
    val clock = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight(400))
    val panelTitle = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight(400))
    val item = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(500))
    val itemSub = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight(400))
}

@Composable
fun MyTvTheme(accent: Color, content: @Composable () -> Unit) {
    val tones = Tones(accent)
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Sans), bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
        bodySmall = base.bodySmall.copy(fontFamily = Sans), titleLarge = base.titleLarge.copy(fontFamily = Sans),
        titleMedium = base.titleMedium.copy(fontFamily = Sans), labelLarge = base.labelLarge.copy(fontFamily = Sans),
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = tones.bg, surface = tones.surface), typography = typography) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTones provides tones, content = content)
    }
}
