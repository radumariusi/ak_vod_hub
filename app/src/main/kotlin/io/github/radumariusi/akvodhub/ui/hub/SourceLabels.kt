package io.github.radumariusi.akvodhub.ui.hub

import io.github.radumariusi.akvodhub.data.catalog.SourceDetails
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo

/** Ex. „Audio EN · HDR10 · H265 · MKV · Sub RO”. „✓” = confirmat la redare. */
fun sourceTags(
    source: SourceInfo,
    info: SourceDetails?,
): String {
    val entry = info?.entry
    return listOfNotNull(
        "Rezoluție ✓".takeIf { entry?.confirmedQuality != null },
        source.languages
            .joinToString(
                "+",
            ) { SourceGrouping.languageLabel(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { "Audio $it" },
        source.hdr?.name?.replace('_', ' '),
        entry?.codec?.name,
        entry?.containerExt?.uppercase(),
        source.subtitles.takeIf { it.isNotEmpty() }?.let {
            "Sub " +
                it.joinToString("+") { s -> SourceGrouping.languageLabel(s) }
        },
    ).joinToString(" · ")
}

/** O sursă în lista de alegere, ex. „1080p · EN · H265 · MKV — Playlist A”. */
fun sourceLabel(
    source: SourceInfo,
    info: SourceDetails?,
) = "${SourceGrouping.qualityLabel(source.quality)} · ${sourceTags(source, info)}" +
    (info?.playlist?.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: "")

/** Ex. „4K EN” (pentru mesajele scurte ale playerului). */
fun sourceShortLabel(source: SourceInfo) =
    "${SourceGrouping.qualityLabel(source.quality)} " +
        source.languages
            .joinToString(
                "+",
            ) { SourceGrouping.languageLabel(it) }
            .ifEmpty { SourceGrouping.languageLabel(null) }
