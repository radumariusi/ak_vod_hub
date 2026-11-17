package io.github.radumariusi.akvodhub.parser.discover

import io.github.radumariusi.akvodhub.parser.match.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private val action = 28
    private val scifi = 878
    private val romance = 10749
    private val drama = 18

    private fun watched(
        id: Long,
        genres: Set<Int>,
        year: Int,
        daysAgo: Int = 1,
        finished: Boolean = true,
        progress: Double = 1.0,
        type: MediaType = MediaType.MOVIE,
    ) = HistoryItem(id, type, genres, year, progress, finished, now - daysAgo * day)

    private fun candidate(
        id: Long,
        genres: Set<Int>,
        year: Int?,
        rating: Double? = 7.0,
        votes: Int? = 5_000,
        popularity: Double? = 50.0,
        type: MediaType = MediaType.MOVIE,
    ) = Candidate(id, type, genres, year, rating, votes, popularity)

    @Test
    fun recentFinishedTitlesWeighMore() {
        val recent = Recommender.weight(watched(1, setOf(action), 2000, daysAgo = 1), now)
        val old = Recommender.weight(watched(2, setOf(action), 2000, daysAgo = 200), now)
        val abandoned = Recommender.weight(watched(3, setOf(action), 2000, finished = false, progress = 0.05), now)
        assertTrue(recent > old)
        assertTrue(old > 0)
        assertTrue(recent > abandoned * 3)
    }

    @Test
    fun profileFollowsGenresYearsAndTypes() {
        val profile =
            Recommender.profile(
                listOf(
                    watched(1, setOf(action, scifi), 1999),
                    watched(2, setOf(action, scifi), 2003),
                    watched(3, setOf(romance), 2001, finished = false, progress = 0.05),
                ),
                emptyMap(),
                now,
            )
        assertEquals(setOf(action, scifi), profile.topGenres(2).toSet())
        assertTrue(profile.genres.getValue(action) > profile.genres.getValue(romance))
        assertEquals(1.0, profile.genres.values.max(), 1e-9)
        assertTrue(profile.yearMean!! in 1999.0..2003.0)
        assertEquals(1.0, profile.typeShare.getValue(MediaType.MOVIE), 1e-9)
    }

    @Test
    fun ranksMatchingGenresFirstAndExcludesWatched() {
        val profile = Recommender.profile(listOf(watched(1, setOf(action, scifi), 1999)), emptyMap(), now)
        val ranked =
            Recommender.rank(
                profile,
                listOf(
                    candidate(10, setOf(romance, drama), 2000),
                    candidate(11, setOf(action, scifi), 2001),
                    candidate(12, setOf(action), 1998),
                    candidate(1, setOf(action, scifi), 1999),
                ),
                emptyMap(),
                exclude = setOf(1),
                limit = 10,
            )
        assertEquals(listOf(11L, 12L, 10L), ranked)
    }

    @Test
    fun tmdbSignalsAndPeopleLiftCandidates() {
        val profile =
            Recommender.profile(
                listOf(watched(1, setOf(drama), 2010)),
                mapOf(1L to listOf(PersonWeight(500, 1.0))),
                now,
            )
        val a = candidate(20, setOf(drama), 2010)
        val b = candidate(21, setOf(drama), 2010)
        val c = candidate(22, setOf(drama), 2010)
        val signals =
            SignalsBuilder()
                .apply {
                    related(21, rank = 0, weight = 1.0)
                    person(22, 500)
                }.build()
        val ranked = Recommender.rank(profile, listOf(a, b, c), signals, emptySet(), 3)
        assertEquals(20L, ranked.last())
        assertTrue(Recommender.score(profile, b, signals[21]) > Recommender.score(profile, a, null))
        assertTrue(Recommender.score(profile, c, signals[22]) > Recommender.score(profile, a, null))
    }

    @Test
    fun relatedRankDecays() {
        val s =
            SignalsBuilder()
                .apply {
                    related(1, 0, 1.0)
                    related(2, 30, 1.0)
                    related(3, 200, 1.0)
                }.build()
        assertTrue(s.getValue(1).related > s.getValue(2).related)
        assertTrue(s.getValue(3).related > 0)
    }

    @Test
    fun qualityTrustsVotes() {
        assertTrue(Recommender.quality(8.0, 20_000) > Recommender.quality(9.5, 3))
        assertEquals(0.3, Recommender.quality(null, null), 1e-9)
        assertTrue(Recommender.quality(4.0, 10_000) < 0.1)
    }

    @Test
    fun seriesFansGetSeries() {
        val profile =
            Recommender.profile(
                listOf(
                    watched(1, setOf(drama), 2015, type = MediaType.TV),
                    watched(2, setOf(drama), 2016, type = MediaType.TV),
                ),
                emptyMap(),
                now,
            )
        val movie = candidate(30, setOf(drama), 2015, type = MediaType.MOVIE)
        val show = candidate(31, setOf(drama), 2015, type = MediaType.TV)
        assertTrue(Recommender.score(profile, show, null) > Recommender.score(profile, movie, null))
    }

    @Test
    fun emptyProfileStillScores() {
        val profile = Recommender.profile(emptyList(), emptyMap(), now)
        assertTrue(profile.isEmpty)
        assertFalse(Recommender.score(profile, candidate(1, setOf(drama), 2000), null).isNaN())
        assertEquals(0.5, Recommender.yearScore(profile, 2000), 1e-9)
    }
}
