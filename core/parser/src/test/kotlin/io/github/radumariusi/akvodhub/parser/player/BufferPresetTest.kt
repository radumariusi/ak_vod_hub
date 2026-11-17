package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.*
import org.junit.Test

class BufferPresetTest {
    @Test fun unknownBackupValuesUseSafeDefaultAndMemoryIsBounded() {
        assertEquals(BufferPreset.NORMAL, BufferPreset.decode("future"))
        assertEquals(BufferPreset.NORMAL, BufferPreset.decode(null))
        BufferPreset.entries.forEach {
            assertTrue(it.minMs >= it.playMs && it.minMs >= it.rebufferMs)
            assertTrue(it.maxMs >= it.minMs)
            assertTrue(it.bytes in 1..64 * 1024 * 1024)
        }
    }
}
