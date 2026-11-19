package io.github.radumariusi.akvodhub.parser.catalog

/**
 * Reindexare incrementală: trigger-ele SQLite notează în `catalog_dirty` titlurile ale căror surse sau date
 * folosite de index s-au schimbat (potrivire TMDB, calitate confirmată la redare, nume/notă actualizate).
 * Indexerul reface apoi doar acele titluri, nu tot catalogul (≈111.000 de titluri).
 *
 * Coloana `visible` lipsește intenționat: o scrie indexerul însuși. Importul (rânduri noi/șterse, `seenRun`)
 * schimbă amprenta structurală și duce oricum la reindexarea completă.
 */
object CatalogDirty {
    const val TABLE = "catalog_dirty"

    /** Subinterogare pentru filtrare: `… AND titleId IN $IDS`. */
    const val IDS = "(SELECT titleId FROM catalog_dirty)"

    private val entryColumns =
        listOf("titleId", "quality", "languages", "hdr", "title", "groupTitle", "season", "episode", "providerAddedAt")
    private val titleColumns = listOf("name", "originalName", "originalLanguage", "rating")

    /** Tabelul îl creează Room (entitate); trigger-ele se creează la fiecare deschidere (`IF NOT EXISTS`). */
    val TRIGGERS: List<String> =
        listOf(
            "CREATE TRIGGER IF NOT EXISTS catalog_dirty_entry AFTER UPDATE OF ${entryColumns.joinToString()} " +
                "ON raw_entry WHEN ${changed(entryColumns)} BEGIN " +
                "INSERT OR IGNORE INTO catalog_dirty(titleId) SELECT OLD.titleId WHERE OLD.titleId IS NOT NULL; " +
                "INSERT OR IGNORE INTO catalog_dirty(titleId) SELECT NEW.titleId WHERE NEW.titleId IS NOT NULL; END",
            "CREATE TRIGGER IF NOT EXISTS catalog_dirty_title AFTER UPDATE OF ${titleColumns.joinToString()} " +
                "ON title WHEN ${changed(titleColumns)} BEGIN " +
                "INSERT OR IGNORE INTO catalog_dirty(titleId) VALUES (NEW.id); END",
        )

    private fun changed(columns: List<String>) = columns.joinToString(" OR ") { "OLD.$it IS NOT NEW.$it" }
}
