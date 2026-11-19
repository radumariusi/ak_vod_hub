package io.github.radumariusi.akvodhub.parser.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleParserTest {
    @Test
    fun providerPrefixesPreserveKnownQualityAndLanguage() {
        val title = "The Glass Island"
        for (prefix in listOf("4K-SC", "4K-SC-HDR", "4K-AMZ", "4K-A+", "4K-TOP")) {
            val parsed = TitleParser.parse("$prefix - $title (2026)")
            assertEquals(prefix, title, parsed.title)
            assertEquals(prefix, VideoQuality.UHD, parsed.quality)
            assertEquals(prefix, if (prefix.endsWith("HDR")) HdrFormat.HDR else null, parsed.hdr)
            assertTrue(prefix, parsed.languages.isEmpty())
        }
        for (prefix in listOf("NF-DO", "A+", "D+", "PRMT", "TOP")) {
            val parsed = TitleParser.parse("$prefix - $title")
            assertEquals(prefix, title, parsed.title)
            assertNull(prefix, parsed.quality)
            assertNull(prefix, parsed.hdr)
            assertTrue(prefix, parsed.languages.isEmpty())
        }
        val french = TitleParser.parse("4K-FR - Île de verre (2026)", "FR - FILMS Dolby Audio 4K 3840P")
        assertEquals("Île de verre", french.title)
        assertEquals(VideoQuality.UHD, french.quality)
        assertEquals(listOf("fr"), french.languages)
        assertNull(french.hdr)
    }

    @Test
    fun categoryCompletesMetadataWithoutInventingAudioOrHdr() {
        val group = "MOVIES ³⁸⁴⁰ᵖ Dolby Vision"
        val source = TitleParser.parse("SC - The Glass Island (2026)", group)
        assertEquals(VideoQuality.UHD, source.quality)
        assertEquals(HdrFormat.DOLBY_VISION, source.hdr)
        assertTrue(source.languages.isEmpty())
        assertEquals(VideoQuality.FHD, TitleParser.parse("1080-ES - The Glass Island", group).quality)
        assertEquals(listOf("es"), TitleParser.parse("1080-ES - The Glass Island", "EN - FILMS").languages)
    }

    @Test
    fun onlyRemovesRankingPositionInRankedCategories() {
        val name = "DE-TOP - 26. Insel aus Glas (2026)"
        val ranked = TitleParser.parse(name, "DE - IMDB TOP 100")
        assertEquals("Insel aus Glas", ranked.title)
        assertEquals(listOf("de"), ranked.languages)
        assertNull(ranked.quality)
        assertEquals("26. Insel aus Glas", TitleParser.parse(name, "DE - FILME").title)
        assertEquals("1917", TitleParser.parse("DE-TOP - 1917 (2019)", "DE - IMDB TOP 100").title)
    }

    @Test
    fun readsDeclaredAudioSeparatelyFromSubtitlesAndQuality() {
        for (label in listOf("EN 5.1", "ENG 5.1", "[eng 5.1]", "EN 5.1 MULTI-SUB")) {
            val source = TitleParser.parse("Insula de sticlă $label")
            assertEquals("Insula de sticlă", source.title)
            assertEquals(listOf("en"), source.languages)
            assertEquals(null, source.quality)
        }
        val multi = TitleParser.parse("Insula de sticlă (2024) MULTI", "Filme UHD")
        assertEquals(listOf("mul"), multi.languages)
        assertEquals(VideoQuality.UHD, multi.quality)
        val subOnly = TitleParser.parse("Insula de sticlă (2024) 5.1 MULTI-SUB", "Furnizor8K")
        assertEquals(emptyList<String>(), subOnly.languages)
        assertEquals(listOf("mul"), subOnly.subtitles)
        assertEquals(null, subOnly.quality)
        val both = TitleParser.parse("Insula de sticlă (2024) MULTI-SUB EN 5.1")
        assertEquals(listOf("en"), both.languages)
        assertEquals(listOf("mul"), both.subtitles)
        assertEquals(listOf("mul"), TitleParser.parse("Insula de sticlă", "MULTI | Filme").languages)
    }

    @Test
    fun examplesFromPlan() {
        val a = TitleParser.parse("EN| The Matrix (1999) 4K HDR")
        assertEquals("The Matrix", a.title)
        assertEquals(1999, a.year)
        assertEquals(VideoQuality.UHD, a.quality)
        assertEquals(HdrFormat.HDR, a.hdr)
        assertEquals(listOf("en"), a.languages)

        val b = TitleParser.parse("|DE| Matrix 1999 [MULTI-SUB]")
        assertEquals("Matrix", b.title)
        assertEquals(1999, b.year)
        assertEquals(listOf("de"), b.languages)
        assertEquals(listOf(ParsedTitle.MULTI), b.subtitles)

        val c = TitleParser.parse("RO - Matrix (1999) FHD")
        assertEquals("Matrix", c.title)
        assertEquals(VideoQuality.FHD, c.quality)
        // Secțiunea „RO - ” la furnizorii români înseamnă subtitrare RO, cu audio original.
        assertEquals(listOf(ParsedTitle.ORIGINAL), c.languages)
        assertEquals(listOf("ro"), c.subtitles)
        assertEquals(listOf("tr"), TitleParser.parse("RO - Flori însângerate (2022) (TR)").languages)
        assertEquals(listOf("ro"), TitleParser.parse("RO - Cei trimiși (2025) (RO)").languages)

        val d = TitleParser.parse("Breaking Bad S01E02")
        assertEquals("Breaking Bad", d.title)
        assertEquals(1, d.season)
        assertEquals(2, d.episode)
        assertNull(d.year)
    }

    @Test
    fun languageAndQualityFromCategory() {
        val p = TitleParser.parse("Matrix (1999)", groupTitle = "DE | Filme 4K")
        assertEquals(listOf("de"), p.languages)
        assertEquals(VideoQuality.UHD, p.quality)
    }

    @Test
    fun nameWinsOverCategory() {
        val p = TitleParser.parse("FR| Matrix (1999) 720p", groupTitle = "EN | Movies 4K")
        assertEquals(listOf("fr"), p.languages)
        assertEquals(VideoQuality.HD, p.quality)
    }

    @Test
    fun yearAtStartIsPartOfTitle() {
        assertEquals("1917", TitleParser.parse("1917 (2019)").title)
        assertEquals("Blade Runner 2049", TitleParser.parse("Blade Runner 2049 (2017)").title)
    }

    @Test
    fun neverReturnsEmptyTitle() {
        for (name in listOf("4K", "EN|", "(2019)", "", "   ", "|EN| 4K", "S01E01")) {
            assertTrue("„$name”", TitleParser.parse(name).title.isNotEmpty() || name.isBlank())
        }
    }

    /** Setul mare din `testdata/titles.tsv`; ținta etapei 3: peste 95% parsate complet corect. */
    @Test
    fun realWorldNames() {
        val cases =
            javaClass
                .getResourceAsStream("/testdata/titles.tsv")!!
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
        val failures = mutableListOf<String>()
        for (line in cases) {
            val c = line.split('\t')
            val expected =
                ParsedTitle(
                    title = c[2],
                    year = c[3].toIntOrNull(),
                    season = c[4].toIntOrNull(),
                    episode = c[5].toIntOrNull(),
                    quality = c[6].ifEmpty { null }?.let(VideoQuality::valueOf),
                    hdr = c[7].ifEmpty { null }?.let(HdrFormat::valueOf),
                    codec = c[8].ifEmpty { null }?.let(VideoCodec::valueOf),
                    languages = c[9].split(',').filter { it.isNotEmpty() },
                    subtitles = c[10].split(',').filter { it.isNotEmpty() },
                )
            val actual = TitleParser.parse(c[0], c[1].ifEmpty { null })
            if (actual != expected) failures += "${c[0]} [${c[1]}]\n  așteptat: $expected\n  primit:   $actual"
        }
        val ok = cases.size - failures.size
        println("Parser titluri: $ok/${cases.size} corecte")
        failures.forEach(::println)
        assertTrue("Doar $ok/${cases.size} corecte", ok * 100 >= cases.size * 95)
    }
}
