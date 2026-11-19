package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.stremio.SubtitleLanguages

/** Limbile audio văzute la redare, salvate pe sursă doar când toate pistele au limba declarată. */
object ConfirmedLanguages {
    private const val MAX = 6

    /** Ex. `[rum, eng]` → `ro,en`; null dacă vreo pistă nu are limbă (nu știm sigur ce conține). */
    fun of(trackLanguages: List<String?>): String? {
        if (trackLanguages.isEmpty()) return null
        val codes =
            trackLanguages.map { raw ->
                SubtitleLanguages.toIso1(raw)?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }
                    ?: return null
            }
        return codes.distinct().take(MAX).joinToString(",")
    }
}
