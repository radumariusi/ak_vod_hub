package io.github.radumariusi.akvodhub.data.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbModelsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    @Test
    fun readsMovieSearchResults() {
        val page =
            json.decodeFromString<TmdbPage>(
                """{"page":1,"results":[{"id":603,"title":"The Matrix","original_title":"The Matrix",
                "release_date":"1999-03-30","poster_path":"/p.jpg","vote_average":8.2,"vote_count":26000,
                "popularity":80.5,"genre_ids":[28,878],"original_language":"en","adult":false}],"total_results":1}""",
            )
        val m = page.results.single()
        assertEquals("The Matrix", m.displayTitle)
        assertEquals(1999, m.year)
        assertEquals(listOf(28, 878), m.allGenreIds)
        assertEquals(603L, m.candidate().tmdbId)
    }

    @Test
    fun readsTvDetailsWithExternalIds() {
        val tv =
            json.decodeFromString<TmdbItem>(
                """{"id":1396,"name":"Breaking Bad","original_name":"Breaking Bad","first_air_date":"2008-01-20",
                "genres":[{"id":18,"name":"Drama"}],"external_ids":{"imdb_id":"tt0903747"},"overview":""}""",
            )
        assertEquals("Breaking Bad", tv.displayTitle)
        assertEquals(2008, tv.year)
        assertEquals(listOf(18), tv.allGenreIds)
        assertEquals("tt0903747", tv.imdb)
    }

    @Test
    fun toleratesNullsAndEmptyDates() {
        val item =
            json.decodeFromString<TmdbItem>(
                """{"id":1,"title":"X","release_date":"","imdb_id":null,"genre_ids":null}""",
            )
        assertNull(item.year)
        assertNull(item.imdb)
        assertEquals(emptyList<Int>(), item.allGenreIds)
    }
}
