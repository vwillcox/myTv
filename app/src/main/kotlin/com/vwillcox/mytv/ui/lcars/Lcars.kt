package com.vwillcox.mytv.ui.lcars

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.ThemeId
import com.vwillcox.mytv.data.TvApp
import com.vwillcox.mytv.ui.HomeFrame
import com.vwillcox.mytv.ui.Metrics
import com.vwillcox.mytv.ui.PlainBackground
import com.vwillcox.mytv.ui.Skin
import com.vwillcox.mytv.ui.TitleKind
import com.vwillcox.mytv.ui.Tones
import com.vwillcox.mytv.ui.TypeScale
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** The colours of a Starfleet LCARS panel of the Enterprise-D's day. */
internal object Lcars {
    val Orange = Color(0xFFFF9900)
    val Sunflower = Color(0xFFFFCC66)
    val Peach = Color(0xFFFFCC99)
    val Tangerine = Color(0xFFFF9966)
    val Lilac = Color(0xFFCC99CC)
    val Violet = Color(0xFF9977CC)
    val Periwinkle = Color(0xFF9999FF)
    val BlueBell = Color(0xFF9999CC)
    val Ice = Color(0xFF99CCFF)
    val Mars = Color(0xFFDD6655)
    /** A button lit by focus. */
    val Lit = Color(0xFFFFEEDD)
    /** The dark inside a frame, a shade off the black round it. */
    val Well = Color(0xFF0D0B12)
    val Faint = Color(0xFF8E7F9C)

    /** What buttons and blocks are painted in, for picking one by name. */
    val Panels = listOf(Orange, Peach, Lilac, BlueBell, Tangerine, Sunflower, Periwinkle, Violet)

    /** Hues the panel colours sit at, for matching an app's own colour to the nearest. */
    val Hues = listOf(4f to Mars, 20f to Tangerine, 36f to Orange, 46f to Sunflower, 205f to Ice, 238f to Periwinkle, 268f to Violet, 305f to Lilac)
}

private fun antonio(weight: Int) = Font(R.font.antonio, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Antonio: tall and narrow, in the manner of the compressed face LCARS panels are lettered in. */
internal val Antonio = FontFamily(antonio(300), antonio(400), antonio(500), antonio(600), antonio(700))

private fun antonio(size: Float, weight: Int, spacing: Float = 0.3f, line: Float? = null) = TextStyle(
    fontFamily = Antonio, fontSize = size.sp, fontWeight = FontWeight(weight), letterSpacing = spacing.sp,
    lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
)

internal val LcarsType = TypeScale(
    tab = antonio(17f, 600, 0.6f),
    heroTitle = antonio(40f, 600, 0.4f, 44f),
    heroMeta = antonio(15f, 500, 0.6f),
    body = antonio(15f, 400, 0.3f, 20f),
    rowSmall = antonio(17f, 500, 0.5f),
    rowBig = antonio(24f, 600, 0.6f),
    cardTitle = antonio(14f, 500, 0.5f),
    cardSub = antonio(13f, 400, 0.3f),
    badge = antonio(12f, 700, 1f),
    clock = antonio(44f, 500, 1f),
    panelTitle = antonio(26f, 600, 0.8f),
    item = antonio(17f, 500, 0.6f),
    itemSub = antonio(14f, 400, 0.3f, 18f),
)

/** Small lettering: the numbers on blocks and the readouts. */
internal object LcarsText {
    val label = antonio(15f, 600, 0.8f)
    val code = antonio(11f, 500, 0.8f)
    val data = antonio(13f, 500, 0.5f)
    val readout = antonio(13f, 500, 1.2f)
}

internal val LcarsMetrics = Metrics(
    gutter = 20.dp,
    pickWidth = 153.dp,
    wideWidth = 196.dp,
    appCircle = 46.dp,
    bannerWidth = 160.dp,
    cardGap = 20.dp,
    corner = 3.dp,
    top = 6.dp,
    bannerPeek = 64.dp,
    appWidth = 152.dp,
    appInset = 0.dp,
    appGap = 12.dp,
    appGridGap = 14.dp,
)

/** [this] colour at [f] of its brightness: an unlit panel. */
internal fun Color.dim(f: Float = 0.45f) = lerp(Color.Black, this, f)

/** One of the panel colours for [key], the same one every time. */
internal fun pick(key: String, from: List<Color> = Lcars.Panels): Color = from[(key.hashCode() and 0x7FFFFFFF) % from.size]

/** The panel colour nearest an app's own [tone], so Netflix stays red and Prime blue; a grey icon gets one by name. */
internal fun appColour(pkg: String, tone: Int?): Color {
    if (tone == null) return pick(pkg, listOf(Lcars.Peach, Lcars.BlueBell, Lcars.Orange, Lcars.Lilac, Lcars.Tangerine))
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(tone, it) }
    return Lcars.Hues.minBy { (hue, _) -> abs(hue - hsv[0]).let { minOf(it, 360 - it) } }.second
}

/** A block's number, like "47-0331": made from [key] so it's the same every time. */
internal fun code(key: String): String {
    val h = key.hashCode() and 0x7FFFFFFF
    return "%02d-%04d".format(Locale.US, h % 97, (h / 97) % 10_000)
}

/**
 * Today as a stardate in the style of the Enterprise-D's: 1000 to a year, with this year standing in for 2364, when
 * that ship's stardates began at 41000. 2026 reads 41xxx, 2027 42xxx, and so on.
 */
internal fun stardate(now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val year = cal.get(Calendar.YEAR)
    val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1) }.timeInMillis
    val end = Calendar.getInstance().apply { clear(); set(year + 1, Calendar.JANUARY, 1) }.timeInMillis
    val through = (now - start).toDouble() / (end - start)
    return "%.1f".format(Locale.US, 41_000 + (year - 2026) * 1000 + through * 1000)
}

/** The LCARS skin: the home screen as a Starfleet computer panel. */
object LcarsSkin : Skin {
    override val id = ThemeId.LCARS
    override val type = LcarsType
    override val metrics = LcarsMetrics
    override fun tones(look: Look) = Tones(
        accent = Lcars.Orange, onAccent = Color.Black, bg = Color.Black, surface = Lcars.Well, pill = Lcars.Well,
        text = Lcars.Peach, dim = Lcars.Lilac, faint = Lcars.Faint, focus = Lcars.Lit,
    )

    @Composable override fun Provide(look: Look, content: @Composable () -> Unit) = LcarsProvide(look, content)
    @Composable override fun Frame(frame: HomeFrame) = LcarsFrame(frame)
    @Composable override fun Background(look: Look, art: String?, modifier: Modifier) = PlainBackground(look, art, modifier)
    @Composable override fun HeroBackdrop(card: MediaCard?, scrolled: Float) = LcarsHeroBackdrop(card, scrolled)
    @Composable override fun HeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier) =
        LcarsHeroInfo(card, emptyTitle, emptyHint, modifier)
    @Composable override fun Title(text: String, kind: TitleKind, focused: Boolean, modifier: Modifier) = LcarsTitle(text, kind, focused, modifier)
    @Composable override fun FocusRing(modifier: Modifier, focused: Boolean, scale: Float, corner: Dp, round: Boolean) =
        modifier.lcarsFocus(focused, scale)
    @Composable override fun CardFace(card: MediaCard, width: Dp, showText: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) =
        LcarsCardFace(card, width, showText, focused, control, modifier)
    @Composable override fun TileFace(card: MediaCard, width: Dp, focused: Boolean, control: Modifier) = LcarsTileFace(card, width, focused, control)
    @Composable override fun AppFace(app: TvApp, banner: Boolean, moving: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) =
        LcarsAppFace(app, banner, moving, focused, control, modifier)
    @Composable override fun ActionFace(icon: ImageVector, title: String, sub: String, focused: Boolean, control: Modifier) =
        LcarsActionFace(icon, title, sub, focused, control)
    @Composable override fun SearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit) = LcarsSearchFace(focused, placeholder, field)
    @Composable override fun ButtonFace(icon: ImageVector, label: String, focused: Boolean, control: Modifier) = LcarsButtonFace(icon, label, focused, control)
    @Composable override fun PanelFace(title: String, subtitle: String?, group: Modifier, list: Modifier, state: LazyListState, content: LazyListScope.() -> Unit) =
        LcarsPanelFace(title, subtitle, group, list, state, content)
    @Composable override fun ItemFace(
        title: String, sub: String?, icon: ImageVector?, trailing: (@Composable (focused: Boolean) -> Unit)?, chevron: Boolean,
        focused: Boolean, control: Modifier,
    ) = LcarsItemFace(title, sub, icon, trailing, chevron, focused, control)
    @Composable override fun SwitchFace(on: Boolean, focused: Boolean) = LcarsSwitch(on, focused)
    @Composable override fun ChoiceFace(selected: Boolean, focused: Boolean) = LcarsChoice(selected, focused)
    @Composable override fun HeadingFace(text: String) = LcarsHeading(text)
    @Composable override fun NoteFace(text: String) = LcarsNote(text)
}
