package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleLogoTest {
    @Test
    fun englishBeforeNeutralWithTwoFallbacksPerLanguage() {
        val logos =
            listOf(
                LogoCandidate("/english.png", "en", 1),
                LogoCandidate("/english.svg", "EN", 30),
                LogoCandidate("/english.svg", "en", 29),
                LogoCandidate("/third.png", "en", 0),
                LogoCandidate("/neutral.png", null, 20),
                LogoCandidate("/neutral2.svg", "", 1),
                LogoCandidate("/romanian.png", "ro", 100),
            )

        assertEquals(
            listOf("/english.svg", "/english.png", "/neutral.png", "/neutral2.svg"),
            TitleLogo.orderedPaths(logos),
        )
    }

    @Test
    fun malformedAndUnsupportedPathsCannotReachTheImageLoader() {
        val invalidPaths = listOf("https://example.invalid/logo.svg", "//logo.svg", "/path/logo.png", "/other.gif")
        assertEquals(emptyList<String>(), TitleLogo.orderedPaths(invalidPaths.map { LogoCandidate(it, "en", 10) }))
        assertEquals(listOf("/valid.svg"), TitleLogo.cachedPaths((invalidPaths + "/valid.svg").joinToString("\n")))
    }

    @Test
    fun missingLogoExpiresBeforeSuccessfulLookup() {
        val day = 86_400_000L
        assertTrue(TitleLogo.isCached(0, day - 1, hasLogos = false))
        assertFalse(TitleLogo.isCached(0, day, hasLogos = false))
        assertTrue(TitleLogo.isCached(0, 30 * day - 1, hasLogos = true))
        assertFalse(TitleLogo.isCached(0, 30 * day, hasLogos = true))
    }
}
