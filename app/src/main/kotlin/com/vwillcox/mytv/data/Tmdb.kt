@file:OptIn(ExperimentalSerializationApi::class)

package com.vwillcox.mytv.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val TmdbJson = Json(LenientJson) { namingStrategy = JsonNamingStrategy.SnakeCase }

@Serializable
data class TmdbTitle(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val voteAverage: Float? = null,
) {
    val label get() = title ?: name.orEmpty()
    val year get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    fun backdrop(size: String = "w1280") = backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}

@Serializable
private data class TmdbPage(val results: List<TmdbTitle> = emptyList())

/** What's being watched now, from TMDB. Only ever matched against the library unless unowned titles are asked for. */
class Tmdb(private val http: OkHttpClient) {
    /**
     * [kind] is "movie" or "tv". Trending this week first, then what's popular in [region], with the repeats taken
     * out: the order is how popular each is.
     */
    suspend fun popular(key: String, kind: String, region: String): List<TmdbTitle> {
        val trending = (1..2).flatMap { page("/trending/$kind/week", key, page = it) }
        val popular = (1..2).flatMap { page("/$kind/popular", key, page = it, region = region) }
        return (trending + popular).distinctBy { it.id }
    }

    private suspend fun page(path: String, key: String, page: Int, region: String? = null): List<TmdbTitle> {
        // A v4 read access token is a JWT and goes in a header; a v3 key goes in the query.
        val bearer = key.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
            if (!bearer) addQueryParameter("api_key", key)
            addQueryParameter("page", "$page")
            addQueryParameter("language", "en-GB")
            region?.let { addQueryParameter("region", it) }
        }.build()
        val req = Request.Builder().url(url).apply { if (bearer) header("Authorization", "Bearer $key") }.build()
        return TmdbJson.decodeFromString<TmdbPage>(http.fetch(req)).results
    }
}
