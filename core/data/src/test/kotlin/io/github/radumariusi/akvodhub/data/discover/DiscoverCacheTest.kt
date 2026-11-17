package io.github.radumariusi.akvodhub.data.discover

import io.github.radumariusi.akvodhub.data.tmdb.TmdbFindResult
import io.github.radumariusi.akvodhub.data.tmdb.TmdbPersonCredits
import io.github.radumariusi.akvodhub.data.tmdb.TmdbRelated
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCacheTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    @Test
    fun keepsWhatRecommendationsNeedFromMovieDetails() {
        val related =
            json.decodeFromString<TmdbRelated>(
                """{"id":603,"title":"The Matrix","genres":[{"id":28,"name":"Action"}],
                "belongs_to_collection":{"id":2344,"name":"The Matrix Collection"},
                "recommendations":{"page":1,"results":[{"id":604,"title":"The Matrix Reloaded",
                "release_date":"2003-05-15"}]},
                "similar":{"results":[{"id":1858,"title":"Transformers"}]},
                "credits":{"cast":[{"id":6384,"name":"Keanu Reeves","character":"Neo"},
                {"id":2975,"name":"Laurence Fishburne"}],
                "crew":[{"id":9340,"name":"Lana Wachowski","job":"Director"},
                {"id":9339,"name":"Lilly Wachowski","job":"Director"},
                {"id":9340,"name":"Lana Wachowski","job":"Writer"}]}}""",
            )
        val cached = DiscoverCache.related(related, tv = false)
        assertEquals(2344L, cached.collectionId)
        assertEquals(listOf(604L), cached.recommendations.map { it.id })
        assertEquals(2003, cached.recommendations[0].year)
        assertEquals(MediaType.MOVIE, cached.similar[0].ref.type)
        assertEquals(listOf(9340L, 9339L), cached.people.filter { it.director }.map { it.id })
        assertEquals(listOf(6384L, 2975L), cached.people.filterNot { it.director }.map { it.id })

        // Se poate salva și citi din cache.
        val text = DiscoverCache.json.encodeToString(cached)
        assertEquals(cached, DiscoverCache.json.decodeFromString<CachedRelated>(text))
    }

    @Test
    fun readsTvAggregateCredits() {
        val related =
            json.decodeFromString<TmdbRelated>(
                """{"id":1396,"aggregate_credits":{"cast":[{"id":17419,"name":"Bryan Cranston",
                "roles":[{"character":"Walter White"}]}],"crew":[]},"recommendations":{"results":[{"id":60059,
                "name":"Better Call Saul"}]}}""",
            )
        val cached = DiscoverCache.related(related, tv = true)
        assertEquals(
            MediaType.TV,
            cached.recommendations
                .single()
                .ref.type,
        )
        assertEquals("Better Call Saul", cached.recommendations.single().name)
        assertEquals(17419L, cached.people.single().id)
        assertFalse(cached.people.single().director)
    }

    @Test
    fun filmographyIsSortedByPopularity() {
        val credits =
            json.decodeFromString<TmdbPersonCredits>(
                """{"cast":[{"id":1,"media_type":"movie","popularity":5.0},
                {"id":2,"media_type":"tv","popularity":50.0},{"id":3,"media_type":"person"}],
                "crew":[{"id":7,"media_type":"movie","job":"Director","popularity":1.0},
                {"id":8,"media_type":"movie","job":"Writer","popularity":99.0}]}""",
            )
        val f = DiscoverCache.filmography(credits)
        assertEquals(listOf(2L, 1L), f.acted.map { it.id })
        assertTrue(f.acted[0].tv)
        assertEquals(listOf(7L), f.directed.map { it.id })
    }

    @Test
    fun readsFindResults() {
        val r =
            json.decodeFromString<TmdbFindResult>(
                """{"movie_results":[{"id":603,"title":"The Matrix"}],"tv_results":[],"person_results":[]}""",
            )
        assertEquals(603L, r.movies.single().id)
        assertTrue(r.tv.isEmpty())
    }
}
