package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun choosesLighterAdaptiveRenditionAndRespectsCurrentCap() {
        val renditions = listOf(-1, 500_000, 1_200_000, 2_000_000, 3_700_000, 4_000_000)
        assertEquals(2_000_000, PlaybackRecovery.lowerBitrate(4_000_000, Int.MAX_VALUE, renditions))
        assertEquals(1_200_000, PlaybackRecovery.lowerBitrate(4_000_000, 2_000_000, renditions))
        assertNull(PlaybackRecovery.lowerBitrate(500_000, Int.MAX_VALUE, renditions))
    }

    @Test
    fun fhdCanRecoverToLowerQualityWithTheSameAudio() {
        val options =
            listOf(
                SourceInfo(1, VideoQuality.UHD, languages = listOf("en")),
                SourceInfo(2, VideoQuality.HD, languages = listOf("de")),
                SourceInfo(3, VideoQuality.SD, languages = listOf("en")),
                SourceInfo(4, null, languages = listOf("en")),
            )
        assertEquals(3L, PlaybackRecovery.lighterSource(VideoQuality.FHD, listOf("en"), options))
        assertEquals(2L, PlaybackRecovery.lighterSource(VideoQuality.UHD, listOf("de"), options))
        assertNull(PlaybackRecovery.lighterSource(VideoQuality.SD, listOf("en"), options))
    }
}
