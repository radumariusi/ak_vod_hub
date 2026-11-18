package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class PageWindowTest {
    @Test
    fun scrollsThroughLargeCatalogAndBackWithBoundedCards() {
        val size = 111_000
        val pageSize = 48
        val maxPages = 5
        val window = PageWindow<Int, Int>(maxPages)
        val fetch: suspend (Int?) -> KeysetPage<Int, Int> = { after ->
            val start = (after ?: -1) + 1
            val end = (start + pageSize).coerceAtMost(size)
            KeysetPage((start until end).toList(), (end - 1).takeIf { end < size })
        }
        var seen = 0
        runImmediately {
            while (window.hasLater) {
                window.move(fetch = fetch)
                val current = window.items
                assertTrue(current.size <= pageSize * maxPages)
                assertEquals(current.distinct(), current)
                assertTrue(seen in current)
                seen = current.last() + 1
            }
            assertEquals(size, seen)
            assertTrue(window.hasEarlier)
            while (window.hasEarlier) {
                window.move(backwards = true, fetch = fetch)
                assertTrue(window.items.size <= pageSize * maxPages)
            }
        }
        assertFalse(window.hasEarlier)
        assertTrue(window.hasLater)
        assertEquals((0 until pageSize * maxPages).toList(), window.items)
    }

    private fun runImmediately(block: suspend () -> Unit) {
        var result: Result<Unit>? = null
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(outcome: Result<Unit>) {
                    result = outcome
                }
            },
        )
        checkNotNull(result).getOrThrow()
    }
}
