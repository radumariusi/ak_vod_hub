package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DubLabelTest {
    private fun label(original: String?, vararg sources: String?): String? =
        DubLabel(original).apply { sources.forEach(::addSource) }.label()

    @Test
    fun onlyDeclaredForeignAudioIsDubbed() {
        assertEquals("DUB DE", label("en", "de", "de"))
        assertEquals("DUB DE / RO", label("en", "ro,de", "de"))
        assertEquals("DUB EN", label("ja", "en"))
        assertEquals("DUB DE", label(" EN ", " DE "))
    }

    @Test
    fun originalOrMultiSuppressesLabel() {
        assertNull(label("en", "de", "en"))
        assertNull(label("en", "de,en"))
        assertNull(label("en", "de", "mul"))
    }

    @Test
    fun unknownLanguageMustNeverProduceFalseBadge() {
        for (unknown in listOf(null, "", " ", "und", "zz")) {
            assertNull(label(unknown, "de"))
            assertNull(label("en", "de", unknown))
        }
        assertNull(label("mul", "de"))
        assertNull(label("en", "de,"))
        assertNull(label("en"))
    }

    @Test
    fun laterSourcesCanRemoveAnEarlierDubConclusion() {
        val result = DubLabel("en")
        repeat(100_000) { result.addSource("de") }
        assertEquals("DUB DE", result.label())
        result.addSource(null)
        assertNull(result.label())
    }

    @Test
    fun originalMarkerCountsAsOriginalAudio() {
        val label = DubLabel("en")
        label.addSource("ori")
        label.addSource("ro")
        assertEquals(null, label.label())
        assertEquals(true, label.hasOriginalAudio)
    }
}
