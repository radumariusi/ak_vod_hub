package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ImdbRatingsTest {
    private val header = "tconst\taverageRating\tnumVotes"

    @Test
    fun readsOfficialColumnsAndSkipsInvalidRows() {
        val input =
            "$header\n" +
                listOf(
                    "tt0133093\t8.7\t2100000",
                    "tt0903747\t9.5\t2200000",
                    "invalid\t8.0\t12",
                    "tt1234567\tNaN\t12",
                    "tt1234567\t11\t12",
                    "tt1234567\t8.0\t-1",
                    "tt1234567\t8.0\t0",
                    "tt1234567\t8.0\t9999999999999",
                    "tt1234567\t8.0\t12\textra",
                ).joinToString("\n")
        assertEquals(
            listOf(ImdbRating("tt0133093", 8.7, 2100000), ImdbRating("tt0903747", 9.5, 2200000)),
            ImdbRatings.read(input.reader().buffered()).toList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlOrWrongColumnsInsteadOfTreatingDownloadAsSuccessful() {
        ImdbRatings.read("<html>eroare</html>".reader().buffered()).count()
    }

    @Test
    fun iteratorDoesNotReadTheWholeDatasetAhead() {
        var lines = 0
        val reader =
            object : BufferedReader(StringReader("")) {
                override fun readLine(): String {
                    lines++
                    return when (lines) {
                        1 -> header
                        2 -> "tt0133093\t8.7\t2100000"
                        else -> error("Citire anticipată a întregului fișier")
                    }
                }
            }
        assertEquals(
            "tt0133093",
            ImdbRatings
                .read(reader)
                .take(1)
                .single()
                .imdbId,
        )
        assertEquals(2, lines)
    }

    @Test
    fun refreshIsWeeklyAndRecoversFromFutureClockTimestamp() {
        val now = 2 * ImdbRatings.WEEK_MS
        assertTrue(ImdbRatings.isDue(null, now))
        assertFalse(ImdbRatings.isDue(now - ImdbRatings.WEEK_MS + 1, now))
        assertTrue(ImdbRatings.isDue(now - ImdbRatings.WEEK_MS, now))
        assertTrue(ImdbRatings.isDue(now + 1, now))
    }
}
