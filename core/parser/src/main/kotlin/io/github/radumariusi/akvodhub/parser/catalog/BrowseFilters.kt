package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.title.VideoQuality

data class FilterPreset(val name: String, val filter: CatalogFilter)

object BrowseFilters {
    val keys = MediaType.entries.flatMap { listOf(current(it), presets(it)) }

    fun current(type: MediaType) = "browse.${type.name}.current"

    fun presets(type: MediaType) = "browse.${type.name}.presets"

    fun encode(filter: CatalogFilter): String =
        listOf(
            filter.genre?.key.orEmpty(),
            filter.years?.label.orEmpty(),
            filter.quality?.name.orEmpty(),
            filter.language.orEmpty(),
            filter.sort.name,
            filter.originalAudioOnly.toString(),
            filter.production.name,
            filter.library.name,
        ).joinToString("|")

    fun decode(type: MediaType, value: String?): CatalogFilter {
        val fields = value.orEmpty().take(300).split('|')

        fun get(index: Int) = fields.getOrNull(index)
        return CatalogFilter(
            type = type,
            genre = get(0)?.let(Genres::byKey),
            years = YearRange.all.firstOrNull { it.label == get(1) },
            quality = VideoQuality.entries.firstOrNull { it.name == get(2) },
            language = get(3)?.takeIf { it.matches(Regex("[a-z]{2,3}")) },
            sort = CatalogSort.entries.firstOrNull { it.name == get(4) } ?: CatalogSort.POPULAR,
            originalAudioOnly = get(5) == "true",
            production = ProductionFilter.entries.firstOrNull { it.name == get(6) } ?: ProductionFilter.ALL,
            library = LibraryFilter.entries.firstOrNull { it.name == get(7) } ?: LibraryFilter.ALL,
        )
    }

    fun name(value: String): String = value.filterNot { it.isISOControl() }.trim().take(36)

    fun encodePresets(values: List<FilterPreset>): String =
        values.take(MAX_PRESETS).joinToString("\n") { "${name(it.name)}\t${encode(it.filter)}" }

    fun decodePresets(type: MediaType, value: String?): List<FilterPreset> =
        value
            .orEmpty()
            .take(4000)
            .lineSequence()
            .mapNotNull { line ->
                val label = name(line.substringBefore('\t'))
                if (label.isEmpty() ||
                    '\t' !in line
                ) {
                    null
                } else {
                    FilterPreset(label, decode(type, line.substringAfter('\t')))
                }
            }.distinctBy { it.name.lowercase() }
            .take(MAX_PRESETS)
            .toList()

    fun sanitize(key: String, value: String): String? {
        val type = MediaType.entries.firstOrNull { key == current(it) || key == presets(it) } ?: return null
        return if (key == current(type)) {
            encode(decode(type, value))
        } else {
            encodePresets(decodePresets(type, value))
        }
    }

    const val MAX_PRESETS = 8
}
