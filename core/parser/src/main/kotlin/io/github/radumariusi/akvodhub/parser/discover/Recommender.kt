package io.github.radumariusi.akvodhub.parser.discover

import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/** Un film sau serial identificat prin tip + ID TMDB (stabil între reindexări). */
data class TitleRef(
    val type: MediaType,
    val tmdbId: Long,
)

/** Un titlu din istoricul de vizionare, cu trăsăturile din fișă. */
data class HistoryItem(
    val titleId: Long,
    val type: MediaType,
    val genreIds: Set<Int>,
    val year: Int?,
    /** Cât s-a văzut, între 0 și 1 (la seriale: din episodul curent). */
    val progress: Double,
    val finished: Boolean,
    val watchedAt: Long,
)

/** Un titlu din playlist care poate fi recomandat. */
data class Candidate(
    val titleId: Long,
    val type: MediaType,
    val genreIds: Set<Int>,
    val year: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val popularity: Double?,
)

/** Persoană (regizor / actor) legată de un titlu văzut, cu ponderea ei (regizorii contează mai mult). */
data class PersonWeight(
    val personId: Long,
    val weight: Double,
)

/**
 * Gusturile utilizatorului, calculate local din istoric: ponderi pe genuri și persoane (normalizate, maximul = 1),
 * anii preferați (medie + abatere) și cât din istoric sunt filme / seriale.
 */
data class TasteProfile(
    val genres: Map<Int, Double>,
    val people: Map<Long, Double>,
    val yearMean: Double?,
    val yearSpread: Double,
    val typeShare: Map<MediaType, Double>,
) {
    val isEmpty: Boolean get() = genres.isEmpty() && people.isEmpty()

    /** Genurile cele mai importante, primele [n]. */
    fun topGenres(n: Int): List<Int> =
        genres.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }

    /** Persoanele cele mai importante, primele [n]. */
    fun topPeople(n: Int): List<Long> =
        people.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
}

/**
 * Ce știm despre un candidat din TMDB: [related] = cât de des și cât de sus apare în „Recomandate” / „Similare”
 * ale titlurilor văzute; [people] = persoanele din profil care au lucrat la el; [collection] = face parte dintr-o
 * colecție (francize) din care s-a văzut ceva.
 */
data class Signals(
    val related: Double = 0.0,
    val people: Set<Long> = emptySet(),
    val collection: Boolean = false,
)

/** Construiește [Signals] din listele TMDB ale titlurilor văzute. */
class SignalsBuilder {
    private val related = HashMap<Long, Double>()
    private val people = HashMap<Long, MutableSet<Long>>()
    private val collection = HashSet<Long>()

    /** [titleId] apare pe poziția [rank] (de la 0) într-o listă de recomandări a unui titlu văzut cu ponderea [weight]. */
    fun related(
        titleId: Long,
        rank: Int,
        weight: Double,
    ) {
        val rankFactor = (1.0 - rank / RANK_SPAN).coerceAtLeast(MIN_RANK_FACTOR)
        related[titleId] = (related[titleId] ?: 0.0) + weight * rankFactor
    }

    fun person(
        titleId: Long,
        personId: Long,
    ) {
        people.getOrPut(titleId) { HashSet() } += personId
    }

    fun collection(titleId: Long) {
        collection += titleId
    }

    val titleIds: Set<Long> get() = related.keys + people.keys + collection

    fun build(): Map<Long, Signals> =
        titleIds.associateWith {
            Signals(related[it] ?: 0.0, people[it].orEmpty(), it in collection)
        }

    private companion object {
        const val RANK_SPAN = 40.0
        const val MIN_RANK_FACTOR = 0.3
    }
}

/**
 * Scorul local al recomandărilor: genuri, persoane (regizori, actori), an, calitate (notă + voturi), popularitate,
 * plus semnalele TMDB. Totul e calculat pe dispozitiv, doar din istoric și din ce există în playlist.
 */
object Recommender {
    private const val DAY_MS = 86_400_000.0

    /** Cât contează un titlu văzut: cât de recent (înjumătățire la 45 de zile) × cât de mult s-a uitat. */
    fun weight(
        item: HistoryItem,
        now: Long,
    ): Double {
        val ageDays = ((now - item.watchedAt).coerceAtLeast(0)) / DAY_MS
        val recency = exp(-ageDays * Math.log(2.0) / HALF_LIFE_DAYS)
        val engagement =
            when {
                item.finished -> 1.0

                item.progress >= 0.5 -> 0.8

                item.progress >= 0.15 -> 0.5

                // Abandonat repede: aproape nu contează.
                else -> 0.15
            }
        return (MIN_RECENCY + (1 - MIN_RECENCY) * recency) * engagement
    }

    fun profile(
        history: List<HistoryItem>,
        people: Map<Long, List<PersonWeight>>,
        now: Long,
    ): TasteProfile {
        val genres = HashMap<Int, Double>()
        val persons = HashMap<Long, Double>()
        val types = HashMap<MediaType, Double>()
        var yearSum = 0.0
        var yearWeight = 0.0
        val years = mutableListOf<Pair<Int, Double>>()
        for (item in history) {
            val w = weight(item, now)
            // Un titlu cu multe genuri nu trebuie să conteze de mai multe ori.
            val perGenre = if (item.genreIds.isEmpty()) 0.0 else w / sqrt(item.genreIds.size.toDouble())
            item.genreIds.forEach { genres[it] = (genres[it] ?: 0.0) + perGenre }
            people[item.titleId]?.forEach { p -> persons[p.personId] = (persons[p.personId] ?: 0.0) + w * p.weight }
            types[item.type] = (types[item.type] ?: 0.0) + w
            item.year?.let {
                yearSum += it * w
                yearWeight += w
                years += it to w
            }
        }
        val mean = if (yearWeight > 0) yearSum / yearWeight else null
        val spread =
            if (mean == null) {
                0.0
            } else {
                sqrt(years.sumOf { (y, w) -> (y - mean) * (y - mean) * w } / yearWeight)
            }
        val typeTotal = types.values.sum()
        return TasteProfile(
            genres = normalizeMax(genres),
            people = normalizeMax(persons),
            yearMean = mean,
            yearSpread = spread,
            typeShare = if (typeTotal > 0) types.mapValues { it.value / typeTotal } else emptyMap(),
        )
    }

    fun score(
        profile: TasteProfile,
        candidate: Candidate,
        signals: Signals?,
    ): Double {
        val genre = genreSimilarity(profile.genres, candidate.genreIds)
        val people = (signals?.people.orEmpty().sumOf { profile.people[it] ?: 0.0 }).coerceAtMost(MAX_PEOPLE)
        val related = (signals?.related ?: 0.0).coerceAtMost(MAX_RELATED)
        val type = profile.typeShare[candidate.type] ?: if (profile.typeShare.isEmpty()) 0.5 else 0.0
        return W_GENRE * genre +
            W_RELATED * related +
            W_PEOPLE * people +
            W_YEAR * yearScore(profile, candidate.year) +
            W_QUALITY * quality(candidate.rating, candidate.voteCount) +
            W_POPULARITY * popularity(candidate.popularity) +
            W_TYPE * type +
            (if (signals?.collection == true) W_COLLECTION else 0.0)
    }

    /** Cele mai bune [limit] titluri (ID-uri), fără cele din [exclude]. La egalitate: mai popular, apoi ID. */
    fun rank(
        profile: TasteProfile,
        candidates: Collection<Candidate>,
        signals: Map<Long, Signals>,
        exclude: Set<Long>,
        limit: Int,
    ): List<Long> =
        candidates
            .asSequence()
            .filter { it.titleId !in exclude }
            .distinctBy { it.titleId }
            .map { it to score(profile, it, signals[it.titleId]) }
            .sortedWith(
                compareByDescending<Pair<Candidate, Double>> { it.second }
                    .thenByDescending { it.first.popularity ?: 0.0 }
                    .thenBy { it.first.titleId },
            ).take(limit)
            .map { it.first.titleId }
            .toList()

    /** Cosinusul dintre profilul pe genuri și genurile candidatului (între 0 și 1). */
    fun genreSimilarity(
        profile: Map<Int, Double>,
        genres: Set<Int>,
    ): Double {
        if (profile.isEmpty() || genres.isEmpty()) return 0.0
        val norm = sqrt(profile.values.sumOf { it * it })
        if (norm == 0.0) return 0.0
        return genres.sumOf { profile[it] ?: 0.0 } / (norm * sqrt(genres.size.toDouble()))
    }

    /** 1 la anul preferat, scade lin cu distanța (mai încet dacă istoricul e variat). 0,5 dacă nu știm. */
    fun yearScore(
        profile: TasteProfile,
        year: Int?,
    ): Double {
        val mean = profile.yearMean ?: return 0.5
        if (year == null) return 0.5
        val width = profile.yearSpread + YEAR_TOLERANCE
        val d = (year - mean) / width
        return exp(-d * d / 2)
    }

    /** Nota „corectată” după numărul de voturi (o notă 9 cu 3 voturi nu bate o notă 8 cu 20.000), între 0 și 1. */
    fun quality(
        rating: Double?,
        votes: Int?,
    ): Double {
        if (rating == null || rating <= 0) return 0.3
        val v = (votes ?: 0).coerceAtLeast(0).toDouble()
        val adjusted = (v * rating + PRIOR_VOTES * PRIOR_RATING) / (v + PRIOR_VOTES)
        return ((adjusted - 5.0) / 3.5).coerceIn(0.0, 1.0)
    }

    fun popularity(value: Double?): Double = (log10(1 + (value ?: 0.0).coerceAtLeast(0.0)) / 3).coerceIn(0.0, 1.0)

    private fun <K> normalizeMax(map: Map<K, Double>): Map<K, Double> {
        val max = map.values.maxOrNull() ?: return emptyMap()
        if (max <= 0) return emptyMap()
        return map.mapValues { it.value / max }
    }

    private const val HALF_LIFE_DAYS = 45.0
    private const val MIN_RECENCY = 0.2
    private const val YEAR_TOLERANCE = 8.0
    private const val PRIOR_VOTES = 300.0
    private const val PRIOR_RATING = 6.4
    private const val MAX_PEOPLE = 1.5
    private const val MAX_RELATED = 1.5

    private const val W_GENRE = 2.0
    private const val W_RELATED = 1.6
    private const val W_PEOPLE = 1.2
    private const val W_YEAR = 0.6
    private const val W_QUALITY = 0.9
    private const val W_POPULARITY = 0.3
    private const val W_TYPE = 0.5
    private const val W_COLLECTION = 0.8
}
