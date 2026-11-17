package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerKeyPressTest {
    @Test
    fun aHeldKeyTogglesOncePerPressEvenWhenKeyUpHasZeroRepeats() {
        val events = listOf(true to 0, true to 1, true to 2, false to 0, true to 0, false to 0)
        assertEquals(
            listOf(true, false, false, false, true, false),
            events.map { (down, repeats) -> PlayerKeyPress.activate(down, repeats) },
        )
    }
}
