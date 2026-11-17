package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActorFilmographyTest {
    @Test
    fun keepsMoviesAndTvDistinctAndIgnoresUnsupportedCredits() {
        val credits =
            listOf(
                FilmographyCredit(1, "movie"),
                FilmographyCredit(1, "tv"),
                FilmographyCredit(1, "movie"),
                FilmographyCredit(2, "person"),
                FilmographyCredit(-1, "movie"),
                FilmographyCredit(3, null),
            )
        assertEquals(
            listOf(FilmographyTitle(1, MediaType.MOVIE), FilmographyTitle(1, MediaType.TV)),
            ActorFilmography.titles(credits),
        )
    }

    @Test
    fun pageUsesAvailableTitlesAndKeysetRatherThanOffset() {
        val query = ActorFilmography.page(42, 100, 48)
        assertEquals(listOf<Any>(42L, 100L, 48), query.args)
        assertTrue(query.sql.contains("t.sourceCount > 0"))
        assertTrue(query.sql.contains("t.id > ?"))
        assertTrue(query.sql.contains("t.mediaType = p.mediaType AND t.tmdbId = p.tmdbId"))
        assertFalse(query.sql.contains("OFFSET"))
    }
}
