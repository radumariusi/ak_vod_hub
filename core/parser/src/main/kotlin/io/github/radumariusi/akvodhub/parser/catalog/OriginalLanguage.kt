package io.github.radumariusi.akvodhub.parser.catalog

import java.util.Locale

/** Numai codurile ISO cunoscute pot confirma audio original. MULTI rămâne o posibilitate. */
object OriginalLanguage {
    private val codes = Locale.getISOLanguages().toSet()

    fun code(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in codes }

    fun rank(original: String, languages: List<String>): Int {
        val normalized = languages.map { it.trim().lowercase(Locale.ROOT) }
        return when {
            original in normalized -> 0
            "mul" in normalized -> 1
            else -> 2
        }
    }
}
