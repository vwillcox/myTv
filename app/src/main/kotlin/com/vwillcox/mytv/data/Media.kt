package com.vwillcox.mytv.data

enum class Kind { MOVIE, SHOW, EPISODE, VIDEO, OTHER }

/** What choosing a card does. */
sealed interface Open {
    /** An intent an app published with the title (TV provider rows carry one). */
    data class Link(val intentUri: String, val pkg: String) : Open
    /**
     * A title in the library, opened in AllMedia through the links it gave: [play] plays or resumes it, [page] shows
     * its page. Choosing the card does the first when [playFirst]. [key] is AllMedia's id for marking it watched.
     */
    data class AllMedia(val play: String, val page: String, val key: String, val playFirst: Boolean) : Open
    /** A popular title that isn't in the library: hand it to the TV's own search. */
    data class Search(val query: String) : Open
    /** One of the home screen's own settings pages, for the getting-started cards. */
    data class Settings(val page: String) : Open
    /** A genre or other set, shown as a grid on its own screen. */
    data class Browse(val title: String, val types: String, val genre: String?) : Open
}

data class MediaCard(
    /** Stable across reloads, for focus memory and for dismissing. */
    val key: String,
    val title: String,
    /** The line under the title: "AllMedia • S7 E2 • A Roulette Wheel". */
    val subtitle: String? = null,
    /** 16:9 picture for the card. */
    val image: String? = null,
    /** Large picture for the banner and background. */
    val backdrop: String? = null,
    /** The title drawn as a logo, for the banner. */
    val logo: String? = null,
    val overview: String? = null,
    /** "2024 · 12A · 1 h 42 m · Comedy". */
    val meta: String? = null,
    val progress: Float? = null,
    /** RESUME, WATCH, NEW. */
    val badge: String? = null,
    /** The package of the app it's from (AllMedia for the library), or "tmdb" for a title nobody here has. */
    val source: String,
    val sourceLabel: String,
    val kind: Kind = Kind.OTHER,
    val open: Open,
    /** For ordering Continue watching: when it was last watched, or the best guess. */
    val engaged: Long = 0,
    /** Ids this title is known by elsewhere (AllMedia's key, TMDB id), for taking out the same title from two sources. */
    val ids: Set<String> = emptySet(),
)

/** A row on a tab. */
data class Row(
    val key: String,
    val title: String,
    val style: RowStyle,
    val cards: List<MediaCard> = emptyList(),
    /** Set on the apps rows instead of [cards]. */
    val apps: List<TvApp> = emptyList(),
)

enum class RowStyle { PICKS, WIDE, APPS, TILES }
