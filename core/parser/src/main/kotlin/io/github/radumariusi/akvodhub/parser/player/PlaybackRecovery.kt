package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.VideoQuality

object PlaybackRecovery {
    /** Un pas de cel puțin 15%, ca fluxul adaptiv să reducă efectiv cererea de date. */
    fun lowerBitrate(current: Int, cap: Int, available: Iterable<Int>): Int? {
        val effective = minOf(current, cap)
        if (effective <= 0) return null
        val limit = effective * 85L / 100
        return available.filter { it > 0 && it <= limit }.maxOrNull()
    }

    /** Alege o sursă cu rezoluție mai mică; audio declarat în comun are prioritate. */
    fun lighterSource(
        currentQuality: VideoQuality?,
        currentLanguages: List<String>,
        available: Iterable<SourceInfo>,
    ): Long? {
        currentQuality ?: return null
        return available
            .filter { candidate -> candidate.quality?.let { it < currentQuality } == true }
            .maxWithOrNull(
                compareBy<SourceInfo> { candidate -> candidate.languages.any { it in currentLanguages } }
                    .thenBy { it.quality },
            )?.id
    }
}
