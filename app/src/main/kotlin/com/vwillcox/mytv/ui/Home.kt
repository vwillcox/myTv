package com.vwillcox.mytv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vwillcox.mytv.data.BackgroundMode
import com.vwillcox.mytv.data.Look
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.Open
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.data.TvApp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A side panel on the stack: settings pages and the menus held-down OK opens. */
sealed interface Panel {
    data class Page(val id: String) : Panel
    data class CardMenu(val card: MediaCard) : Panel
    data class AppMenu(val app: TvApp) : Panel
}

/** Everything the home screen remembers between frames: which tab, what the banner shows, what's open over it. */
class HomeUi {
    var tab by mutableStateOf(Tab.HOME)
    var searching by mutableStateOf(false)
    var spotlight by mutableStateOf<MediaCard?>(null)
    /** True while focus is on a Top picks card, which stops the banner moving on by itself. */
    var pickFocused by mutableStateOf(false)
    /** The backdrop of whatever has focus, for the "artwork" background. */
    var focusedArt by mutableStateOf<String?>(null)
    /** How far the page has scrolled past its top, 0 to 1, which fades the banner and slides the top bar away. */
    var scrolled by mutableStateOf(0f)
    val panels = mutableStateListOf<Panel>()
    var grid by mutableStateOf<Open.Browse?>(null)
    var moving by mutableStateOf<String?>(null)
    /** Bumped by the Home button: back to the top of Home. */
    var resets by mutableIntStateOf(0)

    val main = FocusRequester()
    val tabs = (listOf("search") + Tab.entries.map { it.name }).associateWith { FocusRequester() }
    val selectedTab get() = tabs.getValue(if (searching) "search" else tab.name)
    /** The first row of the screen on show, which focus enters when it comes down from the top bar. */
    val entry = FocusRequester()

    fun open(panel: Panel) { panels.add(panel) }
    fun closePanel() { if (panels.isNotEmpty()) panels.removeAt(panels.lastIndex) }
}

/**
 * Google TV's frame: the background, the screen on show over it, and the bar of tabs floating across the top, whose
 * glass blurs the other two.
 */
@Composable
internal fun GoogleFrame(f: HomeFrame) {
    val ui = f.ui
    val glass = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        BackgroundLayer(f.look, ui.focusedArt, Modifier.hazeSource(glass, zIndex = 0f))
        Box(Modifier.fillMaxSize().focusRequester(ui.main).focusRestorer().focusGroup()) {
            f.content(Modifier.hazeSource(glass, zIndex = 1f))
            if (ui.grid == null) {
                Box(Modifier.focusProperties { down = ui.entry }.onFocusChanged { f.onNavFocus(it.hasFocus) }) {
                    CompositionLocalProvider(LocalGlass provides glass) { TopBar(ui, f.avatar, f.userName, f.look, f.onTab) }
                }
            }
        }
    }
}

/** The bar across the top: who's watching, the tabs and the settings, each group a pill of frosted glass. */
@Composable
fun TopBar(ui: HomeUi, avatar: String?, userName: String?, look: Look, onTab: (Tab?) -> Unit) {
    val tones = LocalTones.current
    val glass = LocalGlass.current
    val hidden by animateFloatAsState(if (ui.scrolled > 0.02f) 1f else 0f, tween(220), label = "bar")
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(start = Size.gutter, end = Size.gutter, top = 20.dp)
            .graphicsLayer { alpha = 1f - hidden; translationY = -hidden * 60.dp.toPx() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatar, userName) { ui.open(Panel.Page("account")) }
        Spacer(Modifier.width(14.dp))
        Row(Modifier.glass(glass).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TabPill("Search", Icons.Rounded.Search, ui.searching, ui.tabs.getValue("search")) { onTab(null) }
            for (t in Tab.entries) TabPill(t.label, null, !ui.searching && ui.tab == t, ui.tabs.getValue(t.name)) { onTab(t) }
        }
        Spacer(Modifier.width(14.dp))
        Row(Modifier.glass(glass).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconPill(Icons.Rounded.Wallpaper, "Screensaver and background") { ui.open(Panel.Page("look")) }
            IconPill(Icons.Rounded.Settings, "Settings") { ui.open(Panel.Page("root")) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.glass(glass).heightIn(min = 50.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Clock(look.clock24)
            if (look.brand.isNotBlank()) {
                Text("  |  ", style = Type.clock, color = tones.faint)
                Text(look.brand, style = Type.clock.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)), color = tones.dim)
            }
        }
    }
}

@Composable
private fun Clock(h24: Boolean) {
    val tones = LocalTones.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000 - value % 60_000) }
    }
    val text = remember(now, h24) { SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    Text(text, style = Type.clock, color = tones.text)
}

@Composable
private fun Avatar(url: String?, name: String?, onClick: () -> Unit) {
    val tones = LocalTones.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "avatar")
    Box(
        Modifier.size(44.dp).focusRing(focused, scale, 22.dp, round = true).tvClickable(interaction, onClick = onClick)
            .glass(LocalGlass.current),
        Alignment.Center,
    ) {
        Text(name?.take(1)?.uppercase() ?: "•", style = Type.tab, color = tones.text)
        if (url != null) AsyncImage(url, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun TabPill(label: String, icon: ImageVector?, selected: Boolean, requester: FocusRequester, onFocus: () -> Unit) {
    val tones = LocalTones.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        // Google TV switches tab as soon as one is focused; the pause lets focus cross tabs without loading each.
        if (focused) { delay(160); onFocus() }
    }
    // Focused is bright white, the open tab is the highlight colour, the rest is the glass showing through.
    val bg by animateColorAsState(
        when {
            focused -> Color.White
            selected -> tones.accent
            else -> Color.White.copy(alpha = 0f)
        }, tween(180), label = "tab",
    )
    val fg by animateColorAsState(if (focused || selected) tones.onAccent else tones.text, tween(180), label = "tabText")
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "tabScale")
    Row(
        Modifier.focusRequester(requester).tvClickable(interaction) { onFocus() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = bg.alpha * 0.82f))))
            .then(if (focused || selected) Modifier.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f))), CircleShape) else Modifier)
            .heightIn(min = 40.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = Type.tab, color = fg)
    }
}

@Composable
private fun IconPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val tones = LocalTones.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0f), tween(180), label = "icon")
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "iconScale")
    Box(
        Modifier.size(40.dp).tvClickable(interaction, onClick = onClick).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = bg.alpha * 0.82f)))),
        Alignment.Center,
    ) { Icon(icon, label, tint = if (focused) tones.onAccent else tones.text, modifier = Modifier.size(20.dp)) }
}

/** The page behind everything: the owner's choice of dark, a colour, a photo, or the focused title's artwork. */
@Composable
fun BackgroundLayer(look: Look, art: String?, modifier: Modifier = Modifier) = LocalSkin.current.Background(look, art, modifier)

/** The owner's background as it is, on the skin's own darkness. */
@Composable
internal fun PlainBackground(look: Look, art: String?, modifier: Modifier) {
    val tones = LocalTones.current
    Box(modifier.fillMaxSize().background(tones.bg)) {
        when (look.background) {
            BackgroundMode.DARK -> {}
            BackgroundMode.COLOUR -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(look.colour), tones.bg))))
            BackgroundMode.PHOTO -> if (look.photo.isNotBlank()) {
                AsyncImage(if (look.photo.startsWith("/")) File(look.photo) else look.photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.dim)))
            }
            BackgroundMode.ARTWORK -> {
                Crossfade(art, animationSpec = tween(600), label = "art") { url ->
                    if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.dim)))
            }
        }
    }
}

/** The spotlight's picture behind the top of a tab, fading as the page scrolls. */
@Composable
fun HeroBackdrop(card: MediaCard?, scrolled: Float) = LocalSkin.current.HeroBackdrop(card, scrolled)

/** The spotlight's picture filling the screen, darkened for the words and rows over it. */
@Composable
internal fun GoogleHeroBackdrop(card: MediaCard?, scrolled: Float) {
    val tones = LocalTones.current
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - scrolled * 1.6f).coerceIn(0f, 1f) }) {
        Crossfade(card?.backdrop, animationSpec = tween(700), label = "hero") { url ->
            if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
        }
        // Dark from the left for the words and from the bottom for the rows, as Google TV does.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to tones.bg.copy(alpha = 0.92f), 0.45f to tones.bg.copy(alpha = 0.55f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tones.bg.copy(alpha = 0.55f), 0.18f to Color.Transparent, 0.55f to Color.Transparent, 0.86f to tones.bg)))
    }
}

/** The words over the banner: where it's from, the title (as its logo when there is one), and a line about it. */
@Composable
fun HeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier = Modifier) =
    LocalSkin.current.HeroInfo(card, emptyTitle, emptyHint, modifier)

@Composable
internal fun GoogleHeroInfo(card: MediaCard?, emptyTitle: String, emptyHint: String, modifier: Modifier) {
    val tones = LocalTones.current
    AnimatedContent(card, modifier, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) }, contentKey = { it?.key }, label = "info") { c ->
        Column(Modifier.fillMaxWidth(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            if (c == null) {
                Text(emptyTitle, style = Type.heroTitle, color = tones.text)
                Text(emptyHint, style = Type.body, color = tones.dim, modifier = Modifier.padding(top = 10.dp))
                return@Column
            }
            Text(c.sourceLabel, style = Type.heroMeta, color = tones.dim)
            Spacer(Modifier.height(10.dp))
            if (c.logo != null) {
                AsyncImage(c.logo, c.title, Modifier.heightIn(max = 96.dp).widthIn(max = 380.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
            } else {
                // A long title drops a size rather than pushing the words below it off the banner.
                Text(c.title, style = if (c.title.length > 28) Type.heroTitle.copy(fontSize = 28.sp, lineHeight = 34.sp) else Type.heroTitle,
                    color = tones.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            c.meta?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.heroMeta, color = tones.text, modifier = Modifier.padding(top = 12.dp)) }
            c.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.body, color = tones.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
