package io.github.radumariusi.akvodhub.parser.catalog

data class NewsCursor(val at: Long, val id: Long)

object PlaylistNews {
    const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    fun page(episodes: Boolean, after: NewsCursor?, limit: Int, now: Long): SqlQuery {
        require(limit in 1..100)
        val column = if (episodes) "newEpisodeAt" else "playlistAddedAt"
        val args = mutableListOf<Any>(now - WINDOW_MS, now)
        val tracked =
            if (episodes) {
                " AND t.mediaType = 'TV' AND (EXISTS(SELECT 1 FROM watchlist w WHERE w.titleId = t.id " +
                    "AND t.newEpisodeAt > w.addedAt) " +
                    "OR EXISTS(SELECT 1 FROM watch_history h WHERE h.titleId = t.id " +
                    "AND t.newEpisodeAt > h.watchedAt) " +
                    "OR EXISTS(SELECT 1 FROM watch_state s WHERE s.titleId = t.id " +
                    "AND t.newEpisodeAt > s.updatedAt))"
            } else {
                ""
            }
        val cursor =
            if (after != null) {
                args.addAll(listOf(after.at, after.at, after.id))
                " AND (t.$column < ? OR (t.$column = ? AND t.id < ?))"
            } else {
                ""
            }
        args += limit
        return SqlQuery(
            "SELECT ${CatalogQuery.COLUMNS}, t.$column AS newsAt FROM title t " +
                "WHERE t.sourceCount > 0 AND t.$column > ? AND t.$column <= ?" + tracked + cursor +
                " ORDER BY t.$column DESC, t.id DESC LIMIT ?",
            args,
        )
    }
}
