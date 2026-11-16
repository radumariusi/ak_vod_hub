package io.github.radumariusi.akvodhub.data.tmdb

import io.github.radumariusi.akvodhub.parser.match.MatchCandidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Un film sau serial așa cum vine de la TMDB (căutare sau detalii; filmele au `title`, serialele `name`). */
@Serializable
data class TmdbItem(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    val genres: List<TmdbGenre>? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val original: String? get() = originalTitle ?: originalName
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    val allGenreIds: List<Int> get() = genreIds ?: genres?.map { it.id }.orEmpty()
    val imdb: String? get() = (imdbId ?: externalIds?.imdbId)?.ifBlank { null }

    fun candidate() = MatchCandidate(id, displayTitle, original, year, popularity ?: 0.0)
}

@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String? = null,
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class TmdbPage(
    val results: List<TmdbItem> = emptyList(),
)

/** Cheia TMDB lipsește sau a fost respinsă (401). */
class TmdbAuthException : Exception("Cheia TMDB nu e validă.")
