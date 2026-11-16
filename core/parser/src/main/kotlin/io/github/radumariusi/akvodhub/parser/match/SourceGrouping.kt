package io.github.radumariusi.akvodhub.parser.match

import io.github.radumariusi.akvodhub.parser.title.HdrFormat
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.VideoQuality

/** O sursă (intrare din playlist) a unui titlu: ce calitate și ce limbi are. */
data class SourceInfo(
    val id: Long,
    val quality: VideoQuality?,
    val hdr: HdrFormat? = null,
    /** ISO 639-1; `mul` = mai multe; gol = necunoscut. */
    val languages: List<String> = emptyList(),
    val subtitles: List<String> = emptyList(),
)

/** Sursele cu aceeași calitate, grupate pe limbă. [quality] null = calitate necunoscută. */
data class QualityGroup(
    val quality: VideoQuality?,
    val languages: List<LanguageGroup>,
) {
    val count: Int get() = languages.sumOf { it.sources.size }
}

/** Sursele cu aceeași limbă principală. [language] null = limbă necunoscută. */
data class LanguageGroup(
    val language: String?,
    val sources: List<SourceInfo>,
)

/**
 * Grupează toate sursele unui film/serial după calitate (4K → 1080p → 720p → SD → necunoscută),
 * apoi după limbă (limbile preferate primele, apoi cele cu mai multe surse), ex.
 * „4K: EN, DE · 1080p: RO, FR, IT, +4”.
 */
object SourceGrouping {
    fun group(
        sources: List<SourceInfo>,
        preferredLanguages: List<String> = emptyList(),
    ): List<QualityGroup> =
        sources
            .groupBy { it.quality }
            .entries
            .sortedByDescending { it.key?.ordinal ?: -1 }
            .map { (quality, inQuality) ->
                val byLanguage =
                    inQuality
                        .groupBy { it.languages.firstOrNull() }
                        .map { (language, list) -> LanguageGroup(language, sortWithin(list)) }
                        .sortedWith(languageOrder(preferredLanguages))
                QualityGroup(quality, byLanguage)
            }

    /** Toate sursele într-o singură listă, cea mai bună prima (ordinea din [group]). */
    fun ordered(
        sources: List<SourceInfo>,
        preferredLanguages: List<String> = emptyList(),
    ): List<SourceInfo> = group(sources, preferredLanguages).flatMap { q -> q.languages.flatMap { it.sources } }

    /** Rezumat pe un rând, ex. `4K: EN, DE · 1080p: RO, FR, IT, +4`. */
    fun summary(
        groups: List<QualityGroup>,
        maxLanguages: Int = 3,
    ): String =
        groups.joinToString(" · ") { q ->
            val names = q.languages.map { languageLabel(it.language) }
            val shown = names.take(maxLanguages).joinToString(", ")
            val more = if (names.size > maxLanguages) ", +${names.size - maxLanguages}" else ""
            "${qualityLabel(q.quality)}: $shown$more"
        }

    fun qualityLabel(quality: VideoQuality?): String =
        when (quality) {
            VideoQuality.UHD -> "4K"
            VideoQuality.FHD -> "1080p"
            VideoQuality.HD -> "720p"
            VideoQuality.SD -> "SD"
            null -> "?"
        }

    fun languageLabel(language: String?): String =
        when (language) {
            null -> "?"
            ParsedTitle.MULTI -> "MULTI"
            else -> language.uppercase()
        }

    // În aceeași limbă: HDR înainte, apoi cele cu subtitrări declarate.
    private fun sortWithin(list: List<SourceInfo>) =
        list.sortedWith(
            compareByDescending<SourceInfo> { it.hdr?.ordinal ?: -1 }
                .thenByDescending { it.subtitles.size }
                .thenBy { it.id },
        )

    private fun languageOrder(preferred: List<String>): Comparator<LanguageGroup> =
        compareBy<LanguageGroup> { g ->
            val i = if (g.language == null) -1 else preferred.indexOf(g.language)
            when {
                i >= 0 -> i
                g.language == null -> Int.MAX_VALUE
                else -> preferred.size
            }
        }.thenByDescending { it.sources.size }
            .thenBy { it.language }
}
