package io.github.radumariusi.akvodhub.data.tmdb

import io.github.radumariusi.akvodhub.parser.catalog.CertificationEntry
import io.github.radumariusi.akvodhub.parser.catalog.MetadataPolicy
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
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    val tagline: String? = null,
    @SerialName("release_dates") val releaseDates: TmdbReleaseDates? = null,
    @SerialName("content_ratings") val contentRatings: TmdbContentRatings? = null,
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val original: String? get() = originalTitle ?: originalName
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    val allGenreIds: List<Int> get() = genreIds ?: genres?.map { it.id }.orEmpty()
    val imdb: String? get() = (imdbId ?: externalIds?.imdbId)?.ifBlank { null }

    val runtimeMinutes: Int? get() = MetadataPolicy.runtime(runtime, episodeRunTime)
    val countries: List<String> get() = MetadataPolicy.originCountries(originCountry)
    val certification: String?
        get() =
            MetadataPolicy.certification(
                releaseDates?.results.orEmpty().flatMap { country ->
                    country.releases.map { CertificationEntry(country.country, it.certification.orEmpty(), it.type) }
                } + contentRatings?.results.orEmpty().map { CertificationEntry(it.country, it.rating.orEmpty()) },
                countries,
            )

    fun candidate() = MatchCandidate(id, displayTitle, original, year, popularity ?: 0.0)
}

@Serializable
data class TmdbReleaseDates(
    val results: List<TmdbReleaseCountry> = emptyList(),
)

@Serializable
data class TmdbReleaseCountry(
    @SerialName("iso_3166_1") val country: String,
    @SerialName("release_dates") val releases: List<TmdbReleaseCertification> = emptyList(),
)

@Serializable
data class TmdbReleaseCertification(
    val certification: String? = null,
    val type: Int? = null,
)

@Serializable
data class TmdbContentRatings(
    val results: List<TmdbContentRating> = emptyList(),
)

@Serializable
data class TmdbContentRating(
    @SerialName("iso_3166_1") val country: String,
    val rating: String? = null,
)

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

/** Distribuția și echipa unui titlu (`/movie/{id}/credits`, `/tv/{id}/aggregate_credits`). */
@Serializable
data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCastMember(
    val id: Long,
    val name: String,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    /** La seriale (`aggregate_credits`), personajele vin într-o listă. */
    val roles: List<TmdbRole>? = null,
) {
    val role: String? get() = character?.ifBlank { null } ?: roles?.firstOrNull()?.character?.ifBlank { null }
}

@Serializable
data class TmdbRole(
    val character: String? = null,
)

@Serializable
data class TmdbCrewMember(
    val id: Long,
    val name: String,
    val job: String? = null,
)

/** Cheia TMDB lipsește sau a fost respinsă (401). */
class TmdbAuthException : Exception("Cheia TMDB nu e validă.")

/** Colecția (francize) din care face parte un film. */
@Serializable
data class TmdbCollectionRef(
    val id: Long,
    val name: String? = null,
)

/**
 * Detaliile unui titlu cu tot ce trebuie pentru descoperire, dintr-o singură cerere (`append_to_response`):
 * recomandări, similare, distribuție și colecția.
 */
@Serializable
data class TmdbRelated(
    val id: Long,
    val genres: List<TmdbGenre>? = null,
    @SerialName("belongs_to_collection") val collection: TmdbCollectionRef? = null,
    val recommendations: TmdbPage? = null,
    val similar: TmdbPage? = null,
    val credits: TmdbCredits? = null,
    @SerialName("aggregate_credits") val aggregateCredits: TmdbCredits? = null,
) {
    val allCredits: TmdbCredits get() = credits ?: aggregateCredits ?: TmdbCredits()
}

@Serializable
data class TmdbCollection(
    val id: Long,
    val name: String? = null,
    val parts: List<TmdbItem> = emptyList(),
)

/** Un titlu din filmografia unei persoane (`/person/{id}/combined_credits`). */
@Serializable
data class TmdbPersonCredit(
    val id: Long,
    @SerialName("media_type") val mediaType: String? = null,
    val job: String? = null,
    val popularity: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
)

@Serializable
data class TmdbPersonCredits(
    val cast: List<TmdbPersonCredit> = emptyList(),
    val crew: List<TmdbPersonCredit> = emptyList(),
)

/** Rezultatul `/find/{imdb_id}`. */
@Serializable
data class TmdbFindResult(
    @SerialName("movie_results") val movies: List<TmdbItem> = emptyList(),
    @SerialName("tv_results") val tv: List<TmdbItem> = emptyList(),
)

@Serializable
data class TmdbEpisode(
    val name: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("episode_number") val number: Int? = null,
)

@Serializable
data class TmdbSeasonRef(
    @SerialName("season_number") val number: Int,
)

@Serializable
data class TmdbSeriesSeasons(val seasons: List<TmdbSeasonRef> = emptyList())

@Serializable
data class TmdbSeason(val episodes: List<TmdbEpisode> = emptyList())

@Serializable
data class TmdbImages(val logos: List<TmdbLogo> = emptyList())

@Serializable
data class TmdbLogo(
    @SerialName("file_path") val path: String,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_count") val votes: Int = 0,
)
