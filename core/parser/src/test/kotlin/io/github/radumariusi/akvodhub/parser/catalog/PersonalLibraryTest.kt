package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalLibraryTest {
    @Test
    fun manualSeenAndUnseenHaveNoPartialProgress() {
        assertEquals(1.0, PersonalLibrary.manualProgress(true), 0.0)
        assertEquals(0.0, PersonalLibrary.manualProgress(false), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageRejectsUnboundedLoads() {
        PersonalLibrary.page(0, 111000)
    }
}
