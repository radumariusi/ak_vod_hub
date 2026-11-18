package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroNeighborsTest {
    @Test
    fun boundsAndNeighborsInAnIncompleteGrid() {
        assertEquals(listOf(1, 4), HeroNeighbors.grid(0, columns = 4, size = 10))
        assertEquals(listOf(6, 4, 9, 1), HeroNeighbors.grid(5, columns = 4, size = 10))
        assertEquals(listOf(6, 9, 3), HeroNeighbors.grid(7, columns = 4, size = 10))
        assertEquals(listOf(8, 5), HeroNeighbors.grid(9, columns = 4, size = 10))
        assertEquals(emptyList<Int>(), HeroNeighbors.grid(-1, columns = 4, size = 10))
    }
}
