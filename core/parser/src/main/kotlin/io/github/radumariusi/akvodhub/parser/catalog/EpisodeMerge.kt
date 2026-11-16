package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo

/** Un episod din playlist (o sursă), cu numerele sezonului și episodului dacă le știm. */
data class EpisodeEntry(
    val season: Int?,
    val episode: Int?,
    /** Numele din playlist, ex. „Breaking Bad S01E02 Cat's in the Bag”. */
    val name: String,
    val source: SourceInfo,
)

/** Un episod unit din toate sursele (playlist-uri, calități, limbi), cu sursele în ordinea celor mai bune. */
data class MergedEpisode(
    val season: Int,
    /** null = episod fără număr (ex. intrări M3U fără SxxEyy). */
    val number: Int?,
    /** Titlul episodului, dacă playlist-ul îl dă după SxxEyy (ex. „Cat's in the Bag”). */
    val title: String?,
    val sources: List<SourceInfo>,
)

data class MergedSeason(
    /** 0 = speciale sau episoade fără sezon. */
    val number: Int,
    val episodes: List<MergedEpisode>,
)

/**
 * Unește episoadele unui serial din toate sursele: același sezon + episod = un singur episod, cu toate
 * sursele lui (ordonate ca în [SourceGrouping]). Sezoanele și episoadele ies în ordine.
 */
object EpisodeMerge {
    private val episodeTag = Regex("""(?i)^.*?\bS\d{1,2}\s*E\d{1,4}\b[\s\-–:|.]*""")
    private val episodeOnlyTag = Regex("""(?i)^.*?\b(?:episode|episodul|ep)\.?\s*\d{1,4}\b[\s\-–:|.]*""")

    fun merge(
        entries: List<EpisodeEntry>,
        preferredLanguages: List<String> = emptyList(),
    ): List<MergedSeason> =
        entries
            .groupBy { it.season ?: 0 }
            .toSortedMap()
            .map { (season, inSeason) ->
                val numbered =
                    inSeason
                        .filter { it.episode != null }
                        .groupBy { it.episode!! }
                        .toSortedMap()
                        .map { (number, list) -> episode(season, number, list, preferredLanguages) }
                val loose =
                    inSeason
                        .filter { it.episode == null }
                        .sortedBy { it.name }
                        .map { episode(season, null, listOf(it), preferredLanguages) }
                MergedSeason(season, numbered + loose)
            }

    private fun episode(
        season: Int,
        number: Int?,
        list: List<EpisodeEntry>,
        preferredLanguages: List<String>,
    ) = MergedEpisode(
        season = season,
        number = number,
        title = list.firstNotNullOfOrNull { episodeTitle(it.name) },
        sources = SourceGrouping.ordered(list.map { it.source }, preferredLanguages),
    )

    /** Ce urmează după `S01E02` în numele din playlist; null dacă nu e nimic util. */
    fun episodeTitle(name: String): String? {
        val match = episodeTag.find(name) ?: episodeOnlyTag.find(name) ?: return null
        return name
            .substring(match.range.last + 1)
            .trim()
            .takeIf { it.length >= 2 }
    }
}
