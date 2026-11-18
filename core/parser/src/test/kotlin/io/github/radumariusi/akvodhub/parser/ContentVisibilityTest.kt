package io.github.radumariusi.akvodhub.parser

import io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContentVisibilityTest {
    @Test fun boundParametersAndEmptyFilter() {
        val query = ContentVisibility.hiddenSources("DE, de,fr", "Kids' films,documentare")
        assertEquals(listOf("%,de,%", "%,fr,%", "Kids' films", "documentare"), query.args)
        assertFalse(query.sql.contains("Kids"))
        assertEquals(emptyList<Any>(), ContentVisibility.hiddenSources("", "").args)
    }
}
