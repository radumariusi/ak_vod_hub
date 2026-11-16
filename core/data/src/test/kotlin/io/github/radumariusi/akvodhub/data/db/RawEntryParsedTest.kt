package io.github.radumariusi.akvodhub.data.db

import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawEntryParsedTest {
    private fun entry(
        name: String,
        group: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ) = RawEntryEntity(
        sourceId = 1,
        providerKey = name,
        kind = VodKind.MOVIE,
        name = name,
        groupTitle = group,
        url = null,
        logo = null,
        season = season,
        episode = episode,
        firstSeenAt = 0,
        seenRun = 0,
    )

    @Test
    fun fillsParsedFields() {
        val e = entry("|DE| Matrix 1999 [MULTI-SUB]", group = "DE | Filme 4K").parsed()
        assertEquals("Matrix", e.title)
        assertEquals(1999, e.year)
        assertEquals(VideoQuality.UHD, e.quality)
        assertEquals("de", e.languages)
        assertEquals("mul", e.subtitles)
    }

    @Test
    fun providerSeasonWins() {
        val e = entry("Breaking Bad S01E02", season = 3, episode = 4).parsed()
        assertEquals(3, e.season)
        assertEquals(4, e.episode)
        assertNull(e.languages)
    }
}
