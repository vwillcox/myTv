@file:OptIn(ExperimentalComposeUiApi::class)

package com.vwillcox.mytv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vwillcox.mytv.data.AppStyle
import com.vwillcox.mytv.data.Config
import com.vwillcox.mytv.data.TvApp

/** The Apps tab: getting and installing apps, then every app, Your apps first. */
@Composable
fun AppsScreen(ui: HomeUi, cfg: Config, all: List<TvApp>, actions: RowActions, onStore: () -> Unit, onInstall: () -> Unit, onSettings: () -> Unit, onSystem: () -> Unit) {
    val tones = LocalTones.current
    val banner = cfg.look.appStyle == AppStyle.BANNER
    val grid = rememberLazyGridState()
    LaunchedEffect(grid) { snapshotFlow { if (grid.firstVisibleItemIndex > 0) 1f else 0f }.collect { ui.scrolled = it } }
    val favourites = cfg.favourites.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
    val others = all.filter { it.pkg !in cfg.favourites && it.pkg !in cfg.hidden }
    LazyVerticalGrid(
        GridCells.Adaptive(if (banner) Size.bannerWidth + 4.dp else Size.appCircle + 30.dp), state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Size.gutter - 10.dp, end = Size.gutter, top = TopBarHeight + 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(if (banner) 18.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "get") {
            Row(
                Modifier.padding(start = 10.dp, bottom = 18.dp).focusRequester(ui.entry).focusProperties { up = ui.selectedTab }.focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ActionTile(Icons.Rounded.Shop, "Play Store", "Get more apps", onStore)
                ActionTile(Icons.Rounded.InstallMobile, "Install an APK", "From your phone or a link", onInstall)
                ActionTile(Icons.Rounded.Tune, "Your apps", "Order, hide, icon style", onSettings)
                ActionTile(Icons.Rounded.Settings, "Android settings", "The TV's own settings", onSystem)
            }
        }
        if (favourites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "favh") { Header("Your apps") }
            items(favourites, key = { "f:" + it.pkg }) { app -> AppTile(app, banner, false, onLongClick = { actions.appMenu(app) }) { actions.launch(app) } }
        }
        if (others.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "allh") { Header("All apps") }
            items(others, key = { "a:" + it.pkg }) { app -> AppTile(app, banner, false, onLongClick = { actions.appMenu(app) }) { actions.launch(app) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
            Text("Hold OK on an app for its options: add it to Your apps, move it, hide it, or uninstall it.",
                style = Type.itemSub, color = tones.faint, modifier = Modifier.padding(start = 10.dp, top = 20.dp))
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = Type.rowSmall, color = LocalTones.current.dim, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))

@Composable
private fun ActionTile(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val tones = LocalTones.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tile")
    Row(
        Modifier.focusRing(focused, scale, 14.dp).tvClickable(interaction, onClick = onClick).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else tones.surface).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) tones.accent else Color(0xFF2E3035)), Alignment.Center) {
            Icon(icon, null, tint = if (focused) tones.onAccent else tones.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = Type.item, color = if (focused) tones.onAccent else tones.text)
            Text(sub, style = Type.itemSub, color = if (focused) tones.onAccent.copy(alpha = 0.7f) else tones.dim)
        }
    }
}

