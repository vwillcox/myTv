package com.vwillcox.mytv.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.vwillcox.mytv.system.SystemControl.Companion.ALLMEDIA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Someone AllMedia can play as. [own] is AllMedia's own sign-in, which it uses when nobody is chosen here. */
data class Person(val id: String, val name: String, val image: String?, val own: Boolean)

data class Genre(val name: String, val image: String?)

/** Why the library can't be read, in words for the home screen. */
class LibraryException(message: String) : Exception(message)

/**
 * The library, from AllMedia. AllMedia keeps the servers and everyone's accounts; this app only asks it what there is
 * to watch, as whoever is chosen here, and opens titles in it through the links it gives. Readable only with
 * AllMedia's LIBRARY permission, which is signature-level, so this app must be signed with AllMedia's key.
 * The contract is in AllMedia's MYTV.md.
 */
class Library(private val context: Context) {
    private val resolver get() = context.contentResolver

    val allowed get() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Null when the library can be read, else why not. */
    val problem: String?
        get() = when {
            context.packageManager.resolveContentProvider(AUTHORITY, 0) == null -> "AllMedia isn't installed, or is a version without its library for the home screen."
            !allowed -> "AllMedia won't share its library: this app isn't signed with AllMedia's key."
            else -> null
        }

    suspend fun people(): List<Person> = query(ROOT.buildUpon().appendPath("people").build()) {
        Person(str("id") ?: return@query null, str("name").orEmpty(), str("image"), int("own") == 1)
    }

    /**
     * Titles in [set] (resume, nextup, latest, unwatched, toprated, genre, search, tmdb, random) as [person], as cards
     * labelled [label]. Choosing one plays it when it's part-watched or next, else opens its page in AllMedia.
     */
    suspend fun items(set: String, person: String?, label: String, params: Map<String, String> = emptyMap(), limit: Int = 30): List<MediaCard> {
        val uri = ROOT.buildUpon().appendPath("items").appendQueryParameter("set", set).appendQueryParameter("limit", "$limit")
            .apply { person?.let { appendQueryParameter("person", it) } }
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }
            .build()
        val watching = set == "resume" || set == "nextup"
        return query(uri) {
            val key = str("key") ?: return@query null
            val kind = runCatching { Kind.valueOf(str("kind").orEmpty()) }.getOrDefault(Kind.OTHER)
            val page = str("open") ?: return@query null
            val play = str("play") ?: page
            val tmdb = str("tmdb")
            MediaCard(
                key = key,
                title = str("title") ?: return@query null,
                subtitle = listOfNotNull(label, str("subtitle")).joinToString(" • "),
                image = str("image"),
                backdrop = str("backdrop"),
                logo = str("logo"),
                overview = str("overview"),
                meta = str("meta"),
                progress = double("progress")?.toFloat()?.takeIf { it > 0f },
                badge = str("badge"),
                source = ALLMEDIA,
                sourceLabel = label,
                kind = kind,
                open = Open.AllMedia(play, page, key, playFirst = watching || kind == Kind.EPISODE),
                engaged = long("engaged"),
                ids = setOfNotNull(key, tmdb?.let { "tmdb:$it" }),
            )
        }
    }

    suspend fun genres(types: String, person: String?): List<Genre> {
        val uri = ROOT.buildUpon().appendPath("genres").appendQueryParameter("types", types)
            .apply { person?.let { appendQueryParameter("person", it) } }.build()
        return query(uri) { Genre(str("name") ?: return@query null, str("image")) }
    }

    /** Marks a title watched for [person]; true when AllMedia did it. */
    suspend fun markPlayed(key: String, person: String?): Boolean = withContext(Dispatchers.IO) {
        val uri = ROOT.buildUpon().appendPath("played").appendPath(key).apply { person?.let { appendQueryParameter("person", it) } }.build()
        runCatching { resolver.update(uri, ContentValues().apply { put("played", 1) }, null, null) == 1 }.getOrDefault(false)
    }

    /** Fires whenever AllMedia says what it would answer has changed: something played, a person switched. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val watching = runCatching { resolver.registerContentObserver(ROOT, true, observer) }.isSuccess
        awaitClose { if (watching) resolver.unregisterContentObserver(observer) }
    }

    private suspend fun <T> query(uri: Uri, map: Cursor.() -> T?): List<T> = withContext(Dispatchers.IO) {
        problem?.let { throw LibraryException(it) }
        val cursor = try {
            resolver.query(uri, null, null, null, null)
        } catch (e: SecurityException) {
            throw LibraryException("AllMedia won't share its library: this app isn't signed with AllMedia's key.")
        } ?: throw LibraryException("AllMedia didn't answer.")
        cursor.use { c -> buildList { while (c.moveToNext()) runCatching { c.map() }.getOrNull()?.let(::add) } }
    }

    private fun Cursor.str(name: String): String? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }?.takeIf { it.isNotBlank() }
    private fun Cursor.long(name: String): Long = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
    private fun Cursor.int(name: String): Int = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) } ?: -1
    private fun Cursor.double(name: String): Double? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getDouble(it) }

    companion object {
        const val PERMISSION = "app.allmedia.permission.LIBRARY"
        private const val AUTHORITY = "app.allmedia.library"
        private val ROOT: Uri = Uri.parse("content://$AUTHORITY/")
    }
}
