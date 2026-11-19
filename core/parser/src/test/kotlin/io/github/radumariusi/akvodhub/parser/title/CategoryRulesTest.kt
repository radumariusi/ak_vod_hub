package io.github.radumariusi.akvodhub.parser.title

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {
    @After
    fun reset() {
        CategoryRules.rules = emptyMap()
    }

    @Test
    fun rulesOverrideAutomaticDetection() {
        assertEquals(listOf(ParsedTitle.ORIGINAL), TitleParser.parse("RO - Burebista (2025)", "RO - MOVIES").languages)

        CategoryRules.rules = mapOf("RO - MOVIES" to CategoryMode.DUBBED, "DE - FILME" to CategoryMode.ORIGINAL)
        val dubbed = TitleParser.parse("RO - Flori însângerate (2022) (TR)", " RO - MOVIES ")
        assertEquals(listOf("ro"), dubbed.languages)
        assertEquals(emptyList<String>(), dubbed.subtitles)

        val original = TitleParser.parse("DE - Matrix (1999)", "DE - FILME")
        assertEquals(listOf(ParsedTitle.ORIGINAL), original.languages)
        assertEquals(listOf("de"), original.subtitles)

        // Audio scris explicit în nume rămâne.
        assertEquals(listOf("ro"), TitleParser.parse("Matrix (1999) DUBLAT", "DE - FILME").languages)
    }

    @Test
    fun encodesAndDecodes() {
        val rules = mapOf("RO - MOVIES" to CategoryMode.DUBBED, "X" to CategoryMode.AUTO)
        assertEquals(mapOf("RO - MOVIES" to CategoryMode.DUBBED), CategoryRules.decode(CategoryRules.encode(rules)))
    }
}
