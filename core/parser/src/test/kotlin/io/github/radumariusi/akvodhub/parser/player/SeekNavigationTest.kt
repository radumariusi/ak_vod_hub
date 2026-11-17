package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekNavigationTest {
    @Test fun accelerationClampingAndMinuteValidation() {
        assertEquals(
            listOf(10000L, 30000L, 60000L, 300000L),
            listOf(0L, 1000L, 3000L, 6000L).map(SeekNavigation::increment),
        )
        assertEquals(0L, SeekNavigation.target(100, -10000, 500))
        assertEquals(99000L, SeekNavigation.target(50000, Long.MAX_VALUE, 100000))
        assertNull(SeekNavigation.target(0, 10000, -1))
        assertEquals(120000L, SeekNavigation.minute("2", 180000))
        assertNull(SeekNavigation.minute("-1", 180000))
        assertNull(SeekNavigation.minute("3", 180000))
        assertNull(SeekNavigation.minute(Long.MAX_VALUE.toString(), 180000))
    }
}
