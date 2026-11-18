package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebufferHistoryTest {
    @Test
    fun repeatedDelaysTriggerRecoveryButSmallOrIsolatedDelaysDoNot() {
        val history = RebufferHistory()
        history.buffering(0)
        assertFalse(history.ready(300))
        history.buffering(3_000)
        assertFalse(history.ready(3_800))
        history.buffering(7_000)
        assertFalse(history.ready(7_700))
        history.buffering(12_000)
        assertTrue(history.ready(12_600))
        assertEquals(3, history.totalCount)
        assertEquals(2_100L, history.totalDurationMs)
        history.buffering(15_000)
        assertFalse(history.ready(17_000))
        history.buffering(90_000)
        assertFalse(history.ready(92_000))
    }

    @Test
    fun twoCostlyStallsTriggerRecoveryButOldStallsDoNot() {
        val history = RebufferHistory()
        history.buffering(0)
        assertFalse(history.ready(2_100))
        history.buffering(40_000)
        assertFalse(history.ready(42_100))
        history.buffering(45_000)
        assertTrue(history.ready(46_900))
    }

    @Test
    fun resetsAfterPauseAndIgnoresBufferingAfterSeek() {
        val history = RebufferHistory()
        history.buffering(100)
        history.reset(ignoreBefore = 6_000)
        assertFalse(history.ready(1_500))
        history.buffering(2_000)
        assertFalse(history.ready(4_000))
        history.buffering(6_000)
        assertFalse(history.ready(8_000))
    }

    @Test
    fun preferredFallbackMustBeAvailableAndSkipsFailedSources() {
        val queue = FallbackQueue(listOf(1, 2, 3))
        assertEquals(3L, queue.fail("buffer repetat", preferred = 3))
        assertEquals(2L, queue.fail("buffer repetat", preferred = 1))
    }
}
