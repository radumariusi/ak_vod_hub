package io.github.radumariusi.akvodhub.parser

import io.github.radumariusi.akvodhub.parser.player.PlaybackDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDefaultsTest {
    @Test fun invalidBackupValuesHaveSafeDefaults() {
        assertEquals(1f, PlaybackDefaults.speed(Float.NaN))
        assertEquals(1f, PlaybackDefaults.speed(50f))
        assertEquals(0.75f, PlaybackDefaults.speed(0.75f))
        assertEquals(120_000L, PlaybackDefaults.offset(Long.MAX_VALUE))
        assertEquals(-120_000L, PlaybackDefaults.offset(Long.MIN_VALUE))
    }
}
