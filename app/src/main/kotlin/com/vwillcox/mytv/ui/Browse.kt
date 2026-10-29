@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.vwillcox.mytv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwillcox.mytv.app
import com.vwillcox.mytv.data.Config
import com.vwillcox.mytv.data.FeedState
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.Open
import com.vwillcox.mytv.data.Row
import com.vwillcox.mytv.data.RowStyle
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.data.TvApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a row of cards needs from the screen around it. */
class RowActions(
    val open: (MediaCard) -> Unit,
    val menu: (MediaCard) -> Unit,
    val launch: (TvApp) -> Unit,
    val appMenu: (TvApp) -> Unit,
)

/**
 * A tab of films and shows (Home, Movies, Shows): the top bar's space, the banner over Top picks, then the rows.
 * The page scrolls row by row as focus moves, with the focused row near the top and the one above peeking in.
 */
@Composable
fun BrowseScreen(ui: HomeUi, tab: Tab, feed: FeedState, cfg: Config, favourites: List<TvApp>, actions: RowActions) {
    val content = feed.tabs[tab]
    val picks = content?.picks.orEmpty()
    val rows = content?.rows.orEmpty().filter { it.style == RowStyle.APPS || it.cards.isNotEmpty() }.distinctBy { it.key }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // The banner moves on by itself while nothing in Top picks has focus.
    LaunchedEffect(picks, ui.pickFocused) {
        if (picks.isEmpty()) { ui.spotlight = null; return@LaunchedEffect }
        if (ui.spotlight == null || picks.none { it.key == ui.spotlight?.key }) ui.spotlight = picks.first()
        while (!ui.pickFocused) {
            delay(cfg.look.heroSeconds * 1000L)
            val i = picks.indexOfFirst { it.key == ui.spotlight?.key }
            ui.spotlight = picks[(i + 1) % picks.size]
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { if (list.firstVisibleItemIndex > 0) 1f else list.firstVisibleItemScrollOffset / with(density) { 300.dp.toPx() } }
            .collect { ui.scrolled = it.coerceIn(0f, 1f) }
    }
    LaunchedEffect(ui.resets) { if (ui.resets > 0) list.scrollToItem(0) }

    fun focusRow(index: Int) = scope.launch {
        if (index == 0) { list.animateScrollToItem(0); return@launch }
        // The row losing focus hides its cards' names as this one shows them. Measured before those sizes land,
        // the row would end up above the top by the difference.
        repeat(2) { withFrameNanos { } }
        val peek = with(density) { 6.dp.toPx() }
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (info != null) list.animateScrollBy(info.offset - peek) else list.animateScrollToItem(index, -peek.toInt())
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(ui.spotlight, ui.scrolled)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screen = maxHeight
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollSpec) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 180.dp)) {
                    // The banner's key and the rows' keys live apart, so no row's key can be the banner's.
                    item("banner") {
                        Column(Modifier.fillMaxWidth().height(screen - Size.bannerPeek)) {
                            Spacer(Modifier.height(TopBarHeight))
                            HeroInfo(
                                ui.spotlight,
                                emptyTitle = if (feed.libraryProblem == null) "Nothing to pick from yet" else "Welcome to ${cfg.look.brand.ifBlank { "myTV" }}",
                                emptyHint = if (feed.libraryProblem == null) "Allow some apps under Settings → Media sources, or add a TMDB key."
                                else "${feed.libraryProblem} Other apps can fill the screen: Settings → Media sources.",
                                modifier = Modifier.weight(1f).padding(start = Size.gutter, bottom = 18.dp),
                            )
                            if (picks.isNotEmpty()) {
                                SkinTitle("Top picks for you", TitleKind.PICKS)
                                CardRow(
                                    picks, RowStyle.PICKS, focusedRow = false,
                                    modifier = Modifier.focusRequester(ui.entry).upToTab(ui)
                                        .onFocusChanged { ui.pickFocused = it.hasFocus; if (it.hasFocus) focusRow(0) },
                                    onCardFocused = { ui.spotlight = it; ui.focusedArt = it.backdrop },
                                    actions = actions,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    itemsIndexed(rows, key = { _, r -> "row:${r.key}" }) { i, row ->
                        var focused by remember { mutableStateOf(false) }
                        val entryMod = if (picks.isEmpty() && i == 0) Modifier.focusRequester(ui.entry).upToTab(ui) else Modifier
                        RowBlock(
                            row, focused, cfg, favourites, ui, actions,
                            modifier = entryMod.onFocusChanged { f -> focused = f.hasFocus; if (f.hasFocus) focusRow(i + 1) },
                        )
                    }
                }
            }
        }
    }
}

/** A titled row. The focused row's title stands out, and only its cards carry their names, as on Google TV. */
@Composable
fun RowBlock(row: Row, focused: Boolean, cfg: Config, favourites: List<TvApp>, ui: HomeUi, actions: RowActions, modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        SkinTitle(row.title, TitleKind.ROW, focused)
        when (row.style) {
            RowStyle.APPS -> AppsRow(favourites, cfg, ui, actions, modifier)
            else -> CardRow(row.cards, row.style, focused, modifier, onCardFocused = { ui.focusedArt = it.backdrop }, actions = actions)
        }
    }
}

@Composable
fun CardRow(
    cards: List<MediaCard>,
    style: RowStyle,
    focusedRow: Boolean,
    modifier: Modifier = Modifier,
    onCardFocused: (MediaCard) -> Unit = {},
    actions: RowActions,
) {
    val lead = with(LocalDensity.current) { Size.gutter.toPx() }
    val width = if (style == RowStyle.PICKS) Size.pickWidth else Size.wideWidth
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(lead) { PivotSpec(lead) }) {
        LazyRow(
            modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = Size.gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Size.cardGap),
        ) {
            // Two cards with one key would bring the whole home screen down, so a repeat is left out.
            items(cards.distinctBy { it.key }, key = { it.key }) { card ->
                if (style == RowStyle.TILES) TileView(card, Size.wideWidth) { actions.open(card) }
                else MediaCardView(
                    card, width, showText = focusedRow && style == RowStyle.WIDE,
                    onFocused = { onCardFocused(card) },
                    onLongClick = { actions.menu(card) },
                ) { actions.open(card) }
            }
        }
    }
}

/**
 * Your apps. While one is being moved (chosen from its menu), left and right carry it along the row and OK or
 * Back puts it down.
 */
@Composable
fun AppsRow(apps: List<TvApp>, cfg: Config, ui: HomeUi, actions: RowActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = context.app.store
    val lead = with(LocalDensity.current) { Size.gutter.toPx() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(lead) { PivotSpec(lead) }) {
        LazyRow(
            modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = Size.gutter - Size.appInset, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (cfg.look.appStyle == com.vwillcox.mytv.data.AppStyle.BANNER) Size.cardGap else Size.appGap),
        ) {
            items(apps, key = { it.pkg }) { app ->
                val moving = ui.moving == app.pkg
                AppTile(
                    app, banner = cfg.look.appStyle == com.vwillcox.mytv.data.AppStyle.BANNER, moving = moving,
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (!moving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                        when {
                            step != 0 -> {
                                store.update { c ->
                                    val order = c.favourites.toMutableList()
                                    val from = order.indexOf(app.pkg)
                                    val to = from + step
                                    if (from >= 0 && to in order.indices) { order.removeAt(from); order.add(to, app.pkg) }
                                    c.copy(favourites = order)
                                }
                                true
                            }
                            e.key == Key.Back || e.key == Key.DirectionCenter || e.key == Key.Enter -> { ui.moving = null; true }
                            else -> true
                        }
                    },
                    onLongClick = { actions.appMenu(app) },
                ) { if (moving) ui.moving = null else actions.launch(app) }
            }
        }
    }
}

/** A genre or other set from the library, in a grid of its own over the tab. */
@Composable
fun GridScreen(browse: Open.Browse, actions: RowActions) {
    val feed = LocalContext.current.app.feed
    val cards by produceState<List<MediaCard>?>(null, browse) { value = runCatching { feed.browse(browse.types, browse.genre) }.getOrDefault(emptyList()) }
    val tones = LocalTones.current
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        SkinTitle(browse.title, TitleKind.PAGE)
        val list = cards
        when {
            list == null -> Text("Loading…", style = Type.body, color = tones.dim, modifier = Modifier.padding(start = Size.gutter))
            list.isEmpty() -> Text("Nothing here.", style = Type.body, color = tones.dim, modifier = Modifier.padding(start = Size.gutter))
            else -> {
                LazyVerticalGrid(
                    GridCells.Adaptive(Size.wideWidth), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Size.gutter, end = Size.gutter, top = 10.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(Size.cardGap), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(list, key = { it.key }) { card ->
                        val m = if (card == list.first()) Modifier.focusRequester(first) else Modifier
                        MediaCardView(card, Size.wideWidth, showText = true, modifier = m, onLongClick = { actions.menu(card) }) { actions.open(card) }
                    }
                }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            }
        }
    }
}

/** A heading as the skin draws it. */
@Composable
fun SkinTitle(text: String, kind: TitleKind, focused: Boolean = false, modifier: Modifier = Modifier) =
    LocalSkin.current.Title(text, kind, focused, modifier)

@Composable
internal fun GoogleTitle(text: String, kind: TitleKind, focused: Boolean, modifier: Modifier) {
    val tones = LocalTones.current
    when (kind) {
        TitleKind.ROW -> {
            val size by animateFloatAsState(if (focused) 30f else 16f, tween(200), label = "title")
            Text(text, style = Type.rowBig.copy(fontSize = size.sp), color = if (focused) tones.text else tones.dim,
                modifier = modifier.padding(start = Size.gutter, bottom = if (focused) 18.dp else 12.dp))
        }
        TitleKind.PICKS -> Text(text, style = Type.rowSmall.copy(fontSize = 14.sp), color = tones.dim, modifier = modifier.padding(start = Size.gutter, bottom = 12.dp))
        TitleKind.SECTION -> Text(text, style = Type.rowSmall, color = tones.dim, modifier = modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
        TitleKind.PAGE -> Text(text, style = Type.rowBig, color = tones.text, modifier = modifier.padding(start = Size.gutter, bottom = 18.dp))
    }
}
