package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
    @Test fun deadlineCancellationAndEpisodeBoundary() {
        val timer = SleepTimer.after(30, 1000)
        assertFalse(timer.shouldStop(1800999, true))
        assertTrue(timer.shouldStop(1801000, false))
        assertFalse(SleepTimer().shouldStop(Long.MAX_VALUE, true))
        assertFalse(SleepTimer(endOfEpisode = true).shouldStop(0, false))
        assertTrue(SleepTimer(endOfEpisode = true).shouldStop(0, true))
        assertFalse(SleepTimer.after(-3, 0).shouldStop(Long.MAX_VALUE, true))
    }
}
