package com.vwillcox.mytv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val LenientJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Who is watching: one of AllMedia's people, chosen on the home screen. AllMedia plays as them, and the home screen
 * shows their Continue watching and picks. [name] is kept for when AllMedia can't be asked.
 */
@Serializable
data class Watcher(val id: String = "", val name: String = "")

/** What one source may put on the home screen. A source is an app, by package; AllMedia's is the library. */
@Serializable
data class SourcePrefs(
    /** Its part-watched and next titles in Continue watching. */
    val continueWatching: Boolean = false,
    /** Its titles among Top picks, at the top of each tab. */
    val topPicks: Boolean = false,
    /** A row of its own for each channel it publishes. */
    val rows: Boolean = false,
) {
    val any get() = continueWatching || topPicks || rows

    companion object {
        val On = SourcePrefs(continueWatching = true, topPicks = true, rows = true)
        val Off = SourcePrefs()
    }
}

enum class Tab(val label: String) { HOME("Home"), MOVIES("Movies"), SHOWS("Shows"), APPS("Apps") }

/** The rows a tab can be built from. Which tabs offer which is [RowKind.tabs]. */
enum class RowKind(val label: String, val tabs: Set<Tab>) {
    APPS("Your apps", setOf(Tab.HOME)),
    CONTINUE("Continue watching", setOf(Tab.HOME, Tab.MOVIES, Tab.SHOWS)),
    NEXT_UP("Next episodes", setOf(Tab.HOME, Tab.SHOWS)),
    POPULAR("Popular in your library", setOf(Tab.HOME, Tab.MOVIES, Tab.SHOWS)),
    LATEST("Recently added", setOf(Tab.HOME, Tab.MOVIES, Tab.SHOWS)),
    UNWATCHED("Not seen yet", setOf(Tab.MOVIES, Tab.SHOWS)),
    TOP_RATED("Highest rated", setOf(Tab.MOVIES, Tab.SHOWS)),
    GENRES("Browse by genre", setOf(Tab.MOVIES, Tab.SHOWS)),
    FROM_APPS("From your apps", setOf(Tab.HOME, Tab.MOVIES, Tab.SHOWS)),
    CHANNELS("App channels", setOf(Tab.HOME)),
}

@Serializable
data class RowPref(val kind: RowKind, val shown: Boolean = true)

val DefaultRows: Map<Tab, List<RowPref>> = mapOf(
    // From your apps would repeat the channel rows on Home, so it starts hidden there.
    Tab.HOME to listOf(RowKind.APPS, RowKind.CONTINUE, RowKind.POPULAR, RowKind.LATEST, RowKind.CHANNELS, RowKind.NEXT_UP, RowKind.FROM_APPS)
        .map { RowPref(it, shown = it != RowKind.NEXT_UP && it != RowKind.FROM_APPS) },
    Tab.MOVIES to listOf(RowKind.POPULAR, RowKind.CONTINUE, RowKind.LATEST, RowKind.UNWATCHED, RowKind.TOP_RATED, RowKind.FROM_APPS, RowKind.GENRES)
        .map { RowPref(it) },
    Tab.SHOWS to listOf(RowKind.NEXT_UP, RowKind.POPULAR, RowKind.CONTINUE, RowKind.LATEST, RowKind.UNWATCHED, RowKind.TOP_RATED, RowKind.FROM_APPS, RowKind.GENRES)
        .map { RowPref(it, shown = it != RowKind.CONTINUE) },
)

enum class AppStyle(val label: String) { CIRCLE("Round icons"), BANNER("Banners") }

enum class BackgroundMode(val label: String) {
    DARK("Dark"),
    ARTWORK("Artwork of what's focused"),
    COLOUR("A colour"),
    PHOTO("A photo"),
}

@Serializable
data class Look(
    val background: BackgroundMode = BackgroundMode.DARK,
    val colour: Long = 0xFF15202BL,
    /** A file in the app's own storage (sent from the phone page) or a web address. */
    val photo: String = "",
    /** How far the background is darkened under the rows, 0 to 1. */
    val dim: Float = 0.6f,
    /** The selected tab's pill and other highlights. */
    val accent: Long = 0xFFD3E3FDL,
    val appStyle: AppStyle = AppStyle.CIRCLE,
    val clock24: Boolean = true,
    /** Shown after the clock where Google TV puts its own name. */
    val brand: String = "myTV",
    /** How long each title stays in the banner before the next, while nothing in Top picks has focus. */
    val heroSeconds: Int = 9,
)

@Serializable
data class Popularity(
    /** A TMDB API key (v3) or read access token (v4). Without one, "popular" falls back to the library's own ratings. */
    val tmdbKey: String = "",
    val region: String = "GB",
    /**
     * Popular titles that aren't in the library, which only a streaming service (most likely a paid one) can
     * play. Off unless asked for.
     */
    val includeUnowned: Boolean = false,
)

@Serializable
data class Config(
    val deviceId: String = "",
    /** Who is watching, or null for AllMedia's own sign-in. */
    val watching: Watcher? = null,
    /** Per source; a source with no entry gets [defaultSource]. */
    val sources: Map<String, SourcePrefs> = emptyMap(),
    /** Packages in Your apps, in order. Empty until first filled from what's installed. */
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val rows: Map<Tab, List<RowPref>> = DefaultRows,
    val look: Look = Look(),
    val popular: Popularity = Popularity(),
    /** Takes the Home button from Google TV, when the Home button service is on. */
    val overrideHome: Boolean = true,
    /** Cards taken off the home screen by hand, by [MediaCard.key]. */
    val dismissed: Set<String> = emptySet(),
    /** Channels of allowed apps that are kept off the home screen anyway, by [channelKey]. */
    val hiddenChannels: Set<String> = emptySet(),
) {
    fun rowsFor(tab: Tab): List<RowPref> {
        // Kinds added in a later version join the end of a saved order.
        val saved = rows[tab].orEmpty().filter { tab in it.kind.tabs }
        val missing = DefaultRows[tab].orEmpty().filter { d -> saved.none { it.kind == d.kind } }
        return saved + missing
    }
}

private val Context.dataStore by preferencesDataStore("config")

class ConfigStore(private val context: Context) {
    private val key = stringPreferencesKey("config")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val state: MutableStateFlow<Config>

    val config: StateFlow<Config> get() = state
    val value: Config get() = state.value

    init {
        // Read once synchronously so the first frame is drawn with real settings.
        val stored = runBlocking { context.dataStore.data.first()[key] }
        var cfg = stored?.let { runCatching { LenientJson.decodeFromString<Config>(it) }.getOrNull() } ?: Config()
        if (cfg.deviceId.isBlank()) cfg = cfg.copy(deviceId = UUID.randomUUID().toString())
        state = MutableStateFlow(cfg)
        if (stored == null) persist()
    }

    /** Applied with compare-and-set, so a change made in the background isn't lost under one made on screen. */
    fun update(block: (Config) -> Config) {
        var changed = false
        state.update { old -> block(old).also { changed = it != old } }
        if (changed) persist()
    }

    private fun persist() {
        scope.launch {
            mutex.withLock { context.dataStore.edit { it[key] = LenientJson.encodeToString(Config.serializer(), state.value) } }
        }
    }
}
