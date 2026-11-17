package io.github.radumariusi.akvodhub.parser

import io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContentVisibilityTest {
    @Test fun boundParametersAndEmptyFilter() {
        val query = ContentVisibility.hiddenTitles("DE, de,fr", "Kids' films,documentare")
        assertEquals(listOf("%,de,%", "%,fr,%", "kids' films", "documentare"), query.args)
        assertFalse(query.sql.contains("Kids"))
        assertEquals(emptyList(), ContentVisibility.hiddenTitles("", "").args)
    }
}
