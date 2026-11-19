package io.github.radumariusi.akvodhub.parser.match

import io.github.radumariusi.akvodhub.parser.title.TitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScorerTest {
    private fun movie(
        title: String,
        year: Int?,
        language: String? = null,
    ) = MatchQuery(MediaType.MOVIE, title, year, language)

    private fun c(
        id: Long,
        title: String,
        year: Int?,
        original: String? = null,
        popularity: Double = 1.0,
    ) = MatchCandidate(id, title, original, year, popularity)

    @Test
    fun normalizesLikeAHuman() {
        assertEquals("matrix", TitleSimilarity.normalize("The Matrix"))
        assertEquals("amelie", TitleSimilarity.normalize("Amélie"))
        assertEquals("mission impossible fallout", TitleSimilarity.normalize("Mission: Impossible - Fallout"))
        assertEquals("fast and furious", TitleSimilarity.normalize("Fast & Furious"))
        assertEquals("rocky 2", TitleSimilarity.normalize("Rocky II"))
        assertEquals("schindlers list", TitleSimilarity.normalize("Schindler's List"))
        assertEquals("v for vendetta", TitleSimilarity.normalize("V for Vendetta"))
    }

    @Test
    fun exactTitleAndYearIsCertain() {
        val best = MatchScorer.best(movie("The Matrix", 1999), listOf(c(603, "The Matrix", 1999)))!!
        assertEquals(1.0, best.score, 0.001)
    }

    @Test
    fun picksTheRightYearAmongRemakes() {
        val candidates = listOf(c(1, "Dune", 1984, popularity = 50.0), c(2, "Dune", 2021, popularity = 200.0))
        assertEquals(1L, MatchScorer.best(movie("Dune", 1984), candidates)!!.candidate.tmdbId)
        assertEquals(2L, MatchScorer.best(movie("Dune", 2021), candidates)!!.candidate.tmdbId)
    }

    @Test
    fun rejectsWrongYearEvenWithSameTitle() {
        val best = MatchScorer.best(movie("Dune", 2021), listOf(c(1, "Dune", 1984)))!!
        assertTrue(best.score < MatchScorer.THRESHOLD)
    }

    @Test
    fun acceptsOffByOneYear() {
        val best = MatchScorer.best(movie("Parasite", 2020), listOf(c(496243, "Parasite", 2019)))!!
        assertTrue(best.score >= MatchScorer.THRESHOLD)
    }

    @Test
    fun matchesTranslatedAndOriginalTitles() {
        // Căutare în germană: TMDB întoarce titlul german, iar originalul e în engleză.
        val de = MatchScorer.best(movie("Der Pate", 1972, "de"), listOf(c(238, "Der Pate", 1972, "The Godfather")))!!
        assertTrue(de.score >= MatchScorer.THRESHOLD)
        val en =
            MatchScorer.best(
                movie("The Godfather", 1972, "de"),
                listOf(c(238, "Der Pate", 1972, "The Godfather")),
            )!!
        assertTrue(en.score >= MatchScorer.THRESHOLD)
    }

    @Test
    fun distinctSourceLabelsAndTranslatedNamesMatchOneCatalogIdentity() {
        val variants =
            listOf(
                "4K-SC-HDR - The Glass Island (2026)",
                "DE - Insel aus Glas (2026)",
                "4K-DE - Insel aus Glas - The Glass Island (2026)",
            )
        val tmdb = c(721, "Insel aus Glas", 2026, "The Glass Island")
        val unrelated = c(722, "Insel aus Glas", 2026, "The Broken House")
        val remake = c(723, "Insel aus Glas", 2006, "The Glass Island")
        for (variant in variants) {
            val parsed = TitleParser.parse(variant)
            val query = movie(parsed.title, parsed.year, parsed.languages.singleOrNull())
            val best = MatchScorer.best(query, listOf(tmdb))!!
            assertEquals(variant, tmdb.tmdbId, best.candidate.tmdbId)
            assertTrue(variant, best.score >= MatchScorer.THRESHOLD)
        }
        val aliases = movie("Insel aus Glas - The Glass Island", 2026, "de")
        assertTrue(MatchScorer.score(aliases, unrelated, 0) < MatchScorer.THRESHOLD)
        assertTrue(MatchScorer.score(aliases, remake, 0) < MatchScorer.THRESHOLD)
        assertEquals(
            listOf("Insel aus Glas", "The Glass Island"),
            MatchScorer.plan(aliases).takeLast(2).map { it.query },
        )
        assertNotEquals(aliases.key, movie("Insel aus Glas The Glass Island", 2026, "de").key)
    }

    @Test
    fun toleratesMissingSubtitleAndPunctuation() {
        val best =
            MatchScorer.best(
                movie("Mission Impossible Fallout", 2018),
                listOf(c(353081, "Mission: Impossible - Fallout", 2018)),
            )!!
        assertEquals(1.0, best.score, 0.001)
        val avatar = MatchScorer.best(movie("Avatar", 2022), listOf(c(76600, "Avatar: The Way of Water", 2022)))!!
        assertTrue(avatar.score >= MatchScorer.THRESHOLD)
    }

    @Test
    fun rejectsUnrelatedTitles() {
        val best = MatchScorer.best(movie("The Room", 2003), listOf(c(1, "Room", 2015), c(2, "Doom", 2005)))!!
        assertTrue(best.score < MatchScorer.THRESHOLD)
        assertNull(MatchScorer.best(movie("X", 2000), emptyList()))
    }

    @Test
    fun withoutYearPrefersFirstResult() {
        val best =
            MatchScorer.best(
                movie("Inception", null),
                listOf(c(27205, "Inception", 2010), c(9, "Inception", 2014)),
            )!!
        assertEquals(27205L, best.candidate.tmdbId)
        assertTrue(best.score >= MatchScorer.THRESHOLD)
    }

    @Test
    fun planRetriesWithoutYearAndWithYearInTitle() {
        val plan = MatchScorer.plan(movie("Wonder Woman", 1984))
        assertEquals(3, plan.size)
        assertEquals(SearchRequest(MediaType.MOVIE, "Wonder Woman", 1984, null), plan[0])
        assertEquals(SearchRequest(MediaType.MOVIE, "Wonder Woman", null, null), plan[1])
        assertEquals(SearchRequest(MediaType.MOVIE, "Wonder Woman 1984", null, null), plan[2])
        assertEquals(1, MatchScorer.plan(movie("Inception", null)).size)
        assertEquals("de", MatchScorer.plan(movie("Der Pate", 1972, "de"))[0].language)
        assertNull(MatchScorer.plan(movie("Matrix", 1999, "mul"))[0].language)
        assertNull(MatchScorer.plan(movie("Matrix", 1999, "en"))[0].language)
    }

    @Test
    fun cacheKeyMergesDuplicates() {
        assertEquals(movie("The Matrix", 1999).key, movie("Matrix", 1999).key)
        assertNotEquals(movie("Matrix", 1999).key, movie("Matrix", 2003).key)
        assertNotEquals(movie("Matrix", 1999).key, MatchQuery(MediaType.TV, "Matrix", 1999).key)
    }
}
