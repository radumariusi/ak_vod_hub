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

    /**
     * Tabelul îl creează Room (entitate); trigger-ele se creează la fiecare deschidere (`IF NOT EXISTS`).
     *
     * Nu folosim `INSERT OR IGNORE`: într-un trigger, politica de conflict a instrucțiunii exterioare o
     * înlocuiește, iar Room scrie `UPDATE OR ABORT` (`@Update`). Al doilea rând al aceluiași titlu dădea
     * atunci `SQLiteConstraintException` (importul eșua la reîmprospătare, v0.1.56). Verificăm cu `NOT EXISTS`.
     * Prima versiune (`catalog_dirty_entry`/`catalog_dirty_title`) se șterge.
     */
    val TRIGGERS: List<String> =
        listOf(
            "DROP TRIGGER IF EXISTS catalog_dirty_entry",
            "DROP TRIGGER IF EXISTS catalog_dirty_title",
            "CREATE TRIGGER IF NOT EXISTS catalog_dirty_entry_v2 AFTER UPDATE OF ${entryColumns.joinToString()} " +
                "ON raw_entry WHEN ${changed(entryColumns)} BEGIN " +
                "${mark("OLD.titleId")} ${mark("NEW.titleId")} END",
            "CREATE TRIGGER IF NOT EXISTS catalog_dirty_title_v2 AFTER UPDATE OF ${titleColumns.joinToString()} " +
                "ON title WHEN ${changed(titleColumns)} BEGIN ${mark("NEW.id")} END",
        )

    private fun mark(id: String) =
        "INSERT INTO catalog_dirty(titleId) SELECT $id WHERE $id IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM catalog_dirty WHERE titleId = $id);"

    private fun changed(columns: List<String>) = columns.joinToString(" OR ") { "OLD.$it IS NOT NEW.$it" }
}
