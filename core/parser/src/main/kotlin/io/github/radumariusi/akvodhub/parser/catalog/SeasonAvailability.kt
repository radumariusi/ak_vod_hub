package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.title.ParsedTitle

enum class SeasonAvailability {
    MISSING,
    UNKNOWN,
    OTHER_AUDIO,
    UNDECLARED_AUDIO,
    AVAILABLE,
    ;

    companion object {
        fun of(season: MergedSeason, originalLanguage: String?, checked: Boolean): SeasonAvailability {
            if (season.episodes.isEmpty()) return if (checked) MISSING else UNKNOWN
            val original = OriginalLanguage.code(originalLanguage)
            val preferred = setOfNotNull(original, "en", "ro")
            val audio =
                season.episodes
                    .asSequence()
                    .flatMap { it.sources }
                    .map { it.languages }
            var uncertain = original == null || !checked
            for (languages in audio) {
                if (languages.any { OriginalLanguage.code(it) in preferred }) return AVAILABLE
                if (languages.isEmpty() ||
                    languages.any { it == ParsedTitle.MULTI || OriginalLanguage.code(it) == null }
                ) {
                    uncertain = true
                }
            }
            return if (uncertain) UNDECLARED_AUDIO else OTHER_AUDIO
        }
    }
}
