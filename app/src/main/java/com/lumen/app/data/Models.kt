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
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val year: String?,
    val cast: String?,
    val duration: String?,
    val backdrop: String?,
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

class AuthException(message: String) : Exception(message)
