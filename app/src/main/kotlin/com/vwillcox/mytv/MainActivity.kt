@file:OptIn(ExperimentalComposeUiApi::class)

package com.vwillcox.mytv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.vwillcox.mytv.data.Config
import com.vwillcox.mytv.data.Open
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.ui.AppsScreen
import com.vwillcox.mytv.ui.BrowseScreen
import com.vwillcox.mytv.ui.GridScreen
import com.vwillcox.mytv.ui.HomeFrame
import com.vwillcox.mytv.ui.HomeUi
import com.vwillcox.mytv.ui.LocalSkin
import com.vwillcox.mytv.ui.LocalSounds
import com.vwillcox.mytv.ui.MyTvTheme
import com.vwillcox.mytv.ui.Panel
import com.vwillcox.mytv.ui.PanelHost
import com.vwillcox.mytv.ui.RowActions
import com.vwillcox.mytv.ui.SearchScreen
import com.vwillcox.mytv.ui.defaultFavourites
import com.vwillcox.mytv.system.SystemPages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : ComponentActivity() {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cfg by app.store.config.collectAsState()
            MyTvTheme(cfg.look) { Root(app, cfg, events) }
        }
        handle(intent, fresh = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent, fresh = false)
    }

    /** Home pressed while already home goes back to the top of Home; a long press opens settings. */
    private fun handle(intent: Intent, fresh: Boolean) {
        when {
            intent.action == ACTION_PANEL -> events.tryEmit(EVENT_PANEL)
            !fresh && intent.hasCategory(Intent.CATEGORY_HOME) -> events.tryEmit(EVENT_HOME)
        }
    }

    override fun onResume() {
        super.onResume()
        app.feed.visible = true
        // Coming back from an app: what was being watched has likely moved on.
        app.feed.refreshIfOlderThan(60_000)
    }

    override fun onPause() {
        app.feed.visible = false
        super.onPause()
    }

    companion object {
        const val ACTION_PANEL = "com.vwillcox.mytv.PANEL"
        private const val EVENT_PANEL = "panel"
        private const val EVENT_HOME = "home"
    }
}

@Composable
private fun Root(app: App, cfg: Config, events: SharedFlow<String>) {
    val ui = remember { HomeUi() }
    val feed by app.feed.state.collectAsState()
    val all by app.apps.apps.collectAsState()
    var barFocused by remember { mutableStateOf(false) }

    // The first time, Your apps is every TV app, the owner's own first.
    LaunchedEffect(all) {
        if (all.isNotEmpty() && cfg.favourites.isEmpty()) app.store.update { it.copy(favourites = defaultFavourites(all, it)) }
    }
    val favourites = cfg.favourites.mapNotNull { p -> all.firstOrNull { it.pkg == p } }.filter { it.pkg !in cfg.hidden }
    val actions = remember(app) {
        RowActions(
            open = { card ->
                when (val o = card.open) {
                    is Open.Browse -> ui.grid = o
                    is Open.Settings -> ui.open(Panel.Page(o.page))
                    else -> app.system.open(card)
                }
            },
            menu = { ui.open(Panel.CardMenu(it)) },
            launch = { app.apps.launch(it) },
            appMenu = { ui.open(Panel.AppMenu(it)) },
        )
    }

    fun focusTab() = runCatching { ui.selectedTab.requestFocus() }
    fun reset() {
        ui.panels.clear(); ui.grid = null; ui.moving = null; ui.searching = false; ui.tab = Tab.HOME
        ui.resets++
    }

    LaunchedEffect(Unit) {
        events.collect {
            when (it) {
                "panel" -> { ui.panels.clear(); ui.open(Panel.Page("root")) }
                "home" -> { reset(); delay(80); if (runCatching { ui.entry.requestFocus() }.isFailure) focusTab() }
            }
        }
    }
    // Focus starts on the first of Top picks once there are some, else on the Home tab.
    LaunchedEffect(Unit) {
        delay(400)
        repeat(10) {
            if (feed.tabs.isNotEmpty() || !feed.loading) {
                if (runCatching { ui.entry.requestFocus() }.isSuccess) return@LaunchedEffect
            }
            delay(300)
        }
        focusTab()
    }
    // Closing the last panel gives focus back to where it was.
    var hadPanel by remember { mutableStateOf(false) }
    LaunchedEffect(ui.panels.size) {
        if (ui.panels.isEmpty() && hadPanel) { delay(30); runCatching { ui.main.requestFocus() } }
        hadPanel = ui.panels.isNotEmpty()
    }

    val skin = LocalSkin.current
    val sounds = LocalSounds.current
    BackHandler(true) {
        when {
            ui.moving != null -> ui.moving = null
            ui.panels.isNotEmpty() -> { sounds.close(); ui.closePanel() }
            ui.grid != null -> ui.grid = null
            !barFocused -> { ui.resets++; focusTab() }
            ui.searching || ui.tab != Tab.HOME -> { reset(); runCatching { ui.tabs.getValue(Tab.HOME.name).requestFocus() } }
        }
    }

    val frame = HomeFrame(
        ui, cfg.look, feed.avatar, feed.userName ?: cfg.watching?.name,
        onTab = { t ->
            // Another tab closes a genre's grid; the one already open (a skin may show the tabs beside it) keeps it.
            val moved = if (t == null) !ui.searching else ui.searching || t != ui.tab
            if (moved) ui.grid = null
            if (t == null) ui.searching = true else { ui.searching = false; ui.tab = t }
            ui.scrolled = 0f
        },
        onNavFocus = { barFocused = it },
    ) { screen ->
        val grid = ui.grid
        if (grid != null) {
            GridScreen(grid, actions)
        } else {
            AnimatedContent(
                if (ui.searching) "search" else ui.tab.name, screen.fillMaxSize(),
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "tab",
            ) { shown ->
                when (shown) {
                    "search" -> SearchScreen(ui, all, actions)
                    Tab.APPS.name -> AppsScreen(
                        ui, cfg, all.filter { it.pkg !in cfg.hidden || it.pkg in cfg.favourites }, actions,
                        onStore = { app.apps.store() }, onInstall = { ui.open(Panel.Page("phone")) },
                        onSettings = { ui.open(Panel.Page("apps")) }, onSystem = { app.system.openSystem(SystemPages.first()) },
                    )
                    else -> BrowseScreen(ui, Tab.valueOf(shown), feed, cfg, favourites, actions)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        skin.Frame(frame)
        PanelHost(ui, cfg, feed, all)
    }
}
