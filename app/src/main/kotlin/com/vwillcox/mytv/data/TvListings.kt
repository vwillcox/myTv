package com.vwillcox.mytv.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A system permission, so not in the SDK's Manifest constants; granted at runtime like any dangerous one. */
const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

/** A preview channel some app published: "YouTube: Recommended", "Twitch: Live channels". */
data class AppChannel(val id: Long, val pkg: String, val name: String, val programs: List<MediaCard>)

data class Listings(val watchNext: List<MediaCard> = emptyList(), val channels: List<AppChannel> = emptyList())

/**
 * Reads what apps have published to Android TV's own home screen system: Watch Next (part-watched and next
 * titles) and preview channels. Any app can publish there, sideloaded or not, which is how they get into
 * Continue watching and Top picks here.
 *
 * With READ_TV_LISTINGS the TV provider lets this app read every app's rows except those an app marked
 * unsearchable; without it, only its own (none). The provider refuses any query with a selection unless the
 * caller is the system, so everything is read and filtered here.
 */
class TvListings(private val context: Context) {
    private val base = Uri.parse("content://android.media.tv")

    val allowed get() = context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    suspend fun load(label: (String) -> String): Listings = withContext(Dispatchers.IO) {
        if (!allowed) return@withContext Listings()
        val watchNext = query("watch_next_program") { it.card(label, watchNext = true) }.filterNotNull().uniqueKeys()
        val programs = query("preview_program") { c -> c.card(label, watchNext = false)?.let { c.long("channel_id") to it } }
            .filterNotNull()
            .let { pairs -> pairs.map { it.first }.zip(pairs.map { it.second }.uniqueKeys()) }
            .groupBy({ it.first }, { it.second })
        val channels = query("channel") { c ->
            if (c.str("type") != "TYPE_PREVIEW") return@query null
            val id = c.long("_id")
            val list = programs[id].orEmpty()
            if (list.isEmpty()) return@query null
            val pkg = c.str("package_name").orEmpty()
            AppChannel(id, pkg, c.str("display_name").orEmpty().ifBlank { label(pkg) }, list)
        }
        Listings(watchNext, channels.filterNotNull())
    }

    /**
     * Some apps give every program the same internal id (Spotify gives all its recommendations one), but the cards in
     * a row need keys of their own or the row can't be drawn, so a repeat gets a count added.
     */
    private fun List<MediaCard>.uniqueKeys(): List<MediaCard> {
        val seen = HashMap<String, Int>()
        return map { c ->
            val n = seen.merge(c.key, 1, Int::plus)!!
            if (n == 1) c else c.copy(key = "${c.key}#$n")
        }
    }

    private fun <T> query(table: String, map: (Cursor) -> T?): List<T?> = runCatching {
        context.contentResolver.query(base.buildUpon().appendPath(table).build(), null, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(runCatching { map(c) }.getOrNull()) }
        }.orEmpty()
    }.onFailure { Log.w("myTvListings", "Reading $table failed: $it") }.getOrDefault(emptyList())

    private fun Cursor.card(label: (String) -> String, watchNext: Boolean): MediaCard? {
        val pkg = str("package_name") ?: return null
        val intent = str("intent_uri") ?: return null
        val title = str("title")?.takeIf { it.isNotBlank() } ?: return null
        if (int("browsable") == 0 && !watchNext) return null
        val type = int("type")
        val episodeTitle = str("episode_title")
        val season = str("season_display_number")
        val episode = str("episode_display_number")
        val duration = long("duration_millis")
        val position = long("last_playback_position_millis")
        val poster = str("poster_art_uri")
        val thumb = str("thumbnail_uri")
        // Portrait posters crop badly into a wide card; a thumbnail is usually wide.
        val portrait = int("poster_art_aspect_ratio") in setOf(4, 5)
        val image = if (portrait) thumb ?: poster else poster ?: thumb
        val tag = if (season != null && episode != null) "S$season E$episode" else null
        val app = label(pkg)
        val nextType = if (watchNext) int("watch_next_type") else -1
        return MediaCard(
            key = "tv:$pkg:${str("internal_provider_id") ?: long("_id")}",
            title = title,
            subtitle = listOfNotNull(app, tag, episodeTitle).joinToString(" • "),
            image = image,
            backdrop = thumb ?: poster,
            overview = str("short_description") ?: str("long_description"),
            meta = listOfNotNull(app, tag, str("release_date")?.take(4)).joinToString(" · "),
            progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
            badge = when (nextType) {
                0 -> "RESUME"
                1, 2 -> "WATCH"
                else -> null
            },
            source = pkg,
            sourceLabel = app,
            kind = when (type) {
                0 -> Kind.MOVIE
                1, 2 -> Kind.SHOW
                3 -> Kind.EPISODE
                4, 5 -> Kind.VIDEO
                else -> Kind.OTHER
            },
            open = Open.Link(intent, pkg),
            engaged = long("last_engagement_time_utc_millis"),
            ids = setOfNotNull(str("internal_provider_id"), str("content_id")).map { "$pkg:$it" }.toSet() +
                setOfNotNull(str("internal_provider_id")),
        )
    }

    private fun Cursor.str(name: String): String? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.long(name: String): Long = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
    private fun Cursor.int(name: String): Int = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) } ?: -1
}
