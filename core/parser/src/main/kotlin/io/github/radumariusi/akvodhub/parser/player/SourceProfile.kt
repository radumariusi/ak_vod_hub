package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.catalog.OriginalLanguage
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.VideoQuality

/** O regulă din profil, ex. „4K EN”. null = oricare (calitate sau limbă). */
data class SourceRule(
    val quality: VideoQuality? = null,
    val language: String? = null,
) {
    fun matches(source: SourceInfo): Boolean =
        (quality == null || source.quality == quality) &&
            (language == null || language in source.languages)

    /** Sursa are „MULTI” (poate include limba cerută, dar nu știm sigur). */
    fun matchesMulti(source: SourceInfo): Boolean =
        language != null &&
            (quality == null || source.quality == quality) &&
            ParsedTitle.MULTI in source.languages

    fun label(): String =
        listOfNotNull(
            quality?.let { SourceGrouping.qualityLabel(it) },
            language?.let { SourceGrouping.languageLabel(it) },
        ).joinToString(" ").ifEmpty { "Oricare" }
}

/**
 * Profilul de preferințe pentru alegerea automată a sursei, ex. „4K EN, altfel 1080p RO”: sursele care
 * respectă prima regulă vin primele, apoi cele pentru a doua regulă și tot așa; restul rămân în ordinea
 * obișnuită (calitate, apoi limbă, ca în [SourceGrouping]). Ordinea dă și lista de rezervă a playerului.
 */
data class SourceProfile(
    val rules: List<SourceRule> = emptyList(),
) {
    /** Limbile din reguli, în ordine (pentru ordonarea pe limbă și pentru pista audio). */
    val languages: List<String> get() = rules.mapNotNull { it.language }.distinct()

    fun order(
        sources: List<SourceInfo>,
        fallbackLanguages: List<String> = emptyList(),
        originalLanguage: String? = null,
    ): List<SourceInfo> {
        val base =
            SourceGrouping.ordered(
                sources,
                (languages + fallbackLanguages + DEFAULT_LANGUAGES.takeIf { rules.isEmpty() }.orEmpty()).distinct(),
            )
        val original = OriginalLanguage.code(originalLanguage)
        return base
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<SourceInfo>> {
                    original?.let { code -> OriginalLanguage.rank(code, it.value.languages) }
                        ?: 0
                }.thenBy { rank(it.value) }
                    .thenBy { it.index },
            ).map { it.value }
    }

    /** 2 × indexul primei reguli respectate (+1 dacă doar „MULTI” ar putea-o respecta); la final, restul. */
    private fun rank(source: SourceInfo): Int {
        if (rules.isEmpty()) {
            return when {
                !hasDefaultLanguage(source) -> 2
                source.quality == VideoQuality.UHD -> 0
                else -> 1
            }
        }
        rules.forEachIndexed { i, rule ->
            if (rule.matches(source)) return 2 * i
            if (rule.matchesMulti(source)) return 2 * i + 1
        }
        return 2 * rules.size
    }

    /** Ex. „4K EN, altfel 1080p RO”; gol dacă nu are reguli. */
    fun describe(): String = rules.joinToString(", altfel ") { it.label() }

    /** Forma salvată: `UHD:en;FHD:ro` (`*` = oricare). */
    fun encode(): String =
        rules.joinToString(";") { "${it.quality?.name ?: ANY}:${it.language ?: ANY}" }

    companion object {
        private const val ANY = "*"
        private val DEFAULT_LANGUAGES = listOf("en", "ro", ParsedTitle.MULTI)

        fun hasDefaultLanguage(source: SourceInfo): Boolean = source.languages.any { it in DEFAULT_LANGUAGES }

        const val MAX_RULES = 4

        fun decode(value: String?): SourceProfile {
            if (value.isNullOrBlank()) return SourceProfile()
            val rules =
                value.split(';').mapNotNull { part ->
                    val q = part.substringBefore(':', "").trim()
                    val l = part.substringAfter(':', "").trim().lowercase()
                    val quality =
                        if (q == ANY || q.isEmpty()) null else VideoQuality.entries.firstOrNull { it.name == q }
                    val language = if (l == ANY || l.isEmpty()) null else l
                    if (quality == null && language == null) null else SourceRule(quality, language)
                }
            return SourceProfile(rules.take(MAX_RULES))
        }
    }
}
