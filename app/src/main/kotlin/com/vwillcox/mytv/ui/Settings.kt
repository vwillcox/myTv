package com.vwillcox.mytv.ui

import android.app.role.RoleManager
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vwillcox.mytv.BuildConfig
import com.vwillcox.mytv.app
import com.vwillcox.mytv.data.AppStyle
import com.vwillcox.mytv.data.BackgroundMode
import com.vwillcox.mytv.data.Config
import com.vwillcox.mytv.data.Feed
import com.vwillcox.mytv.data.FeedState
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.Open
import com.vwillcox.mytv.data.SourcePrefs
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.data.ThemeId
import com.vwillcox.mytv.data.TvApp
import com.vwillcox.mytv.data.Watcher
import com.vwillcox.mytv.data.channelKey
import com.vwillcox.mytv.data.source
import com.vwillcox.mytv.system.PhoneSetup
import com.vwillcox.mytv.system.SystemControl.Companion.ALLMEDIA
import com.vwillcox.mytv.system.SystemPages
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Draws the panel on top of the stack. */
@Composable
fun PanelHost(ui: HomeUi, cfg: Config, feed: FeedState, all: List<TvApp>) {
    val panel = ui.panels.lastOrNull() ?: return
    val key = "${ui.panels.size}:$panel"
    when (panel) {
        is Panel.CardMenu -> CardMenu(ui, panel.card, key)
        is Panel.AppMenu -> AppMenu(ui, cfg, panel.app, key)
        is Panel.Page -> Page(ui, panel.id, cfg, feed, all, key)
    }
}

private fun mins(ms: Long) = when {
    ms <= 0 || ms >= Int.MAX_VALUE -> "Never"
    ms < 3_600_000 -> "${ms / 60_000} minutes"
    ms == 3_600_000L -> "1 hour"
    else -> "${ms / 3_600_000} hours"
}

private fun SourcePrefs.summary() = listOfNotNull(
    "Continue watching".takeIf { continueWatching }, "Top picks".takeIf { topPicks }, "Rows".takeIf { rows },
).joinToString(", ").ifBlank { "Off: shows nothing" }

@Composable
private fun Page(ui: HomeUi, id: String, cfg: Config, feed: FeedState, all: List<TvApp>, key: String) {
    val app = LocalContext.current.app
    val store = app.store
    val sys = app.system
    fun go(page: String) = ui.open(Panel.Page(page))
    val parts = id.split(':', limit = 2)

    when (parts[0]) {
        "root" -> SidePanel("Settings", key = key) {
            item { PanelItem("Who's watching?", feed.userName ?: "AllMedia's own sign-in", Icons.Rounded.People, chevron = true) { go("account") } }
            item { PanelItem("Media sources", "Which apps may add to Continue watching, Top picks and rows", Icons.Rounded.Movie, chevron = true) { go("sources") } }
            item { PanelItem("Home screen rows", "Which rows each tab shows, in what order", Icons.Rounded.ViewAgenda, chevron = true) { go("rows") } }
            item { PanelItem("Your apps", "Icon style, hidden apps", Icons.Rounded.Apps, chevron = true) { go("apps") } }
            item { PanelItem("Theme", cfg.look.theme.label, Icons.Rounded.Style, chevron = true) { go("theme") } }
            item { PanelItem("Look and background", cfg.look.background.label, Icons.Rounded.Palette, chevron = true) { go("look") } }
            item { PanelItem("Screensaver", sys.dream?.let { d -> sys.dreams().firstOrNull { it.component == d }?.label } ?: "Not set", Icons.Rounded.Bedtime, chevron = true) { go("screensaver") } }
            item { PanelItem("Library", feed.libraryProblem ?: "From AllMedia · titles open there", Icons.Rounded.Storage, chevron = true) { go("library") } }
            item { PanelItem("Popular titles", if (cfg.popular.tmdbKey.isBlank()) "No TMDB key: using your library's ratings" else "From TMDB, matched to your library", Icons.Rounded.TrendingUp, chevron = true) { go("popular") } }
            item { PanelItem("Set up from your phone", "TMDB key, install APKs, background photo", Icons.Rounded.PhoneAndroid, chevron = true) { go("phone") } }
            item { PanelItem("Home button", if (sys.homeServiceOn && cfg.overrideHome) "Opens ${cfg.look.brand.ifBlank { "myTV" }}" else "Opens Google TV", Icons.Rounded.Home, chevron = true) { go("home") } }
            item { PanelItem("Android settings", "Network, display, remotes, apps…", Icons.Rounded.Settings, chevron = true) { go("system") } }
            item { PanelItem("Permissions", "What this app can and can't reach", Icons.Rounded.Lock, chevron = true) { go("permissions") } }
            item { PanelItem("About", "Version ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info) {} }
        }

        "account" -> {
            val chosen = feed.people.firstOrNull { it.id == cfg.watching?.id } ?: feed.people.firstOrNull { it.own }
            SidePanel("Who's watching?", "AllMedia plays as whoever is chosen here, and the home screen shows their Continue watching and picks.", key = key) {
                for (p in feed.people) item(p.id) {
                    PanelItem(
                        p.name, if (p.own) "AllMedia's own sign-in" else null,
                        trailing = { focused ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(Feed.tint(p.name))), Alignment.Center) {
                                Text(p.name.take(1).uppercase(), style = Type.item, color = Color.White)
                                if (p.image != null) AsyncImage(p.image, null, Modifier.size(30.dp).clip(CircleShape))
                            }
                            if (p.id == chosen?.id) androidx.compose.material3.Icon(Icons.Rounded.Done, null, tint = if (focused) LocalTones.current.onAccent else LocalTones.current.accent, modifier = Modifier.padding(start = 10.dp))
                        },
                    ) { store.update { it.copy(watching = if (p.own) null else Watcher(p.id, p.name)) }; ui.closePanel() }
                }
                when {
                    feed.libraryProblem != null -> item { PanelNote(feed.libraryProblem) }
                    feed.people.isEmpty() -> item { PanelNote("Nobody is signed in to AllMedia yet.") }
                }
                item { PanelItem("Add or remove people", "In AllMedia: Settings", Icons.Rounded.Add) { app.apps.launch(ALLMEDIA); ui.closePanel() } }
            }
        }

        "sources" -> SidePanel("Media sources", "Nothing appears here unless its source is allowed. Sideloaded apps start switched on; Play Store and built-in apps (the paid services) start off.", key = key) {
            item { PanelItem(app.apps.label(ALLMEDIA), "Your library${feed.userName?.let { " as $it" } ?: ""} · " + cfg.source(ALLMEDIA, true).summary(), Icons.Rounded.Storage, chevron = true) { go("source:$ALLMEDIA") } }
            val publishers = feed.publishers.filter { it != ALLMEDIA }.sortedBy { app.apps.label(it).lowercase() }
            if (!sys.canReadListings) item { PanelNote("This app can't read other apps' channels and Continue watching yet. See Permissions.") }
            else if (publishers.isEmpty()) item { PanelNote("No app has published channels or Continue watching to the TV yet.") }
            if (publishers.isNotEmpty()) item { PanelHeading("Apps that publish to the TV") }
            for (pkg in publishers) item(pkg) {
                PanelItem(app.apps.label(pkg), cfg.source(pkg, app.apps.isOwn(pkg)).summary() + if (app.apps.isOwn(pkg)) " · sideloaded" else "", chevron = true) { go("source:$pkg") }
            }
        }

        "source" -> {
            val pkg = parts.getOrElse(1) { ALLMEDIA }
            val library = pkg == ALLMEDIA
            val label = app.apps.label(pkg)
            val prefs = cfg.source(pkg, app.apps.isOwn(pkg))
            fun set(p: SourcePrefs) = store.update { it.copy(sources = it.sources + (pkg to p)) }
            SidePanel(label, key = key) {
                item { PanelToggle("Continue watching", "Its part-watched and next titles", prefs.continueWatching) { set(prefs.copy(continueWatching = it)) } }
                item { PanelToggle("Top picks", "Its titles in the banner at the top of each tab", prefs.topPicks) { set(prefs.copy(topPicks = it)) } }
                item { PanelToggle(if (library) "Rows" else "Its channels as rows", if (library) "Popular, Recently added, Not seen yet and the rest" else null, prefs.rows) { set(prefs.copy(rows = it)) } }
                if (library) item { PanelNote("Your library, from AllMedia as whoever is watching. Titles open in AllMedia.") }
                val names = feed.channelsOf[pkg].orEmpty()
                if (!library && names.isNotEmpty()) {
                    item { PanelHeading("Its channels") }
                    for (n in names) item("channel:$n") {
                        val k = channelKey(pkg, n)
                        PanelToggle(n, null, k !in cfg.hiddenChannels) { on ->
                            store.update { it.copy(hiddenChannels = if (on) it.hiddenChannels - k else it.hiddenChannels + k) }
                        }
                    }
                    if (!prefs.rows && !prefs.topPicks) item { PanelNote("Switch on rows or Top picks above for these to show.") }
                }
            }
        }

        "rows" -> SidePanel("Home screen rows", key = key) {
            for (t in listOf(Tab.HOME, Tab.MOVIES, Tab.SHOWS)) item(t.name) { PanelItem(t.label, cfg.rowsFor(t).filter { it.shown }.joinToString(", ") { it.kind.label }, chevron = true) { go("tabrows:${t.name}") } }
        }

        "tabrows" -> {
            val tab = Tab.valueOf(parts[1])
            val rows = cfg.rowsFor(tab)
            SidePanel("${tab.label} rows", "OK shows or hides a row. Hold OK to move it up.", key = key) {
                rows.forEachIndexed { i, r ->
                    item(r.kind.name) {
                        PanelToggle(r.kind.label, null, r.shown, Icons.Rounded.DragIndicator) { on ->
                            store.update { c -> c.copy(rows = c.rows + (tab to rows.map { if (it.kind == r.kind) it.copy(shown = on) else it })) }
                        }
                    }
                    item(r.kind.name + "up") {
                        if (i > 0) PanelItem("   ↑ Move ${r.kind.label} up", null) {
                            val order = rows.toMutableList(); order.add(i - 1, order.removeAt(i))
                            store.update { c -> c.copy(rows = c.rows + (tab to order)) }
                        }
                    }
                }
            }
        }

        "apps" -> SidePanel("Your apps", key = key) {
            item { PanelHeading("Style") }
            for (s in AppStyle.entries) item(s.name) { PanelChoice(s.label, selected = cfg.look.appStyle == s) { store.update { it.copy(look = it.look.copy(appStyle = s)) } } }
            item {
                PanelItem("Fill Your apps with every app", "Starts the order again from scratch", Icons.Rounded.Apps) {
                    store.update { it.copy(favourites = defaultFavourites(all, it)) }
                }
            }
            val hidden = cfg.hidden.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
            if (hidden.isNotEmpty()) item { PanelHeading("Hidden apps (OK shows it again)") }
            for (a in hidden) item(a.pkg) { PanelItem(a.label, icon = Icons.Rounded.VisibilityOff) { store.update { it.copy(hidden = it.hidden - a.pkg) } } }
        }

        "theme" -> SidePanel("Theme", "How the whole screen looks. Your apps, rows and settings are the same in each.", key = key) {
            for (t in ThemeId.entries) item(t.name) {
                PanelChoice(t.label, t.sub, cfg.look.theme == t) { store.update { it.copy(look = it.look.copy(theme = t)) } }
            }
            if (cfg.look.theme == ThemeId.LCARS) {
                item { PanelHeading("LCARS") }
                item { PanelToggle("Computer sounds", "A chirp when you choose something, as on the Enterprise", cfg.look.sounds) { on -> store.update { it.copy(look = it.look.copy(sounds = on)) } } }
                item {
                    PanelToggle("Flashing panels and readouts", "Blocks that flicker, numbers that run, and the power-up as it starts", cfg.look.motion) { on ->
                        store.update { it.copy(look = it.look.copy(motion = on)) }
                    }
                }
                item { PanelNote("Each app's mark is lifted off its icon and drawn in the panel colour nearest its own.") }
            }
        }

        "look" -> SidePanel("Look and background", key = key) {
            item { PanelItem("Theme", cfg.look.theme.label, Icons.Rounded.Style, chevron = true) { go("theme") } }
            item { PanelHeading("Background") }
            for (m in BackgroundMode.entries) item(m.name) {
                PanelChoice(m.label, if (m == BackgroundMode.PHOTO) "Send one from the phone setup page" else null, cfg.look.background == m) {
                    if (m == BackgroundMode.PHOTO && cfg.look.photo.isBlank()) go("phone")
                    store.update { it.copy(look = it.look.copy(background = m)) }
                }
            }
            if (cfg.look.background == BackgroundMode.COLOUR) {
                item { PanelHeading("Colour") }
                for ((name, c) in Swatches) item("c$name") { PanelChoice(name, selected = cfg.look.colour == c) { store.update { it.copy(look = it.look.copy(colour = c)) } } }
            }
            if (cfg.look.background == BackgroundMode.PHOTO || cfg.look.background == BackgroundMode.ARTWORK) {
                item { PanelHeading("Darkening") }
                for (d in listOf(0.3f, 0.45f, 0.6f, 0.75f)) item("d$d") { PanelChoice("${(d * 100).toInt()}%", selected = cfg.look.dim == d) { store.update { it.copy(look = it.look.copy(dim = d)) } } }
            }
            // Other themes have colours of their own.
            if (cfg.look.theme == ThemeId.GOOGLE_TV) {
                item { PanelHeading("Highlight colour") }
                for ((name, c) in Accents) item("a$name") { PanelChoice(name, selected = cfg.look.accent == c) { store.update { it.copy(look = it.look.copy(accent = c)) } } }
            }
            item { PanelHeading("Clock") }
            item { PanelToggle("24-hour clock", null, cfg.look.clock24) { on -> store.update { it.copy(look = it.look.copy(clock24 = on)) } } }
            item { PanelHeading("Banner") }
            for (s in listOf(6, 9, 15, 30)) item("h$s") { PanelChoice("Next title every $s seconds", selected = cfg.look.heroSeconds == s) { store.update { it.copy(look = it.look.copy(heroSeconds = s)) } } }
        }

        "screensaver" -> {
            var tick by remember { mutableIntStateOf(0) }
            val current = remember(tick) { sys.dream }
            val after = remember(tick) { sys.screensaverAfterMs }
            val sleep = remember(tick) { sys.sleepAfterMs }
            SidePanel("Screensaver", if (sys.canWriteSecure) null else "Changing these needs a permission only adb can grant. See Permissions.", key = key) {
                item { PanelHeading("Screensaver") }
                for (d in sys.dreams()) item(d.component.flattenToString()) {
                    PanelChoice(d.label, d.component.packageName, current == d.component) { sys.setDream(d.component); tick++ }
                }
                item { PanelHeading("Start after") }
                for (m in listOf(5L, 10L, 15L, 30L, 60L)) item("s$m") { PanelChoice("$m minutes", selected = after == m * 60_000) { sys.screensaverAfterMs = m * 60_000; tick++ } }
                item { PanelHeading("Then sleep after") }
                for (m in listOf(-1L, 1_800_000L, 3_600_000L, 7_200_000L, 14_400_000L)) item("z$m") {
                    PanelChoice(mins(m), selected = sleep == m || (m < 0 && sleep <= 0)) { sys.sleepAfterMs = m; tick++ }
                }
                item { PanelItem("The TV's own screensaver settings", icon = Icons.Rounded.OpenInNew) { sys.openSystem(SystemPages.first { it.label.startsWith("Screensaver") }) } }
            }
        }

        "library" -> SidePanel("Library", (feed.libraryProblem ?: "From AllMedia, as ${feed.userName ?: "its own sign-in"}.") + " Titles open in AllMedia, which keeps the servers and everyone's sign-ins.", key = key) {
            item { PanelItem("Who's watching?", feed.userName ?: "AllMedia's own sign-in", Icons.Rounded.People, chevron = true) { go("account") } }
            item { PanelItem("Open AllMedia", "To sign in or add people", Icons.Rounded.OpenInNew) { app.apps.launch(ALLMEDIA); ui.closePanel() } }
            item { PanelItem("Reload everything now", icon = Icons.Rounded.SwapHoriz) { app.feed.refresh(); ui.closePanel() } }
            if (feed.problems.isNotEmpty()) {
                item { PanelHeading("Last time") }
                for (p in feed.problems) item(p) { PanelNote(p) }
            }
        }

        "popular" -> SidePanel("Popular titles", "Popular this week, from TMDB, shown only where it's in your library. Paid services' titles stay out unless you let them in.", key = key) {
            item { PanelItem(if (cfg.popular.tmdbKey.isBlank()) "Add a TMDB key" else "Change the TMDB key", "On the phone setup page", Icons.Rounded.PhoneAndroid, chevron = true) { go("phone") } }
            item { PanelToggle("Include titles you don't have", "They open Google TV's search, which may lead to a paid service", cfg.popular.includeUnowned) { on -> store.update { it.copy(popular = it.popular.copy(includeUnowned = on)) } } }
            item { PanelHeading("Country") }
            for (r in listOf("GB", "US", "IE", "AU", "CA", "DE", "FR")) item(r) { PanelChoice(r, selected = cfg.popular.region == r) { store.update { it.copy(popular = it.popular.copy(region = r)) } } }
        }

        "phone" -> PhonePage(key)

        "home" -> {
            var tick by remember { mutableIntStateOf(0) }
            val on = remember(tick) { sys.homeServiceOn }
            val context = LocalContext.current
            LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
            SidePanel("Home button", "Google TV keeps the Home button for itself. The Home button service takes it back: a press opens this home screen, a long press opens these settings.", key = key) {
                item { PanelToggle("Home button opens ${cfg.look.brand.ifBlank { "myTV" }}", if (on) "The service is on" else "The service is off", cfg.overrideHome && on) { want ->
                    store.update { it.copy(overrideHome = want) }
                    if (want && !on && !sys.setHomeService(true)) sys.openSystem(SystemPages.first { it.label == "Accessibility" })
                    tick++
                } }
                item {
                    PanelItem("Make this the default home app", "So Back from an app comes here", Icons.Rounded.Home) {
                        val rm = context.getSystemService(RoleManager::class.java)
                        runCatching { (context as android.app.Activity).startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1) }
                    }
                }
                item { PanelItem("Open Google TV's home screen", "Without switching anything off", Icons.Rounded.Tv) {
                    app.apps.start(android.content.Intent(android.content.Intent.ACTION_MAIN).setComponent(ComponentName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity")))
                } }
            }
        }

        "system" -> SidePanel("Android settings", key = key) {
            for (p in SystemPages) item(p.action) { PanelItem(p.label, icon = Icons.Rounded.OpenInNew) { sys.openSystem(p) } }
        }

        "permissions" -> SidePanel("Permissions", "Granted once over adb from a computer on the same network (see the README).", key = key) {
            fun state(on: Boolean) = if (on) "Granted" else "Not granted"
            item { PanelItem("Read other apps' channels", state(sys.canReadListings) + " · READ_TV_LISTINGS", Icons.Rounded.Visibility) {} }
            item { PanelItem("Change system settings", state(sys.canWriteSecure) + " · WRITE_SECURE_SETTINGS: screensaver, Home button", Icons.Rounded.Settings) {} }
            item { PanelItem("Install apps", state(sys.canInstall), Icons.Rounded.Shop) { if (!sys.canInstall) sys.openInstallPermission() } }
            if (!sys.canReadListings || !sys.canWriteSecure) item {
                PanelNote("adb shell pm grant com.vwillcox.mytv android.permission.READ_TV_LISTINGS\nadb shell pm grant com.vwillcox.mytv android.permission.WRITE_SECURE_SETTINGS")
            }
        }

        else -> ui.closePanel()
    }
}

private val Swatches = listOf("Midnight" to 0xFF15202BL, "Plum" to 0xFF2A1B3DL, "Forest" to 0xFF132A1EL, "Ember" to 0xFF2D1A12L, "Slate" to 0xFF22262BL, "Ocean" to 0xFF0E2A3BL)
private val Accents = listOf("Google blue" to 0xFFD3E3FDL, "Mint" to 0xFFC4EED0L, "Peach" to 0xFFFFDBCBL, "Lilac" to 0xFFE9DDFFL, "Lemon" to 0xFFF6EDA6L, "White" to 0xFFFFFFFFL)

/** The TV's own tools, left out of Your apps to start with (they're still in All apps). */
private val Utilities = setOf("com.android.tv.settings", "com.android.vending", "com.google.android.play.games", "com.google.android.videos")

/** Your apps before the owner has arranged them: their own (sideloaded) apps, then the rest, by name. */
fun defaultFavourites(all: List<TvApp>, cfg: Config): List<String> =
    all.filter { it.leanback && it.pkg !in cfg.hidden && it.pkg !in Utilities }.sortedWith(compareBy({ !it.own }, { it.label.lowercase() })).map { it.pkg }

@Composable
private fun PhonePage(key: String) {
    val app = LocalContext.current.app
    val server = remember { PhoneSetup.start(app) }
    DisposableEffect(server) { onDispose { server?.stop() } }
    val activity by (server?.activity ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val installing by app.installer.status.collectAsState()
    val address = server?.address
    SidePanel("Set up from your phone", "Scan the code with a phone on the same Wi-Fi, or type the address into a computer. It works while this page is open.", key = key) {
        item {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (address != null) {
                    val qr = remember(address) { PhoneSetup.qr(address).asImageBitmap() }
                    Image(qr, "QR code", Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
                    Text(address, style = Type.item, color = LocalTones.current.text, modifier = Modifier.padding(top = 12.dp))
                } else Text("The TV isn't on a network.", style = Type.body, color = LocalTones.current.dim)
            }
        }
        installing?.let { s -> item { PanelNote(s) } }
        for (a in activity) item(a) { PanelNote("✓ $a") }
        item { PanelItem("Done", icon = Icons.Rounded.Done) { } }
    }
}

@Composable
private fun CardMenu(ui: HomeUi, card: MediaCard, key: String) {
    val app = LocalContext.current.app
    // An app it came from, rather than TMDB or this screen's own getting-started cards.
    val fromApp = card.source != "tmdb" && card.open !is Open.Settings
    SidePanel(card.title, card.subtitle, key = key) {
        val o = card.open
        if (o is Open.AllMedia) {
            item { PanelItem(if (card.badge == "RESUME") "Resume" else "Play", "In ${card.sourceLabel}", Icons.Rounded.PlayArrow) { app.system.openAllMedia(o.play); ui.closePanel() } }
            item { PanelItem("Details", "In ${card.sourceLabel}", Icons.Rounded.Info) { app.system.openAllMedia(o.page); ui.closePanel() } }
            // On the app's scope: closing the panel would end this one's before the answer came.
            item { PanelItem("Mark as watched", icon = Icons.Rounded.Done) { app.scope.launch { app.feed.markPlayed(card) }; ui.closePanel() } }
        } else {
            item { PanelItem("Open", "In ${card.sourceLabel}", Icons.Rounded.PlayArrow) { app.system.open(card); ui.closePanel() } }
        }
        if (fromApp) item { PanelItem("Open ${card.sourceLabel}", icon = Icons.Rounded.Apps) { app.apps.launch(card.source); ui.closePanel() } }
        item { PanelItem("Search everywhere for it", icon = Icons.Rounded.Search) { app.system.search(card.title); ui.closePanel() } }
        item { PanelItem("Remove from the home screen", "It won't come back unless you clear removed titles", Icons.Rounded.VisibilityOff) {
            app.store.update { it.copy(dismissed = it.dismissed + card.key) }; ui.closePanel()
        } }
        if (fromApp) item { PanelItem("Stop ${card.sourceLabel} adding titles", icon = Icons.Rounded.Lock) {
            app.store.update { it.copy(sources = it.sources + (card.source to SourcePrefs.Off)) }; ui.closePanel()
        } }
    }
}

@Composable
private fun AppMenu(ui: HomeUi, cfg: Config, a: TvApp, key: String) {
    val app = LocalContext.current.app
    val store = app.store
    val favourite = a.pkg in cfg.favourites
    SidePanel(a.label, a.pkg, key = key) {
        item { PanelItem("Open", icon = Icons.Rounded.PlayArrow) { app.apps.launch(a); ui.closePanel() } }
        if (favourite) {
            item { PanelItem("Move", "Left and right move it, OK puts it down", Icons.Rounded.SwapHoriz) { ui.moving = a.pkg; ui.closePanel() } }
            item { PanelItem("Remove from Your apps", icon = Icons.Rounded.Delete) { store.update { it.copy(favourites = it.favourites - a.pkg) }; ui.closePanel() } }
        } else {
            item { PanelItem("Add to Your apps", icon = Icons.Rounded.Add) { store.update { it.copy(favourites = it.favourites + a.pkg, hidden = it.hidden - a.pkg) }; ui.closePanel() } }
        }
        item { PanelItem(if (a.pkg in cfg.hidden) "Show in All apps" else "Hide", icon = Icons.Rounded.VisibilityOff) {
            store.update { if (a.pkg in it.hidden) it.copy(hidden = it.hidden - a.pkg) else it.copy(hidden = it.hidden + a.pkg, favourites = it.favourites - a.pkg) }; ui.closePanel()
        } }
        item { PanelItem("What it may show", cfg.source(a.pkg, app.apps.isOwn(a.pkg)).summary(), Icons.Rounded.Movie, chevron = true) { ui.open(Panel.Page("source:${a.pkg}")) } }
        item { PanelItem("App info", icon = Icons.Rounded.Info) { app.apps.info(a.pkg) } }
        if (a.fromPlay) item { PanelItem("Play Store page", icon = Icons.Rounded.Shop) { app.apps.store(a.pkg) } }
        if (!a.system) item { PanelItem("Uninstall", icon = Icons.Rounded.Delete) { app.apps.uninstall(a.pkg); ui.closePanel() } }
    }
}
