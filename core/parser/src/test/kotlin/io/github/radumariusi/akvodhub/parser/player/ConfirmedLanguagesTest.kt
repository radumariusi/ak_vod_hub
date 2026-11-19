package io.github.radumariusi.akvodhub.parser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmedLanguagesTest {
    @Test
    fun convertsAndRequiresEveryTrackToBeKnown() {
        assertEquals("ro,en", ConfirmedLanguages.of(listOf("rum", "eng", "en")))
        assertEquals("tr", ConfirmedLanguages.of(listOf("tur")))
        assertNull(ConfirmedLanguages.of(listOf("eng", null)))
        assertNull(ConfirmedLanguages.of(emptyList()))
    }
}
