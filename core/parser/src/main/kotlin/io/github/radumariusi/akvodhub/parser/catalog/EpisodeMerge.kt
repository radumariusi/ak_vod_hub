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
    val episodeNameOnly: Boolean = false,
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
    private val episodeOnlyTag =
        Regex("""(?i)^.*?\b(?:episode|episodio|episódio|episodul|episod|ep)\.?\s*\d{1,4}\b[\s\-–:|.]*""")

    private val genericPrefix =
        Regex("""(?i)^(?:episode|episodio|episódio|episodul|episod|ep)\.?\s*\d{1,4}\b[\s\-–:|.]*""")
    private val genericLabel = Regex("""(?i)^(?:episode|episodio|episódio|episodul|episod|ep)[. ]*\d{1,4}[. ]*$""")

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
        title = list.firstNotNullOfOrNull { episodeTitle(it.name) ?: providerTitle(it) },
        sources = SourceGrouping.ordered(list.map { it.source }, preferredLanguages),
    )

    private fun providerTitle(entry: EpisodeEntry): String? {
        if (!entry.episodeNameOnly) return null
        val title = namedEpisode(entry.name) ?: return null
        if (episodeTag.containsMatchIn(title) || genericPrefix.containsMatchIn(title)) return null
        return title
    }

    fun namedEpisode(title: String?): String? =
        title?.trim()?.takeIf { it.isNotEmpty() && !genericLabel.matches(it) }

    /** Ce urmează după `S01E02` în numele din playlist; null dacă nu e nimic util. */
    fun episodeTitle(name: String): String? {
        val match = episodeTag.find(name) ?: episodeOnlyTag.find(name) ?: return null
        val remainder = name.substring(match.range.last + 1).trim()
        val prefix = genericPrefix.find(remainder)
        val title = if (prefix == null) remainder else remainder.substring(prefix.range.last + 1)
        return title.trim().takeIf { it.length >= 2 && !genericLabel.matches(it) }
    }
}
