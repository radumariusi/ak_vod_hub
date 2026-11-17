package io.github.radumariusi.akvodhub.parser.catalog

/** Ascunde titlul întreg dacă oricare dintre surse declară o limbă/categorie blocată. */
object ContentVisibility {
    fun tokens(value: String, lowercase: Boolean = true): List<String> =
        value
            .split(',')
            .map { it.trim().let { text -> if (lowercase) text.lowercase() else text } }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(30)

    fun hiddenTitles(languages: String, categories: String): SqlQuery {
        val terms = mutableListOf<String>()
        val args = mutableListOf<Any>()
        tokens(languages).filter { it.matches(Regex("[a-z]{2,3}")) }.forEach {
            terms += "(',' || r.languages || ',') LIKE ?"
            args += "%,$it,%"
        }
        tokens(categories, lowercase = false).forEach {
            terms += "trim(r.groupTitle) = ?"
            args += it
        }
        return SqlQuery(
            "UPDATE title SET sourceCount = 0 WHERE id IN (SELECT r.titleId FROM raw_entry r WHERE " +
                (terms.joinToString(" OR ").ifEmpty { "0" }) + ")",
            args,
        )
    }
}
