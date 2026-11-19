package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthTest {
    private val now = 10L * SourceHealth.FORGET_MS

    @Test
    fun lastStartedSourceComesFirst() {
        val health = mapOf(3L to SourceHealthRecord(okAt = now - 5), 2L to SourceHealthRecord(okAt = now - 50))
        assertEquals(listOf(3L, 1L, 2L), SourceHealth.reorder(listOf(1L, 2L, 3L), health, now))
    }

    @Test
    fun deadSourcesGoLastUntilForgotten() {
        val dead = SourceHealth.failed(SourceHealth.failed(null, now - 10), now - 5)
        assertTrue(SourceHealth.isDead(dead, now))
        assertEquals(listOf(2L, 3L, 1L), SourceHealth.reorder(listOf(1L, 2L, 3L), mapOf(1L to dead), now))
        assertFalse(SourceHealth.isDead(dead, now + SourceHealth.FORGET_MS))
    }

    @Test
    fun oneFailureIsNotDeadAndStartResets() {
        val once = SourceHealth.failed(null, now)
        assertFalse(SourceHealth.isDead(once, now))
        assertEquals(0, SourceHealth.started(once, now).failures)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val health = mapOf(1L to SourceHealthRecord(5, 0, 0), 2L to SourceHealthRecord(0, 2, 9))
        assertEquals(health, SourceHealth.decode(SourceHealth.encode(health)))
        assertEquals(emptyMap<Long, SourceHealthRecord>(), SourceHealth.decode("bad\n1:x:0:0"))
    }
}
