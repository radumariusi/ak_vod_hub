package io.github.radumariusi.akvodhub.parser.stremio

/** O subtitrare găsită într-un addon (limba deja adusă la ISO 639-1, dacă se poate). */
data class AddonSubtitle(
    val id: String,
    val url: String,
    val rawLanguage: String,
    val language: String?,
    val addon: String,
)

/** Ordonarea subtitrărilor din addon-uri și alegerea automată față de cele încorporate în sursă. */
object SubtitleChoice {
    /** Limbile preferate primele (în ordinea lor), apoi restul; în aceeași limbă rămâne ordinea addon-ului. */
    fun order(
        subtitles: List<AddonSubtitle>,
        preferred: List<String>,
    ): List<AddonSubtitle> =
        subtitles
            .withIndex()
            .sortedWith(compareBy({ rank(it.value.language, preferred) }, { it.index }))
            .map { it.value }
            .distinctBy { it.url }

    /** Poziția limbii în preferințe (0 = prima); [Int.MAX_VALUE] dacă nu e preferată. */
    fun rank(
        language: String?,
        preferred: List<String>,
    ): Int = preferred.indexOf(language).takeIf { it >= 0 } ?: Int.MAX_VALUE

    /**
     * Ce subtitrare din addon pornim singuri: doar dacă sursa nu are deja una încorporată într-o limbă
     * preferată la fel de bună sau mai bună. Ex. preferințe RO, EN: sursa are EN, addon-ul are RO → RO din addon;
     * sursa are RO → rămâne cea încorporată.
     */
    fun autoPick(
        embeddedLanguages: List<String?>,
        subtitles: List<AddonSubtitle>,
        preferred: List<String>,
    ): AddonSubtitle? {
        if (preferred.isEmpty()) return null
        val best = order(subtitles, preferred).firstOrNull() ?: return null
        val addonRank = rank(best.language, preferred)
        if (addonRank == Int.MAX_VALUE) return null
        val embeddedRank = embeddedLanguages.minOfOrNull { rank(it, preferred) } ?: Int.MAX_VALUE
        return if (addonRank < embeddedRank) best else null
    }
}
