package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistNewsTest {
    @Test
    fun tiesUseIdAndWindowExcludesFutureDates() {
        val now = 1234567890L
        val query = PlaylistNews.page(false, NewsCursor(now - 1, 42), 24, now)
        assertEquals(listOf<Any>(now - PlaylistNews.WINDOW_MS, now, now - 1, now - 1, 42L, 24), query.args)
        assertTrue(query.sql.contains("t.playlistAddedAt = ? AND t.id < ?"))
        assertFalse(query.sql.contains("OFFSET"))
    }

    @Test
    fun episodesRequireFollowingAndAvailableSources() {
        val sql = PlaylistNews.page(true, null, 24, 1000).sql
        assertTrue(sql.contains("t.sourceCount > 0"))
        assertTrue(sql.contains("watchlist"))
        assertTrue(sql.contains("watch_history"))
        assertTrue(sql.contains("newEpisodeAt"))
    }
}
