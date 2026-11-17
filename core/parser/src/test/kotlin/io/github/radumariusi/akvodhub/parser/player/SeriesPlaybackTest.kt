package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.*
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
    }
}
