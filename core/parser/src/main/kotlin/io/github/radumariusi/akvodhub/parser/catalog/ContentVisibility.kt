package io.github.radumariusi.akvodhub.parser.catalog

/** Aplică ascunderea limbii pe variante și ascunderea explicită a categoriilor pe titluri. */
object ContentVisibility {
    fun tokens(value: String, lowercase: Boolean = true): List<String> =
        value
            .split(',')
            .map { it.trim().let { text -> if (lowercase) text.lowercase() else text } }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(30)

    fun hiddenSources(languages: String, categories: String): SqlQuery {
        val terms = mutableListOf<String>()
        val args = mutableListOf<Any>()
        tokens(languages).filter { it.matches(Regex("[a-z]{2,3}")) }.forEach {
            terms += "(',' || languages || ',') LIKE ?"
            args += "%,$it,%"
        }
        val blockedCategories = tokens(categories, lowercase = false)
        if (blockedCategories.isNotEmpty()) {
            val placeholders = blockedCategories.joinToString(",") { "?" }
            terms += "titleId IN (SELECT titleId FROM raw_entry WHERE trim(groupTitle) IN ($placeholders))"
            args.addAll(blockedCategories)
        }
        return SqlQuery(
            "UPDATE raw_entry SET visible = 0 WHERE visible = 1 AND (" +
                terms.joinToString(" OR ").ifEmpty { "0" } + ")",
            args,
        )
    }
}
