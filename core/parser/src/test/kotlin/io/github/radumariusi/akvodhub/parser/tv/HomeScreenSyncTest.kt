package io.github.radumariusi.akvodhub.parser.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenSyncTest {
    @Test
    fun insertsUpdatesAndDeletes() {
        val plan =
            HomeScreenSync.plan(
                wanted = listOf("1", "2", "3"),
                published = listOf(PublishedRow("2", 20, true), PublishedRow("9", 90, true)),
            )
        assertEquals(listOf("1", "3"), plan.insert)
        assertEquals(mapOf("2" to 20L), plan.update)
        assertEquals(listOf(90L), plan.delete)
    }

    @Test
    fun keepsRowsRemovedByUserHidden() {
        val plan = HomeScreenSync.plan(listOf("1"), listOf(PublishedRow("1", 10, browsable = false)))
        assertEquals(emptyList<String>(), plan.insert)
        assertEquals(emptyMap<String, Long>(), plan.update)
        assertEquals(emptyList<Long>(), plan.delete)
    }

    @Test
    fun removedRowIsDeletedWhenNoLongerWanted() {
        val plan = HomeScreenSync.plan(emptyList(), listOf(PublishedRow("1", 10, browsable = false)))
        assertEquals(listOf(10L), plan.delete)
    }

    @Test
    fun dropsDuplicates() {
        val plan =
            HomeScreenSync.plan(listOf("1"), listOf(PublishedRow("1", 10, true), PublishedRow("1", 11, true)))
        assertEquals(mapOf("1" to 10L), plan.update)
        assertEquals(listOf(11L), plan.delete)
    }
}
