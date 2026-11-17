package io.github.radumariusi.akvodhub.parser.catalog

/** Ascunde titlul întreg dacă oricare dintre surse declară o limbă/categorie blocată. */
object ContentVisibility {
    fun tokens(value: String): List<String> = value.split(',').map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }.distinct().take(30)

    fun hiddenTitles(languages: String, categories: String): SqlQuery {
        val terms = mutableListOf<String>()
        val args = mutableListOf<Any>()
        tokens(languages).filter { it.matches(Regex("[a-z]{2,3}")) }.forEach {
            terms += "(',' || r.languages || ',') LIKE ?"
            args += "%,$it,%"
        }
        tokens(categories).forEach {
            terms += "lower(trim(r.groupTitle)) = ?"
            args += it
        }
        return SqlQuery(
            "UPDATE title SET sourceCount = 0 WHERE id IN (SELECT r.titleId FROM raw_entry r WHERE " +
                (terms.joinToString(" OR ").ifEmpty { "0" }) + ")",
            args,
        )
    }
}
