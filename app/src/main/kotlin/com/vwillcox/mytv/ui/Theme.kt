package com.vwillcox.mytv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwillcox.mytv.R
import com.vwillcox.mytv.data.Look

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

/** The text styles a skin sets the screens in. */
@Immutable
data class TypeScale(
    val tab: TextStyle,
    val heroTitle: TextStyle,
    val heroMeta: TextStyle,
    val body: TextStyle,
    val rowSmall: TextStyle,
    val rowBig: TextStyle,
    val cardTitle: TextStyle,
    val cardSub: TextStyle,
    val badge: TextStyle,
    val clock: TextStyle,
    val panelTitle: TextStyle,
    val item: TextStyle,
    val itemSub: TextStyle,
)

val GoogleType = TypeScale(
    tab = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight(500)),
    heroTitle = TextStyle(fontFamily = Sans, fontSize = 34.sp, fontWeight = FontWeight(400), lineHeight = 40.sp),
    heroMeta = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight(500)),
    body = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(400), lineHeight = 19.sp),
    rowSmall = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(400)),
    rowBig = TextStyle(fontFamily = Sans, fontSize = 30.sp, fontWeight = FontWeight(400)),
    cardTitle = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(500)),
    cardSub = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight(400)),
    badge = TextStyle(fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight(700), letterSpacing = 0.8.sp),
    clock = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight(400)),
    panelTitle = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight(400)),
    item = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(500)),
    itemSub = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight(400)),
)

/** The sizes a skin lays the screens out with. */
@Immutable
data class Metrics(
    val gutter: Dp,
    val pickWidth: Dp,
    val wideWidth: Dp,
    val appCircle: Dp,
    val bannerWidth: Dp,
    val cardGap: Dp,
    val corner: Dp,
    /** Room at the top of each screen for the skin's navigation. */
    val top: Dp,
    /** How much of the first row shows below the banner. */
    val bannerPeek: Dp,
    /** An app's width in a row or grid, from the edge of its label's room to the other. */
    val appWidth: Dp,
    /** How far that reaches out past the app's artwork on each side, which rows tuck into the gutter. */
    val appInset: Dp,
    val appGap: Dp,
    val appGridGap: Dp,
)

/** Sizes measured off the Google TV home screen at 1080p (960 × 540 dp). */
val GoogleMetrics = Metrics(
    gutter = 58.dp,
    pickWidth = 153.dp,
    wideWidth = 196.dp,
    appCircle = 74.dp,
    bannerWidth = 160.dp,
    cardGap = 20.dp,
    corner = 12.dp,
    top = 96.dp,
    bannerPeek = 108.dp,
    appWidth = 100.dp,
    appInset = 13.dp,
    appGap = 2.dp,
    appGridGap = 6.dp,
)

val LocalTypeScale = staticCompositionLocalOf { GoogleType }
val LocalMetrics = staticCompositionLocalOf { GoogleMetrics }

/** The skin's sizes. */
object Size {
    val gutter: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.gutter
    val pickWidth: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.pickWidth
    val wideWidth: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.wideWidth
    val appCircle: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.appCircle
    val bannerWidth: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.bannerWidth
    val cardGap: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.cardGap
    val corner: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.corner
    val bannerPeek: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.bannerPeek
    val appWidth: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.appWidth
    val appInset: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.appInset
    val appGap: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.appGap
    val appGridGap: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.appGridGap
}

/** The skin's text styles. */
object Type {
    val tab: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.tab
    val heroTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.heroTitle
    val heroMeta: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.heroMeta
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.body
    val rowSmall: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.rowSmall
    val rowBig: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.rowBig
    val cardTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.cardTitle
    val cardSub: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.cardSub
    val badge: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.badge
    val clock: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.clock
    val panelTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.panelTitle
    val item: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.item
    val itemSub: TextStyle @Composable @ReadOnlyComposable get() = LocalTypeScale.current.itemSub
}

/** The room each screen leaves at its top for the skin's navigation. */
val TopBarHeight: Dp @Composable @ReadOnlyComposable get() = LocalMetrics.current.top

@Composable
fun MyTvTheme(look: Look, content: @Composable () -> Unit) {
    val skin = skinFor(look.theme)
    val tones = skin.tones(look)
    val type = skin.type
    val base = Typography()
    val face = type.body.fontFamily ?: Sans
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = face), bodyMedium = base.bodyMedium.copy(fontFamily = face),
        bodySmall = base.bodySmall.copy(fontFamily = face), titleLarge = base.titleLarge.copy(fontFamily = face),
        titleMedium = base.titleMedium.copy(fontFamily = face), labelLarge = base.labelLarge.copy(fontFamily = face),
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = tones.accent, background = tones.bg, surface = tones.surface), typography = typography) {
        CompositionLocalProvider(
            LocalSkin provides skin, LocalTones provides tones, LocalTypeScale provides type, LocalMetrics provides skin.metrics,
        ) { skin.Provide(look, content) }
    }
}
