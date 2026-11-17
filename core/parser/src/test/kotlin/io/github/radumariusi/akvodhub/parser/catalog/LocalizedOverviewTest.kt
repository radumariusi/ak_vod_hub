package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedOverviewTest {
    @Test
    fun romanianWinsAndMissingTranslationKeepsEnglishWithoutAnotherRequest() {
        assertEquals("Descriere română", LocalizedOverview.choose("  Descriere română  ", "English overview"))
        assertEquals("English overview", LocalizedOverview.choose(" ", "English overview"))
        assertNull(LocalizedOverview.choose(null, " "))
    }

    @Test
    fun absentTranslationCanBeCachedAndRecheckedAfterThirtyDays() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertTrue(LocalizedOverview.isCached(now - 1, now))
        assertFalse(LocalizedOverview.isCached(0, now))
        assertFalse(LocalizedOverview.isCached(now + 1, now))
        assertFalse(LocalizedOverview.isCached(now - 30L * 24 * 60 * 60 * 1000, now))
    }
}
