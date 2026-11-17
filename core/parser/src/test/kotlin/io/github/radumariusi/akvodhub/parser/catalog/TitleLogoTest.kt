package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleLogoTest {
    @Test
    fun languagePriorityThenVotesAndTextFallback() {
        val logos = listOf(LogoCandidate("/en.png", "en", 30), LogoCandidate("/ro.png", "ro", 1))
        assertEquals("/ro.png", TitleLogo.choose(logos, "en"))
        assertEquals("/neutral.png", TitleLogo.choose(listOf(LogoCandidate("/neutral.png", null, 0)), "fr"))
        assertEquals(
            "/best.png",
            TitleLogo.choose(listOf(LogoCandidate("/a.png", "en", 1), LogoCandidate("/best.png", "en", 2)), null),
        )
        assertNull(TitleLogo.choose(listOf(LogoCandidate("/de.png", "de", 20)), "fr"))
        assertNull(TitleLogo.choose(emptyList(), "en"))
    }
}
