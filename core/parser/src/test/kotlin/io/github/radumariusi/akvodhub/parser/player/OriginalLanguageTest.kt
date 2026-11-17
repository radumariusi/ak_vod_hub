package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.catalog.DubLabel
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalLanguageTest {
    private val dubbed = SourceInfo(1, VideoQuality.UHD, languages = listOf("de"))
    private val original = SourceInfo(2, VideoQuality.FHD, languages = listOf("ja"))
    private val multi = SourceInfo(3, VideoQuality.UHD, languages = listOf("mul"))
    private val unknown = SourceInfo(4, VideoQuality.SD)
    private val sources = listOf(dubbed, original, multi, unknown)
    private val profile = SourceProfile(listOf(SourceRule(VideoQuality.UHD, "de")))

    @Test
    fun originalAudioOverridesQualityAndProfileThenKeepsFallbacks() {
        val ordered = profile.order(sources, originalLanguage = "ja")
        assertEquals(listOf(2L, 3L, 1L, 4L), ordered.map { it.id })
        assertEquals(sources.toSet(), ordered.toSet())
    }

    @Test
    fun disabledOrUnknownOriginalKeepsExistingProfile() {
        val normal = profile.order(sources)
        assertEquals(1L, normal.first().id)
        for (unknownLanguage in listOf(null, "", "und", "mul", "zz")) {
            assertEquals(normal, profile.order(sources, originalLanguage = unknownLanguage))
        }
    }

    @Test
    fun multipleDeclaredTracksCanConfirmOriginalEvenAfterUnknownSource() {
        val result = DubLabel(" JA ")
        result.addSource(null)
        result.addSource("de,ja")
        assertTrue(result.hasOriginalAudio)
        assertEquals(null, result.label())
    }

    @Test
    fun multiOrUnknownOriginalDoesNotConfirmOriginalForStrictFilter() {
        val multiOnly = DubLabel("en").apply { addSource("mul") }
        assertFalse(multiOnly.hasOriginalAudio)
        assertFalse(DubLabel(null).apply { addSource("en") }.hasOriginalAudio)
        assertFalse(DubLabel("en").apply { addSource("de") }.hasOriginalAudio)
    }

    @Test
    fun normalizesCodesAndFindsOriginalAmongMultipleTracks() {
        val both = SourceInfo(5, VideoQuality.HD, languages = listOf("de", " JA "))
        assertEquals(both, SourceProfile().order(listOf(dubbed, both), originalLanguage = "JA").first())
    }
}
