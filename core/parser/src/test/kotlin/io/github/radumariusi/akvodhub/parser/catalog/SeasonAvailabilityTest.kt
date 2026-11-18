package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonAvailabilityTest {
    @Test
    fun absenceRequiresACompletedPlaylistCheck() {
        val empty = MergedSeason(3, emptyList())
        assertEquals(SeasonAvailability.UNKNOWN, SeasonAvailability.of(empty, "en", checked = false))
        assertEquals(SeasonAvailability.MISSING, SeasonAvailability.of(empty, "en", checked = true))
    }

    @Test
    fun explicitLanguagesAndUnknownTracksStayDistinct() {
        fun status(vararg languages: List<String>): SeasonAvailability {
            val sources = languages.mapIndexed { index, codes -> SourceInfo(index.toLong(), null, languages = codes) }
            return SeasonAvailability.of(
                MergedSeason(1, listOf(MergedEpisode(1, 1, null, sources))),
                "ja",
                checked = true,
            )
        }
        assertEquals(SeasonAvailability.OTHER_AUDIO, status(listOf("de"), listOf("es")))
        assertEquals(SeasonAvailability.UNDECLARED_AUDIO, status(listOf("de"), listOf("mul")))
        assertEquals(SeasonAvailability.UNDECLARED_AUDIO, status(emptyList()))
        assertEquals(SeasonAvailability.AVAILABLE, status(listOf("de"), listOf("ro")))
        assertEquals(SeasonAvailability.AVAILABLE, status(listOf("ja")))
    }
}
