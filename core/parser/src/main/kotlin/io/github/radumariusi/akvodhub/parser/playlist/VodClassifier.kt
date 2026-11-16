package io.github.radumariusi.akvodhub.parser.playlist

import io.github.radumariusi.akvodhub.parser.VodKind

/**
 * Decide dacă o intrare M3U e film, episod de serial sau altceva (Live TV, radio), care se ignoră.
 *
 * Ordinea regulilor:
 * 1. URL de tip Xtream: `/movie/` → film, `/series/` → episod, `/live/` sau `/timeshift/` → ignorat.
 * 2. Extensie video (`.mkv`, `.mp4`, `.avi`…) → film sau episod.
 * 3. Grup de tip „VOD / Filme / Movies / Series / Seriale” → film sau episod.
 *
 * Episod vs. film (la 2 și 3): grupul seamănă cu „Series” sau numele are `S01E02` / `1x02`.
 */
object VodClassifier {
    private val videoExtensions =
        setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "mpg", "mpeg", "flv", "webm", "divx", "ogm", "3gp")

    private val movieWords =
        setOf(
            "vod",
            "movie",
            "movies",
            "film",
            "filme",
            "filmes",
            "films",
            "filmy",
            "filmi",
            "filmler",
            "peliculas",
            "películas",
            "pelicula",
            "cinema",
            "cine",
        )

    private val seriesWords =
        setOf(
            "series",
            "serie",
            "seriale",
            "serial",
            "serien",
            "séries",
            "seriados",
            "dizi",
            "diziler",
            "sorozat",
            "sorozatok",
            "seriály",
            "serije",
        )

    /** Grupuri care arată clar Live TV, chiar dacă au „Movies” în nume (ex. „24/7 Movies”, „Live Cinema”). */
    private val liveWords = setOf("live", "247", "radio", "epg")
    private val liveMarkers = listOf("24/7", "24-7", "24x7")

    private val episodePattern = Regex("""(?i)(\bS\d{1,2}\s?E\d{1,4}\b|\b\d{1,2}x\d{2,3}\b)""")
    private val wordSplit = Regex("""[^\p{L}\p{N}]+""")

    fun classify(
        url: String,
        groupTitle: String?,
        name: String,
    ): VodKind? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        VodKind.fromUrl(path)?.let { return it }
        if ("/live/" in path || "/timeshift/" in path) return null

        val extension = path.substringAfterLast('/').substringAfterLast('.', "")
        val group = groupTitle?.lowercase().orEmpty()
        val groupWords = group.split(wordSplit).filter { it.isNotEmpty() }
        val seriesGroup = groupWords.any { it in seriesWords }
        val looksLikeEpisode = seriesGroup || episodePattern.containsMatchIn(name)

        if (extension in videoExtensions) return if (looksLikeEpisode) VodKind.EPISODE else VodKind.MOVIE

        // Fără semn clar din URL: ne bazăm pe grup, dar nu pe stream-uri .ts (aproape mereu Live TV).
        if (extension == "ts" || groupWords.any { it in liveWords } || liveMarkers.any { it in group }) return null
        return when {
            seriesGroup -> VodKind.EPISODE
            groupWords.any { it in movieWords } -> if (looksLikeEpisode) VodKind.EPISODE else VodKind.MOVIE
            else -> null
        }
    }
}
