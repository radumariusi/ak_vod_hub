package io.github.radumariusi.akvodhub.parser.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test
    fun parsesSrtWithTagsAndCrLf() {
        val srt =
            "﻿1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Bună ziua!</i>\r\n{\\an8}Ce faci?\r\n\r\n" +
                "2\r\n00:00:04,000 --> 00:00:05,000\r\nBine.\r\n"
        val doc = SubtitleParser.parse(srt)
        assertEquals(2, doc.cues.size)
        assertEquals(SubtitleCue(1_000, 3_500, "Bună ziua!\nCe faci?"), doc.cues[0])
        assertEquals("", doc.textAt(999))
        assertEquals("Bună ziua!\nCe faci?", doc.textAt(1_000))
        assertEquals("", doc.textAt(3_500))
        assertEquals("Bine.", doc.textAt(4_500))
    }

    @Test
    fun parsesSrtWithoutBlankLinesAndMissingNumbers() {
        val srt = "00:00:01,000 --> 00:00:02,000\nUnu\n00:00:02,000 --> 00:00:03,000\nDoi\n"
        val doc = SubtitleParser.parse(srt)
        assertEquals(listOf("Unu", "Doi"), doc.cues.map { it.text })
    }

    @Test
    fun parsesWebVtt() {
        val vtt =
            "WEBVTT\n\nNOTE comentariu\n\n00:01.000 --> 00:02.000 align:start\nTom &amp; Jerry\n\n" +
                "id-2\n01:00:00.000 --> 01:00:01.000\n<v Ana>Salut</v>\n"
        val doc = SubtitleParser.parse(vtt)
        assertEquals(SubtitleCue(1_000, 2_000, "Tom & Jerry"), doc.cues[0])
        assertEquals(SubtitleCue(3_600_000, 3_601_000, "Salut"), doc.cues[1])
    }

    @Test
    fun parsesAss() {
        val ass =
            """
            [Script Info]
            Title: x

            [V4+ Styles]
            Format: Name, Fontname
            Style: Default,Arial

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\i1}Primul rând\Nal doilea, cu virgulă
            Comment: 0,0:00:04.00,0:00:05.00,Default,,0,0,0,,ignorat
            """.trimIndent()
        val doc = SubtitleParser.parse(ass)
        assertEquals(1, doc.cues.size)
        assertEquals(SubtitleCue(1_500, 3_000, "Primul rând\nal doilea, cu virgulă"), doc.cues[0])
    }

    @Test
    fun overlappingCuesAreShownTogether() {
        val doc =
            SubtitleDocument(
                listOf(
                    SubtitleCue(0, 10_000, "Lung"),
                    SubtitleCue(2_000, 3_000, "Scurt"),
                    SubtitleCue(4_000, 5_000, "Altul"),
                ),
            )
        assertEquals("Lung\nScurt", doc.textAt(2_500))
        assertEquals("Lung\nAltul", doc.textAt(4_500))
        assertEquals("Lung", doc.textAt(9_000))
        assertEquals("", doc.textAt(10_000))
    }

    @Test
    fun parsesTimes() {
        assertEquals(3_723_456L, SubtitleParser.parseTime("01:02:03,456"))
        assertEquals(123_400L, SubtitleParser.parseTime("02:03.4"))
        assertEquals(3_723_450L, SubtitleParser.parseTime("1:02:03.45"))
        assertNull(SubtitleParser.parseTime("abc"))
        assertTrue(SubtitleParser.parse("nimic aici").isEmpty)
    }

    @Test
    fun decodesLegacyRomanian() {
        val cp1250 = "Ştii că ţara".toByteArray(charset("windows-1250"))
        assertEquals("Ştii că ţara", SubtitleCharset.decode(cp1250, "ro"))
        val utf8 = "Știi că țara".toByteArray(Charsets.UTF_8)
        assertEquals("Știi că țara", SubtitleCharset.decode(utf8, "ro"))
        assertEquals("ªtii cã þara".let(SubtitleCharset::fixRomanian), "Știi că țara")
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hi".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hi", SubtitleCharset.decode(utf16, "en"))
        assertEquals("Café", SubtitleCharset.decode("Café".toByteArray(charset("windows-1252")), "fr"))
    }
}
