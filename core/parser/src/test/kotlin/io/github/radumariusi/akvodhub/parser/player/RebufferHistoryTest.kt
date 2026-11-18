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
        assertFalse(history.ready(5_000))
        history.buffering(7_000)
        assertFalse(history.ready(9_000))
        history.buffering(12_000)
        assertTrue(history.ready(14_000))
        history.buffering(15_000)
        assertFalse(history.ready(17_000))
        history.buffering(90_000)
        assertFalse(history.ready(92_000))
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
