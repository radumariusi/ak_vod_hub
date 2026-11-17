package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.MediaType

data class FilmographyCredit(val id: Long, val type: String?)

data class FilmographyTitle(val tmdbId: Long, val type: MediaType)

object ActorFilmography {
    /** Numai aparițiile ca actor; apelantul transmite lista cast, fără crew. */
    fun titles(cast: List<FilmographyCredit>): List<FilmographyTitle> =
        cast
            .mapNotNull {
                val type =
                    when (it.type) {
                        "movie" -> MediaType.MOVIE
                        "tv" -> MediaType.TV
                        else -> null
                    }
                if (it.id <= 0 || type == null) null else FilmographyTitle(it.id, type)
            }.distinct()

    /** Paginare după cheia locală: numai titlurile cu surse, fără catalog complet sau OFFSET. */
    fun page(personId: Long, afterId: Long, limit: Int): SqlQuery =
        SqlQuery(
            "SELECT ${CatalogQuery.COLUMNS} FROM person_credit p CROSS JOIN title t " +
                "ON t.mediaType = p.mediaType AND t.tmdbId = p.tmdbId " +
                "WHERE p.personId = ? AND t.sourceCount > 0 AND t.id > ? ORDER BY t.id LIMIT ?",
            listOf(personId, afterId, limit),
        )
}
