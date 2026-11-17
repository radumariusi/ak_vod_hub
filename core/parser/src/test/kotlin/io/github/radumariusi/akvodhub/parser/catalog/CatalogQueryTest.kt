package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogQueryTest {
    @Test
    fun usProductionUsesOriginCountryAndNeverInfersCountryFromEnglish() {
        val query = CatalogQuery.page(CatalogFilter(production = ProductionFilter.US), 48, 0)
        assertTrue(query.sql.contains("t.originCountries LIKE ?"))
        assertFalse(query.sql.contains("t.originalLanguage ="))
        assertEquals(listOf<Any>("%,US,%", 48, 0), query.args)
    }

    @Test
    fun englishOriginalIsIndependentOfProductionCountryAndAudioFilter() {
        val filter = CatalogFilter(production = ProductionFilter.ENGLISH, language = "ro", originalAudioOnly = true)
        val query = CatalogQuery.page(filter, 48, 0)
        assertTrue(query.sql.contains("t.originalLanguage = ?"))
        assertTrue(query.sql.contains("t.hasOriginalAudio = 1"))
        assertFalse(query.sql.contains("t.originCountries LIKE"))
        assertEquals(listOf<Any>("%,ro,%", "en", 48, 0), query.args)
    }

    @Test
    fun productionFilterCanBeResetAndCountsAsAnActiveFilter() {
        val filter = CatalogFilter(production = ProductionFilter.US)
        assertTrue(filter.hasFilters)
        val reset = filter.copy(production = ProductionFilter.ALL)
        assertFalse(reset.hasFilters)
        assertFalse(CatalogQuery.page(reset, 48, 0).sql.contains("t.originCountries LIKE"))
    }

    @Test
    fun originalAudioFilterReadsStoredFlagWithoutJoiningSources() {
        val filter = CatalogFilter(originalAudioOnly = true)
        val query = CatalogQuery.page(filter, 48, 0)
        assertTrue(filter.hasFilters)
        assertTrue(query.sql.contains("t.hasOriginalAudio = 1"))
        assertFalse(query.sql.contains("raw_entry"))
        assertEquals(listOf<Any>(48, 0), query.args)
        assertFalse(filter.copy(originalAudioOnly = false).hasFilters)
    }

    @Test
    fun `fara filtre - doar titluri cu surse, populare, paginat`() {
        val q = CatalogQuery.page(CatalogFilter(), limit = 30, offset = 60)
        assertTrue(q.sql.contains("WHERE t.sourceCount > 0 ORDER BY t.popularity DESC, t.id DESC LIMIT ? OFFSET ?"))
        assertEquals(listOf<Any>(30, 60), q.args)
    }

    @Test
    fun `toate filtrele produc argumente in ordinea placeholder-elor`() {
        val filter =
            CatalogFilter(
                type = MediaType.MOVIE,
                genre = Genres.byKey("scifi"),
                years = YearRange("2010–2019", 2010, 2019),
                quality = VideoQuality.UHD,
                language = "RO",
                sort = CatalogSort.RECENT,
            )
        val q = CatalogQuery.page(filter, 20, 0)
        assertEquals(q.sql.count { it == '?' }, q.args.size)
        assertEquals(listOf<Any>("MOVIE", "%,878,%", "%,10765,%", 2010, 2019, "%,UHD,%", "%,ro,%", 20, 0), q.args)
        assertTrue(q.sql.contains("ORDER BY t.addedAt DESC"))
    }

    @Test
    fun `cautarea foloseste FTS si pune intai titlurile care incep cu textul`() {
        val q = CatalogQuery.page(CatalogFilter(text = "Matrix"), 30, 0)
        assertTrue(q.sql.contains("title_search MATCH ?"))
        assertTrue(q.sql.contains("ORDER BY (t.name LIKE ?) DESC"))
        assertEquals(listOf<Any>("matrix*", "Matrix%", 30, 0), q.args)
    }

    @Test
    fun `apreciate cere un minim de voturi`() {
        val q = CatalogQuery.page(CatalogFilter(sort = CatalogSort.RATING), 10, 0)
        assertTrue(q.sql.contains("t.voteCount >= ?"))
        assertEquals(q.sql.count { it == '?' }, q.args.size)
    }

    @Test
    fun `textul devine prefixe FTS fara diacritice si fara operatori`() {
        assertEquals("amelie* poulain*", CatalogQuery.ftsQuery("  Amélie   Poulain "))
        assertEquals("la* casa* de* papel*", CatalogQuery.ftsQuery("La Casa de Papel"))
        assertEquals("spider* man*", CatalogQuery.ftsQuery("Spider-Man\"*"))
        assertEquals("fratii* or*", CatalogQuery.ftsQuery("Frații OR"))
        assertNull(CatalogQuery.ftsQuery(" - * \" "))
    }

    @Test
    fun `filtrele se pot verifica`() {
        assertFalse(CatalogFilter(type = MediaType.TV).hasFilters)
        assertTrue(CatalogFilter(language = "en").hasFilters)
    }

    @Test
    fun `numele genurilor TMDB`() {
        assertEquals(listOf("Acțiune", "SF"), Genres.names("28,878,99999"))
        assertEquals(emptyList<String>(), Genres.names(null))
        assertEquals(
            Genres.home.size,
            Genres.home
                .map { it.key }
                .toSet()
                .size,
        )
    }
}
