package io.github.radumariusi.akvodhub.parser.catalog

/** Aplică ascunderea limbii pe variante și ascunderea explicită a categoriilor pe titluri. */
object ContentVisibility {
    const val SOURCE_OPTIONS =
        "WITH RECURSIVE options(titleId, quality, language, originalAudio, rest) AS (" +
            "SELECT t.id, COALESCE(r.quality, ''), '', CASE WHEN t.originalLanguage IS NOT NULL AND " +
            "instr(',' || r.languages || ',', ',' || lower(t.originalLanguage) || ',') > 0 THEN 1 ELSE 0 END, " +
            "COALESCE(r.languages || ',', '') FROM raw_entry r JOIN title t ON t.id = r.titleId " +
            "WHERE r.visible = 1 " +
            "UNION ALL SELECT titleId, quality, substr(rest, 1, instr(rest, ',') - 1), originalAudio, " +
            "substr(rest, instr(rest, ',') + 1) FROM options WHERE rest <> '') " +
            "INSERT OR IGNORE INTO title_source_option " +
            "SELECT titleId, quality, language, originalAudio FROM options"

    fun tokens(value: String, lowercase: Boolean = true): List<String> =
        value
            .split(',')
            .map { it.trim().let { text -> if (lowercase) text.lowercase() else text } }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(30)

    /** Etichete din categoriile playlistului, fără clasificare după titlu ori certificare TMDB. */
    fun adultMovieSources(): SqlQuery {
        val group = "' ' || lower(replace(replace(trim(groupTitle), 'Ț', 'ț'), 'Ţ', 'ţ')) || ' '"
        val markers =
            listOf(
                "*[^a-z0-9]xxx[^a-z0-9]*",
                "*[^0-9]18+[^a-z0-9]*",
                "*[^a-z]adult movie[^a-z]*",
                "*[^a-z]adult movies[^a-z]*",
                "*[^a-z]adult films[^a-z]*",
                "*[^a-z]movies adult[^a-z]*",
                "*[^a-z]adult entertainment[^a-z]*",
                "*[^a-z]adult vod[^a-z]*",
                "*[^a-z]for adults[^a-z]*",
                "*[^a-z]adulti[^a-z]*",
                "*[^a-z]adulți[^a-z]*",
                "*[^a-z]adulţi[^a-z]*",
                " adult ",
                " adults ",
                "*| adult *",
                "*| adults *",
            )
        return SqlQuery(
            "UPDATE raw_entry SET visible = 0 WHERE kind = 'MOVIE' AND visible = 1 AND (" +
                markers.joinToString(" OR ") { "($group) GLOB ?" } + ")",
            markers,
        )
    }

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
