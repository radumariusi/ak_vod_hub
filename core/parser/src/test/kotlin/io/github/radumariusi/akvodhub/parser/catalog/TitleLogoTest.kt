package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleLogoTest {
    @Test
    fun languagePriorityThenVotesAndTextFallback() {
        val logos = listOf(LogoCandidate("/en.png", "en", 30), LogoCandidate("/ro.png", "ro", 1))
        assertEquals("/en.png", TitleLogo.choose(logos))
        assertEquals("/neutral.png", TitleLogo.choose(listOf(LogoCandidate("/neutral.png", null, 0))))
        assertEquals(
            "/best.png",
            TitleLogo.choose(listOf(LogoCandidate("/a.png", "en", 1), LogoCandidate("/best.png", "en", 2))),
        )
        assertNull(TitleLogo.choose(listOf(LogoCandidate("/de.png", "de", 20))))
        assertNull(TitleLogo.choose(emptyList()))
    }
}
