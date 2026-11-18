package io.github.radumariusi.akvodhub.parser.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoResolutionTest {
    @Test
    fun classifiesCroppedAndStandardVideoFromItsDimensions() {
        assertEquals(VideoQuality.UHD, VideoResolution.quality(3840, 1608))
        assertEquals(VideoQuality.FHD, VideoResolution.quality(1920, 800))
        assertEquals(VideoQuality.HD, VideoResolution.quality(1280, 540))
        assertEquals(VideoQuality.SD, VideoResolution.quality(720, 576))
        assertNull(VideoResolution.quality(0, 1080))
    }

    @Test
    fun onlyAcceptsExplicitResolutionDeclarations() {
        assertEquals(VideoQuality.FHD, VideoResolution.declared("1920 × 1080"))
        assertEquals(VideoQuality.FHD, VideoResolution.declared("Full HD"))
        assertEquals(VideoQuality.UHD, VideoResolution.declared("4k"))
        assertNull(VideoResolution.declared("Unknown"))
        assertNull(VideoResolution.declared("MKV"))
    }
}
