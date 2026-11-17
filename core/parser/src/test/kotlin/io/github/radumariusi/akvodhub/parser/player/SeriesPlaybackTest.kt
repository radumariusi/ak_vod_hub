package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesPlaybackTest {
    @Test fun stableKeysAndIntroBounds() {
        assertEquals("tmdb_42", SeriesPlayback.key(42, "Serial", 2020))
        assertEquals(SeriesPlayback.key(-1, " Serial ", 2020), SeriesPlayback.key(-2, "serial", 2020))
        assertTrue(SeriesPlayback.canSkip(2000, 1800000, 90000))
        assertFalse(SeriesPlayback.canSkip(90000, 1800000, 90000))
        assertFalse(SeriesPlayback.validIntro(90000, 80000))
        assertFalse(SeriesPlayback.validIntro(700000, 1800000))
        assertTrue(SeriesPlayback.validSetting("series.tmdb_42.intro", "90000"))
        assertFalse(SeriesPlayback.validSetting("series.tmdb_42.intro", "-1"))
        assertFalse(SeriesPlayback.validSetting("arbitrary", "90000"))
        assertTrue(SeriesPlayback.validSetting("series.tmdb_42.text", "off"))
        assertTrue(SeriesPlayback.validSetting("series.tmdb_42.text", "addon:ro"))
        assertTrue(SeriesPlayback.validSetting("series.tmdb_42.audio", "en"))
        assertFalse(SeriesPlayback.validSetting("series.tmdb_42.text", "https://secret"))
        assertFalse(SeriesPlayback.validSetting("series.tmdb_42.audio", "999"))
    }

    @Test
    fun rememberedTracksRespectOffAndFallBackWhenUnavailable() {
        assertEquals("off", SeriesPlayback.subtitleChoice("off", setOf("ro"), setOf("ro")))
        assertEquals("embedded:ro", SeriesPlayback.subtitleChoice("embedded:ro", setOf("ro"), setOf("ro")))
        assertEquals("addon:ro", SeriesPlayback.subtitleChoice("addon:ro", setOf("ro"), setOf("ro")))
        assertEquals("embedded:ro", SeriesPlayback.subtitleChoice("addon:ro", setOf("ro"), emptySet()))
        assertNull(SeriesPlayback.subtitleChoice("addon:ro", setOf("en"), setOf("en")))
        assertEquals(listOf("ro", "en"), SeriesPlayback.audioLanguages("ro", listOf("en", "ro")))
        assertEquals(listOf("en"), SeriesPlayback.audioLanguages(null, listOf("en")))
    }
}
