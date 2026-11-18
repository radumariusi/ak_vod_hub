package io.github.radumariusi.akvodhub.parser.match

import io.github.radumariusi.akvodhub.parser.title.HdrFormat
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceGroupingTest {
    private var nextId = 1L

    private fun s(
        quality: VideoQuality?,
        vararg languages: String,
        hdr: HdrFormat? = null,
    ) = SourceInfo(nextId++, quality, hdr, languages.toList())

    @Test
    fun groupsByQualityThenLanguage() {
        val sources =
            listOf(
                s(VideoQuality.FHD, "ro"),
                s(VideoQuality.UHD, "de"),
                s(VideoQuality.FHD, "fr"),
                s(VideoQuality.UHD, "en"),
                s(VideoQuality.UHD, "en"),
                s(null),
                s(VideoQuality.FHD, "it"),
                s(VideoQuality.FHD, "es"),
                s(VideoQuality.FHD, "pl"),
                s(VideoQuality.SD, "ro"),
            )
        val groups = SourceGrouping.group(sources)
        assertEquals(listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.SD, null), groups.map { it.quality })
        // EN are 2 surse, deci înaintea DE.
        assertEquals(listOf("en", "de"), groups[0].languages.map { it.language })
        assertEquals(
            "4K: EN, DE · 1080p: ES, FR, IT, +2 · SD: RO · Calitate nedeclarată: Limbă audio necunoscută",
            SourceGrouping.summary(groups),
        )
    }

    @Test
    fun preferredLanguagesComeFirst() {
        val sources =
            listOf(
                s(VideoQuality.FHD, "fr"),
                s(VideoQuality.FHD, "fr"),
                s(VideoQuality.FHD, "ro"),
                s(VideoQuality.FHD, "en"),
            )
        val groups = SourceGrouping.group(sources, preferredLanguages = listOf("ro", "en"))
        assertEquals(listOf("ro", "en", "fr"), groups[0].languages.map { it.language })
        assertEquals(listOf(3L, 4L, 1L, 2L), SourceGrouping.ordered(sources, listOf("ro", "en")).map { it.id })
    }

    @Test
    fun hdrFirstWithinSameLanguage() {
        val plain = s(VideoQuality.UHD, "en")
        val dv = s(VideoQuality.UHD, "en", hdr = HdrFormat.DOLBY_VISION)
        assertEquals(listOf(dv.id, plain.id), SourceGrouping.ordered(listOf(plain, dv)).map { it.id })
    }
}
