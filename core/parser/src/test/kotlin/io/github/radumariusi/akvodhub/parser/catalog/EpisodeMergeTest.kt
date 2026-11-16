package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeMergeTest {
    private fun entry(
        id: Long,
        season: Int?,
        episode: Int?,
        name: String,
        quality: VideoQuality? = null,
        language: String? = null,
    ) = EpisodeEntry(season, episode, name, SourceInfo(id, quality, languages = listOfNotNull(language)))

    @Test
    fun `episoadele din mai multe surse se unesc, in ordine`() {
        val seasons =
            EpisodeMerge.merge(
                listOf(
                    entry(1, 2, 1, "Show S02E01"),
                    entry(2, 1, 2, "Show S01E02 - Second", VideoQuality.HD, "en"),
                    entry(3, 1, 1, "Show S01E01 Pilot", VideoQuality.FHD, "ro"),
                    entry(4, 1, 2, "RO| Show S01E02", VideoQuality.UHD, "ro"),
                    entry(5, 1, 1, "Show S01E01", VideoQuality.HD, "en"),
                ),
                preferredLanguages = listOf("ro"),
            )
        assertEquals(listOf(1, 2), seasons.map { it.number })
        val first = seasons[0].episodes
        assertEquals(listOf(1, 2), first.map { it.number })
        assertEquals(listOf(3L, 5L), first[0].sources.map { it.id })
        assertEquals("Pilot", first[0].title)
        assertEquals(listOf(4L, 2L), first[1].sources.map { it.id })
        assertEquals("Second", first[1].title)
    }

    @Test
    fun `episoadele fara numar raman separate, la sfarsitul sezonului`() {
        val seasons =
            EpisodeMerge.merge(
                listOf(entry(1, null, null, "Extra B"), entry(2, null, null, "Extra A"), entry(3, null, 1, "x E1")),
            )
        assertEquals(listOf(0), seasons.map { it.number })
        assertEquals(listOf(1, null, null), seasons[0].episodes.map { it.number })
        assertEquals(listOf(3L, 2L, 1L), seasons[0].episodes.map { it.sources.single().id })
    }

    @Test
    fun `titlul episodului`() {
        assertEquals("Cat's in the Bag", EpisodeMerge.episodeTitle("Breaking Bad S01E02 Cat's in the Bag"))
        assertEquals("Pilot", EpisodeMerge.episodeTitle("EN| Lost (2004) S01 E01 - Pilot"))
        assertNull(EpisodeMerge.episodeTitle("Lost S01E01"))
        assertNull(EpisodeMerge.episodeTitle("Fără marcaj"))
        assertEquals("Final", EpisodeMerge.episodeTitle("Serial Episodul 12: Final"))
    }
}
