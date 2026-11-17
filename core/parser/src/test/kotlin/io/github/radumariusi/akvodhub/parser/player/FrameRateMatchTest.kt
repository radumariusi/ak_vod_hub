package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatchTest {
    @Test fun selectsMultiplesAndRejectsUnknown() {
        assertEquals(1, FrameRateMatch.choose(23.976f, listOf(60f, 23.976f, 47.952f)))
        assertEquals(0, FrameRateMatch.choose(25f, listOf(50f, 60f)))
        assertNull(FrameRateMatch.choose(24f, listOf(50f, 60f)))
        assertNull(FrameRateMatch.choose(Float.NaN, listOf(24f)))
        assertNull(FrameRateMatch.choose(-1f, listOf(24f)))
        assertNull(FrameRateMatch.choose(23.976f, listOf(24f, 60f)))
        assertEquals(1, FrameRateMatch.choose(24f, listOf(23.976f, 48f)))
    }
}
