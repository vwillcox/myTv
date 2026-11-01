package com.vwillcox.mytv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwillcox.mytv.app
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.TitleSearch
import com.vwillcox.mytv.data.TvApp
import kotlinx.coroutines.delay

/** The keyboard's letters and digits, a row at a time. */
private val KeyRows = listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890")

/**
 * Search, made for the remote: a keyboard on the screen, and beside it what's found, as each key is pressed. The
 * library's titles are fetched once and searched here, so results come with every letter, and keys that can't lead
 * to any title or app go dark. With nothing typed, the library is there to browse, best rated first.
 *
 * The box above the keys still opens the TV's own keyboard (and takes a phone's), and a keyboard plugged into the
 * TV types wherever focus is. The globe asks Google TV, which searches every service.
 */
@Composable
fun SearchScreen(ui: HomeUi, all: List<TvApp>, actions: RowActions) {
    val app = LocalContext.current.app
    val tones = LocalTones.current
    var query by rememberSaveable { mutableStateOf("") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }

    // Every film and show (or as many as AllMedia gives at once), with their names ready to search.
    val titles by produceState<List<MediaCard>?>(null) { value = runCatching { app.feed.titles() }.getOrDefault(emptyList()) }
    val index = remember(titles) { titles.orEmpty().map { TitleSearch.name(it.title) to it } }
    val appIndex = remember(all) { all.map { TitleSearch.name(it.label) to it } }
    // Only the whole library can say a key leads nowhere; otherwise every key stays lit and AllMedia is asked too.
    val whole = titles.orEmpty().let { it.isNotEmpty() && it.size < Feed.TITLE_LIMIT }

    var asked by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(query, whole, titles == null) {
        asked = emptyList(); asking = false
        if (query.isBlank() || whole || titles == null) return@LaunchedEffect
        delay(350)
        asking = true
        asked = runCatching { app.feed.search(query.trim()) }.getOrDefault(emptyList())
        asking = false
    }

    val q = TitleSearch.query(query)
    val found = remember(q, index, asked) { (TitleSearch.find(q, index) + asked).distinctBy { it.key } }
    val apps = remember(q, appIndex) { TitleSearch.find(q, appIndex) }
    val lit: Set<Char>? = remember(q, index, appIndex, whole) {
        if (q.isBlank() || !whole) null else TitleSearch.next(q, index.map { it.first } + appIndex.map { it.first })
    }

    fun type(c: Char) {
        if (c == ' ' && (query.isEmpty() || query.endsWith(' '))) return
        if (query.length < 60) query += c
    }

    // With nothing beside the keys, Right stays on the keyboard rather than wandering up to the top bar.
    val empty = if (q.isBlank()) titles.isNullOrEmpty() else found.isEmpty() && apps.isEmpty()
    val edge = Modifier.focusProperties { if (empty) right = FocusRequester.Cancel }

    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()

    BoxWithConstraints(
        Modifier.fillMaxSize().padding(top = TopBarHeight + 12.dp, bottom = 12.dp)
            // A keyboard plugged in (or a phone's, or the remote's digits) types wherever focus is.
            .onPreviewKeyEvent { e ->
                if (fieldFocused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val c = e.utf16CodePoint
                when {
                    e.key == Key.Backspace -> { query = query.dropLast(1); true }
                    c > 0 && (Character.isLetterOrDigit(c) || c == ' '.code) -> { String(Character.toChars(c)).lowercase().forEach(::type); true }
                    else -> false
                }
            },
    ) {
        val gap = 6.dp
        val field = 52.dp
        // Seven rows of keys under the box, as tall as fits, a little wider than tall.
        val keyHeight = ((maxHeight - field - 14.dp - gap * 6) / 7).coerceIn(28.dp, 46.dp)
        val keyWidth = (keyHeight * 1.12f).coerceAtMost(52.dp)
        val board = keyWidth * 6 + gap * 5

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = Size.gutter).width(board)) {
                LocalSkin.current.SearchFace(fieldFocused, "Films, shows and apps".takeIf { query.isEmpty() }) {
                    BasicTextField(
                        query, { query = it.take(60) }, singleLine = true, interactionSource = fieldInteraction,
                        textStyle = Type.rowSmall.copy(color = tones.text), cursorBrush = SolidColor(tones.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                        modifier = Modifier.fillMaxWidth().focusProperties { up = ui.selectedTab; if (empty) right = FocusRequester.Cancel },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    KeyRows.forEachIndexed { row, keys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            keys.forEachIndexed { i, c ->
                                val entry = when {
                                    row == 0 && i == 0 -> Modifier.focusRequester(ui.entry)
                                    i == keys.lastIndex -> edge
                                    else -> Modifier
                                }
                                BoardKey(c.uppercase(), null, false, row, lit?.contains(c) ?: true, i == 0, i == keys.lastIndex, entry.size(keyWidth, keyHeight)) { type(c) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        val wide = keyWidth * 2 + gap
                        val row = KeyRows.size
                        BoardKey("Space", Icons.Rounded.SpaceBar, true, row, query.isNotEmpty() && (lit?.contains(' ') ?: true), first = true, last = false, Modifier.size(wide, keyHeight)) { type(' ') }
                        // Held down, it clears the lot.
                        BoardKey("Delete", Icons.AutoMirrored.Rounded.Backspace, true, row, query.isNotEmpty(), first = false, last = false, Modifier.size(wide, keyHeight),
                            onLongClick = { query = "" }) { query = query.dropLast(1) }
                        BoardKey("Speak", Icons.Rounded.Mic, false, row, true, first = false, last = false, Modifier.size(keyWidth, keyHeight)) {
                            runCatching {
                                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                            }
                        }
                        BoardKey("Everywhere", Icons.Rounded.Public, false, row, true, first = false, last = true, edge.size(keyWidth, keyHeight)) {
                            if (query.isBlank()) app.system.assistant() else app.system.search(query.trim())
                        }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            Results(ui, query, q.isBlank(), titles, found, apps, asking, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** What's found, beside the keyboard: apps, then titles; or, with nothing typed, the library to browse. */
@Composable
private fun Results(
    ui: HomeUi, query: String, blank: Boolean, titles: List<MediaCard>?, found: List<MediaCard>, apps: List<TvApp>, asking: Boolean,
    actions: RowActions, modifier: Modifier,
) {
    val tones = LocalTones.current
    BoxWithConstraints(modifier) {
        val gap = 16.dp
        val room = maxWidth - Size.gutter
        val columns = ((room + gap) / (140.dp + gap)).toInt().coerceAtLeast(1)
        val width = (room - gap * (columns - 1)) / columns

        fun LazyGridScope.line(key: String, content: @Composable () -> Unit) = item(key, span = { GridItemSpan(maxLineSpan) }) { content() }
        fun LazyGridScope.note(text: String) = line("note") {
            Text(text, style = Type.body, color = tones.dim, modifier = Modifier.padding(start = 10.dp, top = 4.dp, end = 20.dp))
        }
        fun LazyGridScope.cards(list: List<MediaCard>) = items(list, key = { it.key }) { card ->
            MediaCardView(card, width, showText = true, onFocused = { ui.focusedArt = card.backdrop }, onLongClick = { actions.menu(card) }) { actions.open(card) }
        }

        LazyVerticalGrid(
            GridCells.Fixed(columns),
            // Up from the top row goes to the tabs, as on every page; Right from the last card goes nowhere.
            Modifier.fillMaxSize().focusProperties {
                onExit = {
                    when (requestedFocusDirection) {
                        FocusDirection.Up -> runCatching { ui.selectedTab.requestFocus() }
                        FocusDirection.Right -> cancelFocusChange()
                    }
                }
            },
            // Room above for a focused card to grow into.
            contentPadding = PaddingValues(end = Size.gutter, top = 2.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                blank -> when {
                    titles == null -> note("Fetching your library…")
                    titles.isEmpty() -> note("Type, or press the microphone. The globe asks Google TV, which searches every service.")
                    else -> {
                        line("head") { SkinTitle("Your library, best rated first", TitleKind.SECTION) }
                        cards(titles.take(30))
                    }
                }
                else -> {
                    if (apps.isNotEmpty()) {
                        line("apps-head") { SkinTitle("Apps", TitleKind.SECTION) }
                        line("apps") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Size.appGap)) {
                                items(apps, key = { it.pkg }) { a -> AppTile(a, banner = false, moving = false, onLongClick = { actions.appMenu(a) }) { actions.launch(a) } }
                            }
                        }
                    }
                    if (found.isNotEmpty()) {
                        line("found-head") { SkinTitle(if (found.size == 1) "In your library" else "In your library · ${found.size}", TitleKind.SECTION) }
                        cards(found)
                    }
                    when {
                        asking && found.isEmpty() -> note("Searching…")
                        found.isEmpty() && apps.isEmpty() ->
                            note("Nothing called “${query.trim()}” in your library or apps. The globe asks Google TV, which searches every service.")
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardKey(
    label: String, icon: ImageVector?, wide: Boolean, group: Int, lit: Boolean, first: Boolean, last: Boolean, modifier: Modifier,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LocalSkin.current.KeyFace(label, icon, wide, group, lit, first, last, focused, Modifier.tvClickable(interaction, onLongClick, onClick), modifier)
}

@Composable
internal fun GoogleSearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit) {
    val tones = LocalTones.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(tones.surface)
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = tones.dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (placeholder != null) Text(placeholder, style = Type.rowSmall, color = tones.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            field()
        }
    }
}

/** Google TV's key: a rounded square of glass, white and a little bigger when focused, faint when it leads nowhere. */
@Composable
internal fun GoogleKeyFace(label: String, icon: ImageVector?, lit: Boolean, focused: Boolean, control: Modifier, modifier: Modifier) {
    val tones = LocalTones.current
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(120), label = "key")
    val ink = when {
        focused -> tones.onAccent
        lit -> tones.text
        else -> tones.text.copy(alpha = 0.25f)
    }
    Box(
        modifier.then(control).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = if (lit) 0.09f else 0.03f)),
        Alignment.Center,
    ) {
        if (icon != null) Icon(icon, label, tint = ink, modifier = Modifier.size(22.dp))
        else Text(label, style = Type.tab.copy(fontSize = 18.sp), color = ink)
    }
}
