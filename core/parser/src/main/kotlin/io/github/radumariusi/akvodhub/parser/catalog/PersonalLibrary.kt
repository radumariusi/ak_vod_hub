package io.github.radumariusi.akvodhub.parser.catalog

object PersonalLibrary {
    /** Marcarea manuală resetează explicit istoricul; nu inventează un episod sau o durată. */
    fun manualProgress(watched: Boolean): Double = if (watched) 1.0 else 0.0

    fun page(afterId: Long, limit: Int): SqlQuery {
        require(afterId >= 0 && limit in 1..100)
        return SqlQuery(
            "SELECT ${CatalogQuery.COLUMNS} FROM watchlist w JOIN title t ON t.id = w.titleId " +
                "WHERE t.sourceCount > 0 AND t.id > ? ORDER BY t.id LIMIT ?",
            listOf(afterId, limit),
        )
    }
}
