package io.github.radumariusi.akvodhub.parser.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionsTest {
    private val matrix =
        listOf(
            CollectionPart(604, "The Matrix Reloaded", 2003, titleId = 2),
            CollectionPart(603, "The Matrix", 1999, titleId = 1),
            CollectionPart(605, "The Matrix Revolutions", 2003, titleId = null),
            CollectionPart(624860, "The Matrix Resurrections", 2021, titleId = 4),
            CollectionPart(999, "The Matrix 5", 2031, titleId = null),
        )

    @Test
    fun ordersByYearAndDropsFarFutureParts() {
        val ordered = Collections.ordered(matrix, maxYear = 2027)
        assertEquals(listOf(603L, 604L, 605L, 624860L), ordered.map { it.tmdbId })
    }

    @Test
    fun summarizesAvailableParts() {
        val ordered = Collections.ordered(matrix, maxYear = 2027)
        assertEquals("Ai 3 din 4 părți · lipsește: The Matrix Revolutions (2003)", Collections.summary(ordered))
        val all = ordered.map { it.copy(titleId = it.tmdbId) }
        assertEquals("Ai toate cele 4 părți", Collections.summary(all))
        val none = ordered.map { it.copy(titleId = null) }
        assertEquals(
            "Ai 0 din 4 părți · lipsesc: The Matrix (1999), The Matrix Reloaded (2003), " +
                "The Matrix Revolutions (2003), +1",
            Collections.summary(none),
        )
    }

    @Test
    fun nextSkipsMissingAndWatchedParts() {
        val ordered = Collections.ordered(matrix, maxYear = 2027)
        assertEquals(2L, Collections.next(ordered, watched = setOf(1))?.titleId)
        // Partea 3 lipsește din playlist: urmează partea 4.
        assertEquals(4L, Collections.next(ordered, watched = setOf(1, 2))?.titleId)
        assertNull(Collections.next(ordered, watched = setOf(1, 2, 4)))
        assertNull(Collections.next(ordered, watched = emptySet()))
    }
}
