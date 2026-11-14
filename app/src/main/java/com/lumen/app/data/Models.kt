package com.lumen.app.data

data class Category(val id: String, val name: String)

data class Channel(
    val id: Int,
    val num: Int,
    val name: String,
    val logo: String?,
    val catId: String,
    val epgId: String?,
)

data class Movie(
    val id: Int,
    val name: String,
    val poster: String?,
    val catId: String,
    val ext: String,
    val rating: String?,
)

data class Series(
    val id: Int,
    val name: String,
    val cover: String?,
    val catId: String,
    val plot: String?,
    val rating: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val num: Int,
    val title: String,
    val ext: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
)

data class SeriesDetail(
    val plot: String?,
    val genre: String?,
    val year: String?,
    val cast: String?,
    val backdrop: String?,
    val seasons: Map<Int, List<Episode>>,
    val extra: TitleExtra = TitleExtra(),
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val year: String?,
    val cast: String?,
    val duration: String?,
    val backdrop: String?,
    val extra: TitleExtra = TitleExtra(),
)

/** Informazioni aggiuntive di un film o di una serie, quando il fornitore le dà (di solito arrivano da TMDB). */
data class TitleExtra(
    val rating: Double? = null,
    val director: String? = null,
    val country: String? = null,
    val released: String? = null,
    val runtime: String? = null,
    val age: String? = null,
    val original: String? = null,
    val quality: String? = null,
    val audio: String? = null,
    val trailer: String? = null,
)

data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String,
)

data class AccountInfo(
    val username: String,
    val status: String,
    val expiry: Long?,
    val maxConnections: Int,
    val activeConnections: Int,
)

class AppUpdate(val version: String, val url: String)

class AuthException(message: String) : Exception(message)
