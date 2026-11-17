package io.github.radumariusi.akvodhub.parser.catalog

import java.util.Locale

/** Acumulator în flux: păstrează doar limbile distincte, nu lista surselor. */
class DubLabel(originalLanguage: String?) {
    private val original = language(originalLanguage)
    private val languages = sortedSetOf<String>()
    private var uncertain = original == null
    private var originalAvailable = false

    fun addSource(audioLanguages: String?) {
        val tokens = audioLanguages?.split(',').orEmpty()
        if (tokens.isEmpty()) uncertain = true
        for (token in tokens) {
            val code = token.trim().lowercase(Locale.ROOT)
            when {
                code == "mul" || code == original -> originalAvailable = true
                language(code) == null -> uncertain = true
                else -> languages += code
            }
        }
    }

    fun label(): String? =
        if (uncertain || originalAvailable || languages.isEmpty()) {
            null
        } else {
            "DUB " + languages.joinToString(" / ") { it.uppercase(Locale.ROOT) }
        }

    private fun language(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in ISO_LANGUAGES }

    private companion object {
        val ISO_LANGUAGES = Locale.getISOLanguages().toSet()
    }
}
