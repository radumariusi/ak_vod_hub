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
    fun readsEnrichedMovieDetailsAndSelectsTheatricalUsCertification() {
        val item =
            json.decodeFromString<TmdbItem>(
                """{"id":603,"title":"The Matrix","runtime":136,"origin_country":["US","AU"],
            "tagline":"Welcome to the Real World.","imdb_id":"tt0133093",
            "release_dates":{"results":[{"iso_3166_1":"US","release_dates":[
            {"type":5,"certification":"NC-17"},{"type":3,"certification":"R"}]}]}}""",
            )
        assertEquals(136, item.runtimeMinutes)
        assertEquals(listOf("US", "AU"), item.countries)
        assertEquals("US: R", item.certification)
        assertEquals("tt0133093", item.imdb)
    }

    @Test
    fun readsTvContentRatingsAndToleratesMissingMetadata() {
        val item =
            json.decodeFromString<TmdbItem>(
                """{"id":1396,"name":"Breaking Bad","episode_run_time":[0,45],"origin_country":["US"],
            "external_ids":{"imdb_id":"tt0903747"},"content_ratings":{"results":[
            {"iso_3166_1":"US","rating":"TV-MA"},{"iso_3166_1":"RO","rating":"15"}]}}""",
            )
        assertEquals(45, item.runtimeMinutes)
        assertEquals("RO: 15", item.certification)
        val absent = json.decodeFromString<TmdbItem>("""{"id":1,"runtime":null,"origin_country":null}""")
        assertNull(absent.runtimeMinutes)
        assertNull(absent.certification)
        assertEquals(emptyList<String>(), absent.countries)
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
