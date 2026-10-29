package com.vwillcox.mytv.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.vwillcox.mytv.data.Look
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.data.ThemeId
import com.vwillcox.mytv.data.TvApp
import com.vwillcox.mytv.ui.lcars.LcarsSkin

/**
 * A look for the whole home screen: its colours, type and sizes, and how each part of the screen is drawn. The
 * screens keep the behaviour (focus, keys, what choosing does) and hand a skin only what it needs to draw: whether
 * the part has focus, and a `control` modifier that makes it focusable and choosable, which the skin puts on
 * whichever of its shapes should take focus.
 */
@Stable
interface Skin {
    val id: ThemeId
    val type: TypeScale
    val metrics: Metrics
    fun tones(look: Look): Tones

    /** Round everything, the panels included: for what the skin keeps running, like its sounds. */
    @Composable fun Provide(look: Look, content: @Composable () -> Unit)

    /** The screen's frame: the background, the navigation, and the screen on show inside them. */
    @Composable fun Frame(frame: HomeFrame)

    /** The owner's choice of background (dark, a colour, a photo, or the focused title's artwork). */
    @Composable fun Background(look: Look, art: String?, modifier: Modifier)

    /** The spotlight's picture behind the top of a tab, fading as the page scrolls by [scrolled] (0 to 1). */
    @Composable fun HeroBackdrop(card: MediaCard?, scrolled: Float)

    /** The words over the banner, or [emptyTitle] and [emptyHint] when there's nothing in it. */
    @Composable fun HeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier)

    @Composable fun Title(text: String, kind: TitleKind, focused: Boolean, modifier: Modifier)

    /** How a focused card or tile is marked, given how far it has grown by. */
    @Composable fun FocusRing(modifier: Modifier, focused: Boolean, scale: Float, corner: Dp, round: Boolean): Modifier

    @Composable fun CardFace(card: MediaCard, width: Dp, showText: Boolean, focused: Boolean, control: Modifier, modifier: Modifier)

    /** A genre: a coloured tile with its name. */
    @Composable fun TileFace(card: MediaCard, width: Dp, focused: Boolean, control: Modifier)

    /** An app as an icon or its banner. While [moving], arrows shift it along its row. */
    @Composable fun AppFace(app: TvApp, banner: Boolean, moving: Boolean, focused: Boolean, control: Modifier, modifier: Modifier)

    /** One of the Apps tab's shortcuts: the Play Store, installing, settings. */
    @Composable fun ActionFace(icon: ImageVector, title: String, sub: String, focused: Boolean, control: Modifier)

    /** Search's box round its text [field], with [placeholder] while nothing's typed. */
    @Composable fun SearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit)

    /** A button with only an icon, like search's microphone. */
    @Composable fun ButtonFace(icon: ImageVector, label: String, focused: Boolean, control: Modifier)

    /** A side panel: its [group] modifier goes round all of it and its [list] modifier on the list of [content]. */
    @Composable fun PanelFace(title: String, subtitle: String?, group: Modifier, list: Modifier, state: LazyListState, content: LazyListScope.() -> Unit)

    @Composable fun ItemFace(
        title: String, sub: String?, icon: ImageVector?, trailing: (@Composable (focused: Boolean) -> Unit)?, chevron: Boolean,
        focused: Boolean, control: Modifier,
    )

    @Composable fun SwitchFace(on: Boolean, focused: Boolean)
    @Composable fun ChoiceFace(selected: Boolean, focused: Boolean)
    @Composable fun HeadingFace(text: String)
    @Composable fun NoteFace(text: String)
}

/** The kinds of heading a screen has, which a skin may draw quite differently. */
enum class TitleKind {
    /** A row's title, larger while the row has focus. */
    ROW,
    /** "Top picks for you", over the cards under the banner. */
    PICKS,
    /** A heading over part of a grid, like All apps. */
    SECTION,
    /** The title of a screen of its own, like a genre. */
    PAGE,
}

/** What a skin's [Skin.Frame] lays out. */
class HomeFrame(
    val ui: HomeUi,
    val look: Look,
    val avatar: String?,
    val userName: String?,
    /** A tab chosen, or search (null). */
    val onTab: (Tab?) -> Unit,
    /** Whether focus is in the navigation, which Back uses. */
    val onNavFocus: (Boolean) -> Unit,
    /** The screen on show. Google TV's glass blurs whatever the modifier it's given is put on. */
    val content: @Composable (Modifier) -> Unit,
)

/** Sounds the screen makes, in skins that have any. */
interface UiSounds {
    /** OK on something. */
    fun select() {}
    /** Focus arriving on a tab. */
    fun tab() {}
    /** Back out of a panel. */
    fun close() {}
    /** The screen coming up. */
    fun boot() {}
}

object Silent : UiSounds

val LocalSkin = staticCompositionLocalOf<Skin> { GoogleTvSkin }
val LocalSounds = staticCompositionLocalOf<UiSounds> { Silent }

fun skinFor(id: ThemeId): Skin = when (id) {
    ThemeId.GOOGLE_TV -> GoogleTvSkin
    ThemeId.LCARS -> LcarsSkin
}

/** Google TV's own look. Each part is drawn where its screen is written; this only gathers them. */
object GoogleTvSkin : Skin {
    override val id = ThemeId.GOOGLE_TV
    override val type = GoogleType
    override val metrics = GoogleMetrics
    override fun tones(look: Look) = Tones(Color(look.accent))

    @Composable override fun Provide(look: Look, content: @Composable () -> Unit) = content()
    @Composable override fun Frame(frame: HomeFrame) = GoogleFrame(frame)
    @Composable override fun Background(look: Look, art: String?, modifier: Modifier) = PlainBackground(look, art, modifier)
    @Composable override fun HeroBackdrop(card: MediaCard?, scrolled: Float) = GoogleHeroBackdrop(card, scrolled)
    @Composable override fun HeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier) =
        GoogleHeroInfo(card, emptyTitle, emptyHint, modifier)
    @Composable override fun Title(text: String, kind: TitleKind, focused: Boolean, modifier: Modifier) = GoogleTitle(text, kind, focused, modifier)
    @Composable override fun FocusRing(modifier: Modifier, focused: Boolean, scale: Float, corner: Dp, round: Boolean) =
        modifier.whiteRing(focused, scale, corner, round)
    @Composable override fun CardFace(card: MediaCard, width: Dp, showText: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) =
        GoogleCardFace(card, width, showText, focused, control, modifier)
    @Composable override fun TileFace(card: MediaCard, width: Dp, focused: Boolean, control: Modifier) = GoogleTileFace(card, width, focused, control)
    @Composable override fun AppFace(app: TvApp, banner: Boolean, moving: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) =
        GoogleAppFace(app, banner, moving, focused, control, modifier)
    @Composable override fun ActionFace(icon: ImageVector, title: String, sub: String, focused: Boolean, control: Modifier) =
        GoogleActionFace(icon, title, sub, focused, control)
    @Composable override fun SearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit) = GoogleSearchFace(focused, placeholder, field)
    @Composable override fun ButtonFace(icon: ImageVector, label: String, focused: Boolean, control: Modifier) = GoogleButtonFace(icon, label, focused, control)
    @Composable override fun PanelFace(title: String, subtitle: String?, group: Modifier, list: Modifier, state: LazyListState, content: LazyListScope.() -> Unit) =
        GooglePanelFace(title, subtitle, group, list, state, content)
    @Composable override fun ItemFace(
        title: String, sub: String?, icon: ImageVector?, trailing: (@Composable (focused: Boolean) -> Unit)?, chevron: Boolean,
        focused: Boolean, control: Modifier,
    ) = GoogleItemFace(title, sub, icon, trailing, chevron, focused, control)
    @Composable override fun SwitchFace(on: Boolean, focused: Boolean) = GoogleSwitch(on, focused)
    @Composable override fun ChoiceFace(selected: Boolean, focused: Boolean) = GoogleChoice(selected, focused)
    @Composable override fun HeadingFace(text: String) = GoogleHeading(text)
    @Composable override fun NoteFace(text: String) = GoogleNote(text)
}
