package com.vwillcox.mytv.data

import android.util.Log
import com.vwillcox.mytv.system.SystemControl.Companion.ALLMEDIA
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class TabFeed(val picks: List<MediaCard> = emptyList(), val rows: List<Row> = emptyList())

data class FeedState(
    val tabs: Map<Tab, TabFeed> = emptyMap(),
    val loading: Boolean = true,
    /** Who AllMedia can play as, for "Who's watching?". */
    val people: List<Person> = emptyList(),
    /** Who is watching: the chosen person, or AllMedia's own sign-in. */
    val userName: String? = null,
    val avatar: String? = null,
    /** Why the library can't be read, or null when it can. */
    val libraryProblem: String? = null,
    /** Why a source gave nothing this time, for the settings screen. */
    val problems: List<String> = emptyList(),
    /** Whether "popular" came from TMDB or, without a key, from the library's ratings. */
    val popularFromTmdb: Boolean = false,
    /** Apps that have published Continue watching or channels to the TV, whether or not they're allowed here. */
    val publishers: List<String> = emptyList(),
    /** The channels each app publishes, by name, whether or not they're shown. */
    val channelsOf: Map<String, List<String>> = emptyMap(),
    val updatedAt: Long = 0,
)

/** How one app's channel is named in [Config.hiddenChannels]. Names, unlike ids, survive the app publishing again. */
fun channelKey(pkg: String, name: String) = "$pkg/$name"

/** What a source may do, with the default for one the owner hasn't decided on: their own apps on, bought ones off. */
fun Config.source(pkg: String, own: Boolean): SourcePrefs = sources[pkg] ?: if (own || pkg == ALLMEDIA) SourcePrefs.On else SourcePrefs.Off

/**
 * Builds every tab from the library AllMedia gives, TMDB's popular lists and what apps published to the TV provider,
 * keeping to the sources the owner allowed.
 */
class Feed(
    private val store: ConfigStore,
    private val library: Library,
    private val tmdb: Tmdb,
    private val listings: TvListings,
    private val apps: AppRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state

    private var job: Job? = null
    /** Set when AllMedia said its answers changed while the home screen wasn't showing. */
    private var stale = false
    /** Whether the home screen is in front, so a change from AllMedia is fetched now rather than on coming back. */
    var visible = false

    fun refresh() {
        stale = false
        job?.cancel()
        job = scope.launch {
            _state.value = _state.value.copy(loading = true)
            _state.value = try {
                build()
            } catch (e: CancellationException) {
                // A newer refresh took over; what it builds is what shows.
                throw e
            } catch (e: Exception) {
                Log.w("myTvFeed", "Building the home screen failed", e)
                _state.value.copy(loading = false, problems = listOf(e.friendly()))
            }
        }
    }

    /** Refreshes unless the last one is recent and nothing has changed since: for coming back to the home screen. */
    fun refreshIfOlderThan(ms: Long) { if (stale || System.currentTimeMillis() - _state.value.updatedAt > ms) refresh() }

    /** AllMedia's answers changed: something was played or marked, or someone was added. */
    fun libraryChanged() { if (visible) refresh() else stale = true }

    private val label get() = apps.label(ALLMEDIA)
    private val person get() = store.value.watching?.id

    private suspend fun build(): FeedState = coroutineScope {
        apps.ready()
        val cfg = store.value
        val problems = mutableListOf<String>()
        val libraryProblem = library.problem
        // Which of Continue watching, Top picks and rows the library may fill.
        val allowed = cfg.source(ALLMEDIA, true)
        val lib = libraryProblem == null && allowed.any
        val person = cfg.watching?.id
        val label = label

        fun set(name: String, types: String? = null, limit: Int = 30): Deferred<List<MediaCard>> = async {
            if (!lib) emptyList()
            else runCatching { library.items(name, person, label, listOfNotNull(types?.let { "types" to it }).toMap(), limit) }
                .getOrElse { problems += "AllMedia ($name): ${it.friendly()}"; emptyList() }
        }
        fun genres(types: String): Deferred<List<Genre>> = async {
            if (!lib) emptyList() else runCatching { library.genres(types, person) }.getOrElse { problems += "AllMedia (genres): ${it.friendly()}"; emptyList() }
        }

        val resume = set("resume")
        val nextUp = set("nextup")
        val latestMovies = set("latest", "Movie")
        val latestShows = set("latest", "Series")
        val latest = set("latest")
        val unseenMovies = set("unwatched", "Movie")
        val unseenShows = set("unwatched", "Series")
        val topMovies = set("toprated", "Movie")
        val topShows = set("toprated", "Series")
        val movieGenres = genres("Movie")
        val showGenres = genres("Series")
        val people = async { if (libraryProblem != null) emptyList() else runCatching { library.people() }.getOrElse { problems += "AllMedia (people): ${it.friendly()}"; emptyList() } }
        val listed = async { listings.load(apps::label) }
        val popular = async { popular(cfg, lib, person, label, problems, topMovies, topShows) }

        val resumeCards = if (allowed.continueWatching) resume.await() else emptyList()
        val nextCards = nextUp.await()
        val watchingCards = resumeCards + if (allowed.continueWatching) nextCards else emptyList()
        val (popMovies, popShows, fromTmdb) = popular.await()
        val tv = listed.await()

        // Apps' own Continue watching, from the ones allowed to add to it. A title AllMedia already gave is left out.
        val known = watchingCards.flatMap { it.ids }.toSet()
        val knownTitles = watchingCards.map { it.title.lowercase() + "|" + it.subtitle?.substringAfter(" • ").orEmpty() }.toSet()
        val appContinue = tv.watchNext
            .filter { cfg.source(it.source, apps.isOwn(it.source)).continueWatching }
            .filter { c -> c.ids.none { it in known } && (c.title.lowercase() + "|" + c.subtitle?.substringAfter(" • ").orEmpty()) !in knownTitles }
        val continueAll = (watchingCards + appContinue)
            .filter { it.key !in cfg.dismissed }
            .distinctBy { it.key }
            .sortedByDescending { it.engaged }

        // An allowed app's channels, less any the owner switched off one by one (YouTube's Trending, say).
        val shownChannels = tv.channels.filter { channelKey(it.pkg, it.name) !in cfg.hiddenChannels }
        val channels = shownChannels.filter { cfg.source(it.pkg, apps.isOwn(it.pkg)).rows }
        val pickChannels = shownChannels.filter { cfg.source(it.pkg, apps.isOwn(it.pkg)).topPicks }
        val fromApps = channels.flatMap { it.programs }.distinctBy { it.key }

        val latestMovieCards = latestMovies.await()
        val latestShowCards = latestShows.await()
        val latestAll = latest.await()
        val unseenMovieCards = unseenMovies.await()
        val unseenShowCards = unseenShows.await()
        val topMovieCards = topMovies.await()
        val topShowCards = topShows.await()

        fun isMovie(c: MediaCard) = c.kind == Kind.MOVIE
        fun isShow(c: MediaCard) = c.kind == Kind.SHOW || c.kind == Kind.EPISODE
        val popTitle = if (fromTmdb) "Popular in your library" else "Highly rated in your library"

        fun appPicks(filter: (MediaCard) -> Boolean) = pickChannels.map { ch -> ch.programs.filter(filter).take(4) }
        fun pick(cards: List<MediaCard>, n: Int) = if (allowed.topPicks) cards.take(n) else emptyList()
        fun rowOf(cards: List<MediaCard>) = if (allowed.rows) cards else emptyList()

        // Until AllMedia gives a library, the banner says why and what else can fill the screen.
        val starters = if (libraryProblem == null) emptyList() else listOf(
            MediaCard("start:library", "Your library comes from AllMedia", "Settings → Library",
                overview = libraryProblem, meta = "Get started", source = ALLMEDIA, sourceLabel = "myTV", open = Open.Settings("library")),
            MediaCard("start:sources", "Choose your media sources", "Settings → Media sources",
                overview = "Pick exactly which apps may add to Continue watching, Top picks and rows. Paid services start switched off.",
                meta = "Get started", source = ALLMEDIA, sourceLabel = "myTV", open = Open.Settings("sources")),
        )
        val home = TabFeed(
            picks = starters + mix(listOf(pick(popMovies, 6), pick(popShows, 6), pick(nextCards, 3), pick(latestAll, 6)) + appPicks { true }, cfg),
            rows = cfg.rowsFor(Tab.HOME).filter { it.shown }.flatMap { pref ->
                when (pref.kind) {
                    RowKind.APPS -> listOf(Row("apps", "Your apps", RowStyle.APPS))
                    RowKind.CONTINUE -> listOf(Row("continue", "Continue watching", RowStyle.WIDE, continueAll))
                    RowKind.NEXT_UP -> listOf(Row("nextup", "Next episodes", RowStyle.WIDE, rowOf(nextCards)))
                    RowKind.POPULAR -> listOf(Row("popular", popTitle, RowStyle.WIDE, rowOf(interleave(popMovies, popShows))))
                    RowKind.LATEST -> listOf(Row("latest", "Recently added", RowStyle.WIDE, rowOf(latestAll)))
                    RowKind.FROM_APPS -> listOf(Row("fromapps", "From your apps", RowStyle.WIDE, fromApps))
                    RowKind.CHANNELS -> channels.map { ch ->
                        val app = apps.label(ch.pkg)
                        Row("ch:${ch.id}", if (ch.name.equals(app, true)) app else "${ch.name} · $app", RowStyle.WIDE, ch.programs)
                    }
                    else -> emptyList()
                }
            },
        )

        fun genreRow(genres: List<Genre>, types: String) = Row("genres", "Browse by genre", RowStyle.TILES, genres.filter { allowed.rows }.map { g ->
            MediaCard(key = "genre:$types:${g.name}", title = g.name, image = g.image, source = ALLMEDIA, sourceLabel = label, open = Open.Browse(g.name, types, g.name))
        })

        val movies = TabFeed(
            picks = mix(listOf(pick(popMovies, 10), pick(latestMovieCards, 6)) + appPicks(::isMovie), cfg),
            rows = cfg.rowsFor(Tab.MOVIES).filter { it.shown }.mapNotNull { pref ->
                when (pref.kind) {
                    // Without TMDB, popular is the highest rated, which has its own row here.
                    RowKind.POPULAR -> Row("popular", "Popular films in your library", RowStyle.WIDE, rowOf(if (fromTmdb) popMovies else emptyList()))
                    RowKind.CONTINUE -> Row("continue", "Continue watching", RowStyle.WIDE, continueAll.filter(::isMovie))
                    RowKind.LATEST -> Row("latest", "Recently added films", RowStyle.WIDE, rowOf(latestMovieCards))
                    RowKind.UNWATCHED -> Row("unseen", "Films you haven't seen", RowStyle.WIDE, rowOf(unseenMovieCards))
                    RowKind.TOP_RATED -> Row("top", "Highest rated films", RowStyle.WIDE, rowOf(topMovieCards))
                    RowKind.FROM_APPS -> Row("fromapps", "Films from your apps", RowStyle.WIDE, fromApps.filter(::isMovie))
                    RowKind.GENRES -> genreRow(movieGenres.await(), "Movie")
                    else -> null
                }
            },
        )

        val shows = TabFeed(
            picks = mix(listOf(pick(popShows, 10), pick(nextCards, 4), pick(latestShowCards, 6)) + appPicks(::isShow), cfg),
            rows = cfg.rowsFor(Tab.SHOWS).filter { it.shown }.mapNotNull { pref ->
                when (pref.kind) {
                    RowKind.NEXT_UP -> Row("nextup", "Next episodes", RowStyle.WIDE, rowOf(nextCards))
                    RowKind.POPULAR -> Row("popular", "Popular shows in your library", RowStyle.WIDE, rowOf(if (fromTmdb) popShows else emptyList()))
                    RowKind.CONTINUE -> Row("continue", "Continue watching", RowStyle.WIDE, continueAll.filter(::isShow))
                    RowKind.LATEST -> Row("latest", "New episodes and shows", RowStyle.WIDE, rowOf(latestShowCards))
                    RowKind.UNWATCHED -> Row("unseen", "Shows you haven't started", RowStyle.WIDE, rowOf(unseenShowCards))
                    RowKind.TOP_RATED -> Row("top", "Highest rated shows", RowStyle.WIDE, rowOf(topShowCards))
                    RowKind.FROM_APPS -> Row("fromapps", "Shows from your apps", RowStyle.WIDE, fromApps.filter(::isShow))
                    RowKind.GENRES -> genreRow(showGenres.await(), "Series")
                    else -> null
                }
            },
        )

        val everyone = people.await()
        // Someone AllMedia no longer has is watched as AllMedia's own sign-in, as AllMedia does.
        val me = everyone.firstOrNull { it.id == person } ?: everyone.firstOrNull { it.own }
        FeedState(
            tabs = mapOf(Tab.HOME to home, Tab.MOVIES to movies, Tab.SHOWS to shows),
            loading = false,
            people = everyone,
            userName = me?.name ?: cfg.watching?.name,
            avatar = me?.image,
            libraryProblem = libraryProblem,
            problems = problems.distinct(),
            popularFromTmdb = fromTmdb,
            publishers = (tv.watchNext.map { it.source } + tv.channels.map { it.pkg }).distinct(),
            channelsOf = tv.channels.groupBy({ it.pkg }, { it.name }).mapValues { (_, names) -> names.distinct() },
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** Takes one from each list in turn, so no one source fills Top picks; repeats and dismissed cards are dropped. */
    private fun mix(lists: List<List<MediaCard>>, cfg: Config, max: Int = 20): List<MediaCard> {
        val out = LinkedHashMap<String, MediaCard>()
        val seenTitles = mutableSetOf<String>()
        var i = 0
        while (out.size < max && lists.any { i < it.size }) {
            for (l in lists) {
                val c = l.getOrNull(i) ?: continue
                if (c.key in cfg.dismissed || !seenTitles.add(c.title.lowercase())) continue
                out[c.key] = c
            }
            i++
        }
        return out.values.take(max)
    }

    private fun interleave(a: List<MediaCard>, b: List<MediaCard>): List<MediaCard> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    /**
     * Popular films and shows that are in the library, most popular first. From TMDB with a key, matched to the library
     * by AllMedia; without one, the library's highest rated stand in. Returns (films, shows, from TMDB).
     */
    private suspend fun popular(
        cfg: Config, lib: Boolean, person: String?, label: String, problems: MutableList<String>,
        topMovies: Deferred<List<MediaCard>>, topShows: Deferred<List<MediaCard>>,
    ): Triple<List<MediaCard>, List<MediaCard>, Boolean> {
        if (!lib) return Triple(emptyList(), emptyList(), false)
        val key = cfg.popular.tmdbKey
        if (key.isBlank()) return Triple(topMovies.await(), topShows.await(), false)
        return coroutineScope {
            val films = async { runCatching { tmdb.popular(key, "movie", cfg.popular.region) }.getOrElse { problems += "TMDB: ${it.friendly()}"; emptyList() } }
            val series = async { runCatching { tmdb.popular(key, "tv", cfg.popular.region) }.getOrElse { emptyList() } }
            suspend fun owned(titles: List<TmdbTitle>, show: Boolean): List<MediaCard> {
                val prefix = if (show) "tv" else "movie"
                val wanted = titles.map { "$prefix:${it.id}" }.distinct().take(60)
                val mine = if (wanted.isEmpty()) emptyList() else runCatching {
                    library.items("tmdb", person, label, mapOf("tmdb" to wanted.joinToString(","), "types" to if (show) "Series" else "Movie"), limit = 40)
                }.getOrElse { problems += "AllMedia (popular): ${it.friendly()}"; emptyList() }
                if (!cfg.popular.includeUnowned) return mine
                val have = mine.flatMap { it.ids }.toSet()
                return mine + titles.filter { "tmdb:$prefix:${it.id}" !in have }.take(15).map { unowned(it, show) }
            }
            Triple(owned(films.await(), false), owned(series.await(), true), true)
        }
    }

    private fun unowned(t: TmdbTitle, show: Boolean) = MediaCard(
        key = "tmdb:${if (show) "tv" else "movie"}:${t.id}",
        title = t.label,
        subtitle = "Not in your library",
        image = t.backdrop("w780"),
        backdrop = t.backdrop(),
        overview = t.overview,
        meta = listOfNotNull(t.year?.toString(), "Not in your library").joinToString(" · "),
        source = "tmdb",
        sourceLabel = "TMDB",
        kind = if (show) Kind.SHOW else Kind.MOVIE,
        open = Open.Search(t.label),
    )

    // ---- On demand --------------------------------------------------------------------------

    suspend fun browse(types: String, genre: String?): List<MediaCard> =
        library.items("genre", person, label, mapOf("types" to types, "genre" to genre.orEmpty()), limit = 400)

    suspend fun search(query: String): List<MediaCard> =
        if (query.isBlank() || library.problem != null) emptyList()
        else library.items("search", person, label, mapOf("q" to query, "types" to "Movie,Series"), limit = 40)

    /** Titles with big pictures, for the screensaver. */
    suspend fun backdrops(): List<MediaCard> = library.items("random", person, label, mapOf("types" to "Movie,Series"), limit = 80)

    suspend fun markPlayed(card: MediaCard): Boolean {
        val o = card.open as? Open.AllMedia ?: return false
        return library.markPlayed(o.key, person).also { if (it) refresh() }
    }

    companion object {
        /** A steady colour per name, for tiles without pictures. */
        fun tint(name: String): Long {
            val palette = listOf(0xFF3B4A6B, 0xFF5B3A6E, 0xFF2F5D50, 0xFF6B4A2F, 0xFF6B2F3E, 0xFF2F4F6B, 0xFF4A5B2F, 0xFF3F3F6B)
            return palette[name.hashCode().absoluteValue % palette.size]
        }
    }
}
