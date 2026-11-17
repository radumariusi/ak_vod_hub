package io.github.radumariusi.akvodhub.parser

import io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class ContentVisibilityTest {
    @Test fun boundParametersAndEmptyFilter() {
        val query = ContentVisibility.hiddenTitles("DE, de,fr", "Kids' films,documentare")
        assertEquals(listOf("%,de,%", "%,fr,%", "kids' films", "documentare"), query.args)
        assertFalse(query.sql.contains("Kids"))
        assertEquals(emptyList(), ContentVisibility.hiddenTitles("", "").args)
    }
}
