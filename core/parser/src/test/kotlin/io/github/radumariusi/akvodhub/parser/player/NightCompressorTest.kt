package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertTrue
import org.junit.Test

class NightCompressorTest {
    @Test fun boostsQuietDialogueAndLimitsSuddenPeaks() {
        val compressor = NightCompressor(48000)
        assertTrue(compressor.gain(0.05) > 1.0)
        assertTrue(compressor.gain(1.0) <= 0.95)
        repeat(48000) { compressor.gain(0.9) }
        assertTrue(compressor.gain(0.9) < 1.0)
        assertTrue(compressor.gain(0.0).isFinite())
    }
}
