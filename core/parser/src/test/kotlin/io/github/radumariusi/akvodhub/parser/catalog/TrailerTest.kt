package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerTest {
    @Test
    fun rejectsOtherSitesTeasersAndUnsafeKeys() {
        val videos =
            listOf(
                TrailerCandidate("abcdefghijk", "Vimeo", "Trailer", true, "en"),
                TrailerCandidate("abcdefghijk", "YouTube", "Teaser", true, "en"),
                TrailerCandidate("https://example.org/", "YouTube", "Trailer", true, "en"),
            )
        assertNull(Trailer.choose(videos, "en"))
    }

    @Test
    fun romanianThenEnglishAndOfficialWithinEachLanguage() {
        val english = TrailerCandidate("abcdefghijk", "YouTube", "Trailer", false, "en")
        val official = english.copy(key = "123456789_-", official = true)
        val romanian = english.copy(key = "ABCDEFGHIJK", language = "ro")
        assertEquals(romanian.key, Trailer.choose(listOf(english, official, romanian), "fr"))
        assertEquals(official.key, Trailer.choose(listOf(english, official), "fr"))
    }
}
