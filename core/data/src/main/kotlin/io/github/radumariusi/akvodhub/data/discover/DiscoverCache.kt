package io.github.radumariusi.akvodhub.data.discover

import io.github.radumariusi.akvodhub.data.stremio.StremioMeta
import io.github.radumariusi.akvodhub.data.tmdb.TmdbCollection
import io.github.radumariusi.akvodhub.data.tmdb.TmdbItem
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPersonCredit
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPersonCredits
import io.github.radumariusi.akvodhub.data.tmdb.TmdbRelated
import io.github.radumariusi.akvodhub.parser.discover.TitleRef
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Formele compacte (JSON) ale listelor din `discover_cache`. */
@Serializable
data class CachedRef(
    val tv: Boolean,
    val id: Long,
    val name: String = "",
    val year: Int? = null,
) {
    val ref: TitleRef get() = TitleRef(if (tv) MediaType.TV else MediaType.MOVIE, id)
}

@Serializable
data class CachedPerson(
    val id: Long,
    val name: String,
    val director: Boolean,
)

@Serializable
data class CachedRelated(
    val recommendations: List<CachedRef> = emptyList(),
    val similar: List<CachedRef> = emptyList(),
    val collectionId: Long? = null,
    val people: List<CachedPerson> = emptyList(),
)

@Serializable
data class CachedCollection(
    val name: String = "",
    val parts: List<CachedRef> = emptyList(),
)

@Serializable
data class CachedFilmography(
    /** Ce a regizat (filme și seriale), cele mai populare primele. */
    val directed: List<CachedRef> = emptyList(),
    /** În ce a jucat, cele mai populare primele. */
    val acted: List<CachedRef> = emptyList(),
)

@Serializable
data class CachedMeta(
    val name: String,
    val tv: Boolean,
    val year: Int? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
)

@Serializable
data class CachedCatalog(
    val metas: List<CachedMeta> = emptyList(),
)

internal object DiscoverCache {
    val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun ref(
        item: TmdbItem,
        tv: Boolean,
    ) = CachedRef(tv, item.id, item.displayTitle, item.year)

    fun related(
        r: TmdbRelated,
        tv: Boolean,
    ): CachedRelated {
        val credits = r.allCredits
        val directors =
            credits.crew
                .filter { it.job == "Director" }
                .distinctBy { it.id }
                .take(MAX_DIRECTORS)
                .map { CachedPerson(it.id, it.name, director = true) }
        val cast = credits.cast.take(MAX_CAST).map { CachedPerson(it.id, it.name, director = false) }
        return CachedRelated(
            recommendations =
                r.recommendations
                    ?.results
                    .orEmpty()
                    .map { ref(it, tv) },
            similar =
                r.similar
                    ?.results
                    .orEmpty()
                    .map { ref(it, tv) },
            collectionId = r.collection?.id,
            people = (directors + cast).distinctBy { it.id to it.director },
        )
    }

    fun collection(c: TmdbCollection) = CachedCollection(c.name.orEmpty(), c.parts.map { ref(it, tv = false) })

    fun filmography(credits: TmdbPersonCredits): CachedFilmography {
        fun refs(list: List<TmdbPersonCredit>) =
            list
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .distinctBy { it.mediaType to it.id }
                .sortedByDescending { it.popularity ?: 0.0 }
                .take(MAX_FILMOGRAPHY)
                .map { CachedRef(it.mediaType == "tv", it.id) }
        return CachedFilmography(
            directed = refs(credits.crew.filter { it.job == "Director" }),
            acted = refs(credits.cast),
        )
    }

    fun meta(
        m: StremioMeta,
        fallbackType: String,
    ) = CachedMeta(
        name = m.name,
        tv = (m.type.ifEmpty { fallbackType }) == "series",
        year = m.year,
        imdb = m.imdbId,
        tmdb = m.tmdbId,
    )

    private const val MAX_DIRECTORS = 2
    private const val MAX_CAST = 6
    private const val MAX_FILMOGRAPHY = 120
}
