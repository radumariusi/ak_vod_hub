package io.github.radumariusi.akvodhub.parser.match

import kotlin.math.abs

/** Film sau serial (pentru TMDB: `movie` sau `tv`). */
enum class MediaType {
    MOVIE,
    TV,
}

/** Un rezultat de căutare TMDB, redus la ce contează pentru potrivire. */
data class MatchCandidate(
    val tmdbId: Long,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val popularity: Double = 0.0,
)

/** Ce căutăm: titlul curat și anul scoase de parser din numele din playlist. */
data class MatchQuery(
    val type: MediaType,
    val title: String,
    val year: Int?,
    /** Limba în care e scris titlul (ISO 639-1), dacă o știm; altfel căutăm în engleză. */
    val language: String? = null,
) {
    /** Cheia de cache: aceeași cheie = același rezultat, deci o singură cerere TMDB pentru toate duplicatele. */
    val key: String =
        listOf(
            if (type == MediaType.MOVIE) "m" else "t",
            TitleSimilarity.normalize(title),
            year?.toString().orEmpty(),
            language.orEmpty(),
        ).joinToString("|")
}

/** O cerere de căutare TMDB. `year == null` = fără filtru de an. */
data class SearchRequest(
    val type: MediaType,
    val query: String,
    val year: Int?,
    val language: String?,
)

data class ScoredCandidate(
    val candidate: MatchCandidate,
    val score: Double,
)

/**
 * Scorul de încredere al potrivirii titlu din playlist ↔ rezultat TMDB, între 0 și 1:
 * 70% asemănarea titlului (cu titlul tradus sau cel original), 20% anul, 10% poziția în rezultate.
 * Un an care diferă cu mai mult de 1 scade mult scorul (de obicei e un remake sau alt film cu același nume).
 */
object MatchScorer {
    /** Sub prag, intrarea rămâne „nepotrivită” (mai bine fără poster decât cu filmul greșit). */
    const val THRESHOLD = 0.75

    fun score(
        query: MatchQuery,
        candidate: MatchCandidate,
        rank: Int,
    ): Double {
        val q = TitleSimilarity.normalize(query.title)
        val sim =
            maxOf(
                TitleSimilarity.similarity(q, TitleSimilarity.normalize(candidate.title)),
                candidate.originalTitle?.let { TitleSimilarity.similarity(q, TitleSimilarity.normalize(it)) } ?: 0.0,
            )
        val yearScore =
            if (query.year == null || candidate.year == null) {
                0.5
            } else {
                when (abs(query.year - candidate.year)) {
                    0 -> 1.0
                    1 -> 0.7
                    else -> -1.0
                }
            }
        val rankScore =
            when (rank) {
                0 -> 1.0
                1, 2 -> 0.5
                else -> 0.0
            }
        return (0.7 * sim + 0.2 * yearScore + 0.1 * rankScore).coerceIn(0.0, 1.0)
    }

    /** Cel mai bun candidat (la egalitate, cel mai popular), sau null dacă lista e goală. */
    fun best(
        query: MatchQuery,
        candidates: List<MatchCandidate>,
    ): ScoredCandidate? =
        candidates
            .mapIndexed { i, c -> ScoredCandidate(c, score(query, c, i)) }
            .maxWithOrNull(compareBy<ScoredCandidate> { it.score }.thenBy { it.candidate.popularity })

    /**
     * Căutările de încercat, în ordine, până când una dă un scor peste prag:
     * 1. titlu + an (în limba titlului, ca „Der Pate” să fie comparat cu titlul german de pe TMDB);
     * 2. fără an (anul din playlist e uneori greșit sau e al lansării pe DVD);
     * 3. titlu + an ca text, pentru filme în care anul face parte din nume („Wonder Woman 1984”, „2012”).
     */
    fun plan(query: MatchQuery): List<SearchRequest> {
        val language = query.language?.takeIf { it.length == 2 && it != "en" }
        val list = mutableListOf(SearchRequest(query.type, query.title, query.year, language))
        if (query.year != null) {
            list += SearchRequest(query.type, query.title, null, language)
            list += SearchRequest(query.type, "${query.title} ${query.year}", null, language)
        }
        return list
    }
}
