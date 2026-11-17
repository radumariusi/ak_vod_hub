package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterBadgesTest {
    @Test
    fun choicesRoundTripIncludingAllHiddenAndUnknownFutureFlags() {
        assertEquals(PosterBadges.defaults, PosterBadges.decode(null))
        assertEquals(emptySet<PosterBadge>(), PosterBadges.decode(PosterBadges.encode(emptySet())))
        val selected = setOf(PosterBadge.IMDB, PosterBadge.PROGRESS)
        assertEquals(selected, PosterBadges.decode(PosterBadges.encode(selected)))
        assertEquals(setOf(PosterBadge.DUB), PosterBadges.decode("FUTURE,DUB"))
    }

    @Test
    fun ratingLabelsAreRomanianAndUnknownOrInvalidRatingsStayHidden() {
        assertEquals("IMDb 8,7", PosterBadges.rating("IMDb", 8.7))
        assertEquals("★ 7,8", PosterBadges.rating("★", 7.8))
        for (value in listOf(null, 0.0, -1.0, 11.0, Double.NaN)) assertNull(PosterBadges.rating("IMDb", value))
        val rank = listOf("HDR10", "DOLBY_VISION", null).maxOf { PosterBadges.hdrRank(it) }
        assertEquals("DV", PosterBadges.hdrLabel(rank))
        assertNull(PosterBadges.hdrLabel(0))
    }

    @Test
    fun newBadgeExpiresAndDoesNotAcceptFutureOrMissingDates() {
        val now = 2 * PosterBadges.NEW_WINDOW_MS
        assertTrue(PosterBadges.isNew(now - 1, now))
        assertFalse(PosterBadges.isNew(now - PosterBadges.NEW_WINDOW_MS, now))
        assertFalse(PosterBadges.isNew(now + 1, now))
        assertFalse(PosterBadges.isNew(null, now))
    }

    @Test
    fun progressIsSafeForZeroDurationRewindingAndCompletedPlayback() {
        assertEquals(0.5, PosterBadges.progress(50, 100), 0.0)
        assertEquals(0.0, PosterBadges.progress(50, 0), 0.0)
        assertEquals(0.0, PosterBadges.progress(-10, 100), 0.0)
        assertEquals(1.0, PosterBadges.progress(150, 100), 0.0)
    }
}
