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
        val query = CatalogQuery.page(CatalogFilter(production = ProductionFilter.US), 48)
        assertTrue(query.sql.contains("t.originCountries LIKE ?"))
        assertFalse(query.sql.contains("t.originalLanguage ="))
        assertEquals(listOf<Any>("%,US,%", 48), query.args)
    }

    @Test
    fun englishOriginalIsIndependentOfProductionCountryAndAudioFilter() {
        val filter = CatalogFilter(production = ProductionFilter.ENGLISH, language = "ro", originalAudioOnly = true)
        val query = CatalogQuery.page(filter, 48)
        assertTrue(query.sql.contains("t.originalLanguage = ?"))
        assertTrue(query.sql.contains("s.originalAudio = 1"))
        assertFalse(query.sql.contains("t.originCountries LIKE"))
        assertEquals(listOf<Any>("ro", "en", 48), query.args)
    }

    @Test
    fun productionFilterCanBeResetAndCountsAsAnActiveFilter() {
        val filter = CatalogFilter(production = ProductionFilter.US)
        assertTrue(filter.hasFilters)
        val reset = filter.copy(production = ProductionFilter.ALL)
        assertFalse(reset.hasFilters)
        assertFalse(CatalogQuery.page(reset, 48).sql.contains("t.originCountries LIKE"))
    }

    @Test
    fun originalAudioFilterReadsStoredFlagWithoutJoiningSources() {
        val filter = CatalogFilter(originalAudioOnly = true)
        val query = CatalogQuery.page(filter, 48)
        assertTrue(filter.hasFilters)
        assertTrue(query.sql.contains("s.originalAudio = 1"))
        assertFalse(query.sql.contains("raw_entry"))
        assertEquals(listOf<Any>(48), query.args)
        assertFalse(filter.copy(originalAudioOnly = false).hasFilters)
    }

    @Test
    fun `fara filtre - doar titluri cu surse, populare, paginat`() {
        val cursor = CatalogCursor(CatalogPageSection.VALUES, 9.5, 60)
        val q = CatalogQuery.page(CatalogFilter(), limit = 30, after = cursor)
        assertTrue(q.sql.contains("t.popularity <= ? AND (t.popularity < ? OR t.id < ?)"))
        assertTrue(q.sql.endsWith("ORDER BY t.popularity DESC, t.id DESC LIMIT ?"))
        assertEquals(listOf<Any>(9.5, 9.5, 60L, 30), q.args)
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
        val q = CatalogQuery.page(filter, 20)
        assertEquals(q.sql.count { it == '?' }, q.args.size)
        assertEquals(listOf<Any>("MOVIE", "%,878,%", "%,10765,%", 2010, 2019, "UHD", "ro", 20), q.args)
        assertTrue(q.sql.contains("s.titleId = t.id AND s.quality = ? AND s.language = ?"))
        assertFalse(q.sql.contains("raw_entry"))
        assertTrue(q.sql.contains("ORDER BY t.addedAt DESC"))
    }

    @Test
    fun newSortsKeepEqualValuesAndNullsInSeparateCursorSegments() {
        val sorts =
            mapOf(
                CatalogSort.IMDB to "imdbRating",
                CatalogSort.RELEASE to "year",
                CatalogSort.PLAYLIST to "playlistAddedAt",
            )
        for ((sort, column) in sorts) {
            val filter = CatalogFilter(sort = sort, library = LibraryFilter.WATCHLIST)
            val query = CatalogQuery.page(filter, 48, after = CatalogCursor(CatalogPageSection.VALUES, 7, 22))
            assertTrue(query.sql.contains("t.$column <= ? AND (t.$column < ? OR t.id < ?)"))
            assertTrue(query.sql.contains("FROM watchlist"))
            assertEquals(listOf<Any>(7, 7, 22L, 48), query.args)
            val afterNull = CatalogCursor(CatalogPageSection.NULLS, null, 10)
            val nulls = CatalogQuery.page(filter, 48, afterNull.section, afterNull)
            assertTrue(nulls.sql.contains("t.$column IS NULL AND t.id < ?"))
            assertEquals(listOf<Any>(10L, 48), nulls.args)
        }
    }

    @Test
    fun savedCombinationsPreserveSelectionAndValidateFields() {
        val filter =
            CatalogFilter(
                type = MediaType.TV,
                sort = CatalogSort.IMDB,
                language = "de",
                library = LibraryFilter.WATCHLIST,
            )
        val presets = listOf(FilterPreset("Seriale preferate", filter))
        assertEquals(presets, BrowseFilters.decodePresets(MediaType.TV, BrowseFilters.encodePresets(presets)))
        assertEquals(CatalogFilter(type = MediaType.MOVIE), BrowseFilters.decode(MediaType.MOVIE, "bad|bad|BAD|x|NA"))
        assertNull(BrowseFilters.sanitize("unrelated", "value"))
    }

    @Test
    fun `cautarea foloseste FTS si pune intai titlurile care incep cu textul`() {
        val q = CatalogQuery.page(CatalogFilter(text = "Matrix"), 30)
        assertTrue(q.sql.contains("title_search MATCH ?"))
        assertTrue(q.sql.contains("t.name LIKE ?"))
        assertEquals(listOf<Any>("matrix*", "Matrix%", 30), q.args)
    }

    @Test
    fun `apreciate cere un minim de voturi`() {
        val q = CatalogQuery.page(CatalogFilter(sort = CatalogSort.RATING), 10)
        assertTrue(q.sql.contains("t.voteCount >= ?"))
        assertEquals(q.sql.count { it == '?' }, q.args.size)
    }

    @Test
    fun `cautarea pastreaza prefixele cu popularitate necunoscuta inaintea celorlalte rezultate`() {
        val filter = CatalogFilter(text = "Matrix")
        assertEquals(
            listOf(
                CatalogPageSection.PREFIX_VALUES,
                CatalogPageSection.PREFIX_NULLS,
                CatalogPageSection.OTHER_VALUES,
                CatalogPageSection.OTHER_NULLS,
            ),
            CatalogQuery.sections(filter),
        )
        val cursor = CatalogCursor(CatalogPageSection.PREFIX_NULLS, null, 42)
        val query = CatalogQuery.page(filter, 10, cursor.section, cursor)
        assertTrue(query.sql.contains("t.name LIKE ? AND t.popularity IS NULL AND t.id < ?"))
        assertEquals(listOf<Any>("matrix*", "Matrix%", 42L, 10), query.args)
        val other = CatalogQuery.page(filter, 10, CatalogPageSection.OTHER_VALUES)
        assertTrue(other.sql.contains("t.name NOT LIKE ? AND t.popularity IS NOT NULL"))
    }

    @Test
    fun `alfabetic continua dupa nume si ID crescator`() {
        val filter = CatalogFilter(sort = CatalogSort.TITLE)
        val cursor = CatalogCursor(CatalogPageSection.VALUES, "Șir", 42)
        val query = CatalogQuery.page(filter, 10, after = cursor)
        assertTrue(query.sql.contains("t.name >= ? AND (t.name > ? OR t.id > ?)"))
        assertTrue(query.sql.endsWith("ORDER BY t.name, t.id LIMIT ?"))
        assertEquals(listOf<Any>("Șir", "Șir", 42L, 10), query.args)
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
