package io.github.radumariusi.akvodhub.parser.title

/** Cum tratăm limba unei categorii din playlist, ales de utilizator în Setări → Conținut. */
enum class CategoryMode {
    /** Regulile automate ale parserului. */
    AUTO,

    /** Audio original, limba categoriei devine subtitrare (ex. „RO - MOVIES” la un furnizor care subtitrează). */
    ORIGINAL,

    /** Audio în limba categoriei (dublaj). */
    DUBBED,
}

/**
 * Regulile pe categorii, aplicate de [TitleParser] (numele categoriei exact, fără spațiile de la capete).
 * Se încarcă la pornire din preferințe; schimbarea lor pornește reparsarea intrărilor salvate.
 */
object CategoryRules {
    @Volatile
    var rules: Map<String, CategoryMode> = emptyMap()

    fun mode(groupTitle: String?): CategoryMode =
        groupTitle?.trim()?.let { rules[it] } ?: CategoryMode.AUTO

    /** Forma salvată: o categorie pe rând, `MOD<TAB>categorie`. */
    fun encode(rules: Map<String, CategoryMode>): String =
        rules.entries
            .filter { it.value != CategoryMode.AUTO && it.key.isNotBlank() }
            .sortedBy { it.key }
            .joinToString("\n") { "${it.value.name}\t${it.key.trim()}" }

    fun decode(value: String?): Map<String, CategoryMode> =
        value
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val mode = CategoryMode.entries.firstOrNull { it.name == line.substringBefore('\t') }
                val category = line.substringAfter('\t', "").trim()
                if (mode == null || mode == CategoryMode.AUTO || category.isEmpty()) null else category to mode
            }.toMap()
}
