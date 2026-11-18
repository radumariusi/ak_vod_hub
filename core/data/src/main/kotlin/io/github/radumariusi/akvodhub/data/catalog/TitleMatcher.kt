package io.github.radumariusi.akvodhub.data.catalog

import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.MatchCacheEntity
import io.github.radumariusi.akvodhub.data.db.MatchState
import io.github.radumariusi.akvodhub.data.db.PendingEntry
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.tmdb.TmdbAuthException
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbItem
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.match.MatchQuery
import io.github.radumariusi.akvodhub.parser.match.MatchScorer
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
import io.github.radumariusi.akvodhub.parser.title.TitleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Cum s-a terminat o rulare a potrivirii. */
enum class MatchOutcome {
    /** Nu mai e nimic de potrivit. */
    DONE,

    /** S-a terminat timpul alocat; mai sunt intrări (rularea continuă în alt job). */
    MORE,

    /** Fără cheie TMDB: s-au legat doar intrările cu ID TMDB de la furnizor. */
    NEEDS_KEY,

    /** Cheia a fost respinsă de TMDB. */
    BAD_KEY,

    /** Fără rețea / TMDB indisponibil: se reîncearcă mai târziu. */
    NETWORK,
}

/**
 * Leagă intrările din playlist de filme/seriale TMDB și unește duplicatele (același ID TMDB → un singur
 * [TitleEntity], cu toate sursele lui). Rulează în fundal după fiecare import, incremental și reluabil:
 *
 * 1. **ID de la furnizor** (Xtream `tmdb`, M3U `tvg-tmdb`): legare directă, fără rețea.
 * 2. **Căutare după nume**, în loturi: duplicatele (aceeași cheie titlu + an + tip + limbă) costă o singură
 *    cerere, iar rezultatul rămâne în cache (`match_cache`) și pentru reîmprospătări și alte playlist-uri.
 *    Scorul de încredere vine din [MatchScorer]; sub prag, intrarea e „nepotrivită”.
 * 3. **Detalii** pentru titlurile știute doar după ID (sau găsite în altă limbă).
 *
 * Fiecare lot se salvează imediat, deci o rulare întreruptă continuă de unde a rămas.
 */
class TitleMatcher(
    private val db: AkVodDatabase,
    private val tmdb: TmdbClient,
    private val settings: TmdbSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.catalogDao()
    private val mutex = Mutex()

    suspend fun run(budgetMs: Long): MatchOutcome =
        mutex.withLock {
            val deadline = clock() + budgetMs
            val outcome =
                try {
                    linkProviderIds()
                    val key = settings.apiKey()
                    when {
                        key == null -> MatchOutcome.NEEDS_KEY
                        PlaybackActivityState.active -> MatchOutcome.MORE
                        !searchPending(key, deadline) -> MatchOutcome.MORE
                        !fetchDetails(key, deadline) -> MatchOutcome.MORE
                        else -> MatchOutcome.DONE
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TmdbAuthException) {
                    MatchOutcome.BAD_KEY
                } catch (e: IOException) {
                    MatchOutcome.NETWORK
                }
            db.playlistDao().inheritSeriesTitles()
            settings.recordRun(
                error =
                    when (outcome) {
                        MatchOutcome.BAD_KEY -> "Cheia TMDB a fost respinsă. Verifică-o în Setări."
                        MatchOutcome.NETWORK -> "TMDB nu a putut fi contactat; reîncerc mai târziu."
                        else -> null
                    },
                at = clock(),
            )
            outcome
        }

    // ---------------------------------------------------------------- 1. ID-uri de la furnizor

    private suspend fun linkKeepingLibrary(ids: List<Long>, titleId: Long) {
        dao.carryWatchlist(ids, titleId)
        dao.carryHistory(ids, titleId)
        dao.carryEpisodeProgress(ids, titleId)
        dao.carryWatchState(ids, titleId)
        dao.link(ids, titleId)
    }

    private suspend fun linkProviderIds() {
        while (true) {
            val batch = dao.pendingWithProviderId(BATCH)
            if (batch.isEmpty()) return
            db.withTransaction {
                batch
                    .groupBy { mediaType(it.kind) to it.tmdbId!! }
                    .forEach { (typeAndId, entries) ->
                        val (type, tmdbId) = typeAndId
                        val first = entries.first()
                        val parsed = parsed(first)
                        val titleId =
                            ensureTitle(type, tmdbId) {
                                TitleEntity(
                                    mediaType = type,
                                    tmdbId = tmdbId,
                                    name = parsed.title.ifBlank { first.name },
                                    year = parsed.year,
                                    detailsFetched = false,
                                    updatedAt = clock(),
                                )
                            }
                        entries.map { it.id }.chunked(SQL_CHUNK).forEach { linkKeepingLibrary(it, titleId) }
                    }
            }
        }
    }

    // ---------------------------------------------------------------- 2. căutare după nume

    /** Întoarce false dacă s-a terminat timpul înainte de a termina. */
    private suspend fun searchPending(
        key: String,
        deadline: Long,
    ): Boolean {
        while (true) {
            if (clock() > deadline || PlaybackActivityState.active) return false
            val batch = dao.pendingSearch(BATCH)
            if (batch.isEmpty()) return true

            val queries = batch.associate { it.id to query(it) }
            val noTitle = batch.filter { queries[it.id] == null }.map { it.id }
            val unique = queries.values.filterNotNull().associateBy { it.key }
            val cached =
                unique.keys
                    .chunked(SQL_CHUNK)
                    .flatMap { dao.cached(it) }
                    .associateBy { it.queryKey }
            val toSearch = unique.values.filter { it.key !in cached }
            val found = search(key, toSearch)

            db.withTransaction {
                val now = clock()
                dao.putCache(
                    toSearch.map { q ->
                        val hit = found[q.key]
                        MatchCacheEntity(q.key, hit?.item?.id, hit?.score ?: 0.0, now)
                    },
                )
                // cheie de cache → ID-ul titlului local (null = nepotrivit)
                val titleIds = HashMap<String, Long?>()
                for ((k, q) in unique) {
                    val hit = found[k]
                    titleIds[k] =
                        when {
                            hit != null -> {
                                val item = hit.item
                                ensureTitle(q.type, item.id) { titleOf(q.type, item, hit.english, now) }
                            }

                            cached[k]?.tmdbId != null -> {
                                val tmdbId = cached.getValue(k).tmdbId!!
                                ensureTitle(q.type, tmdbId) {
                                    TitleEntity(
                                        mediaType = q.type,
                                        tmdbId = tmdbId,
                                        name = q.title,
                                        year = q.year,
                                        detailsFetched = false,
                                        updatedAt = now,
                                    )
                                }
                            }

                            else -> {
                                null
                            }
                        }
                }
                val unmatched = ArrayList<Long>(noTitle)
                batch
                    .filter { queries[it.id] != null }
                    .groupBy { titleIds[queries.getValue(it.id)!!.key] }
                    .forEach { (titleId, entries) ->
                        val ids = entries.map { it.id }
                        if (titleId ==
                            null
                        ) {
                            unmatched += ids
                        } else {
                            ids.chunked(SQL_CHUNK).forEach { linkKeepingLibrary(it, titleId) }
                        }
                    }
                unmatched.chunked(SQL_CHUNK).forEach { dao.setState(it, MatchState.UNMATCHED) }
            }
        }
    }

    private data class SearchHit(val item: TmdbItem, val score: Double, val english: Boolean)

    /** Caută în paralel (limitat) fiecare titlu unic. */
    private suspend fun search(
        key: String,
        queries: List<MatchQuery>,
    ): Map<String, SearchHit> {
        if (queries.isEmpty()) return emptyMap()
        val permits = Semaphore(PARALLEL)
        return coroutineScope {
            queries
                .map { q -> async { permits.withPermit { q.key to searchOne(key, q) } } }
                .awaitAll()
                .mapNotNull { (k, hit) -> hit?.let { k to it } }
                .toMap()
        }
    }

    private suspend fun searchOne(
        key: String,
        query: MatchQuery,
    ): SearchHit? {
        for (request in MatchScorer.plan(query)) {
            val results = tmdb.search(key, request).take(MAX_CANDIDATES)
            val best = MatchScorer.best(query, results.map { it.candidate() }) ?: continue
            if (best.score >= MatchScorer.THRESHOLD) {
                val item = results.first { it.id == best.candidate.tmdbId }
                return SearchHit(item, best.score, request.language == null || request.language == "en-US")
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 3. detalii

    private suspend fun fetchDetails(
        key: String,
        deadline: Long,
    ): Boolean {
        var after = 0L
        while (true) {
            if (clock() > deadline || PlaybackActivityState.active) return false
            val titles = dao.titlesWithoutDetails(after, DETAILS_BATCH)
            if (titles.isEmpty()) return true
            val permits = Semaphore(PARALLEL)
            val details =
                withTimeoutOrNull((deadline - clock()).coerceAtLeast(1L)) {
                    titles
                        .map { t ->
                            async {
                                permits.withPermit {
                                    if (PlaybackActivityState.active || clock() >= deadline) {
                                        null
                                    } else {
                                        t to tmdb.details(key, t.mediaType, t.tmdbId)
                                    }
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                } ?: return false
            db.withTransaction {
                val now = clock()
                for ((title, item) in details) {
                    if (item == null) {
                        // ID inexistent pe TMDB (greșit la furnizor): intrările se caută din nou după nume.
                        dao.unlinkTitle(title.id)
                        dao.deleteTitle(title.id)
                    } else {
                        // Reîncărcăm doar metadatele: rezumatele surselor și datele locale rămân intacte.
                        val current = dao.titleById(title.id) ?: continue
                        val imdbId = item.imdb ?: current.imdbId
                        dao.updateTitle(
                            current.copy(
                                name = item.displayTitle.ifBlank { current.name },
                                originalName = item.original ?: current.originalName,
                                year = item.year ?: current.year,
                                overview = item.overview?.ifBlank { null } ?: current.overview,
                                posterPath = item.posterPath ?: current.posterPath,
                                backdropPath = item.backdropPath ?: current.backdropPath,
                                rating = item.voteAverage ?: current.rating,
                                voteCount = item.voteCount ?: current.voteCount,
                                popularity = item.popularity ?: current.popularity,
                                genreIds = item.allGenreIds.joinToString(",").ifEmpty { current.genreIds },
                                originalLanguage = item.originalLanguage ?: current.originalLanguage,
                                imdbId = imdbId,
                                imdbRating = current.imdbRating.takeIf { imdbId == current.imdbId },
                                imdbVotes = current.imdbVotes.takeIf { imdbId == current.imdbId },
                                imdbBadge = current.imdbBadge.takeIf { imdbId == current.imdbId },
                                runtimeMinutes = item.runtimeMinutes,
                                certification = item.certification,
                                originCountries =
                                    item.countries.takeIf { it.isNotEmpty() }?.joinToString(
                                        ",",
                                        ",",
                                        ",",
                                    ),
                                tagline = item.tagline?.trim()?.ifEmpty { null },
                                metadataFetched = true,
                                detailsFetched = true,
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }
            if (details.size < titles.size) return false
            after = titles.last().id
        }
    }

    // ---------------------------------------------------------------- ajutătoare

    /** ID-ul local al titlului TMDB; îl creează cu [create] dacă nu există. */
    private suspend fun ensureTitle(
        type: MediaType,
        tmdbId: Long,
        create: () -> TitleEntity,
    ): Long {
        dao.title(type, tmdbId)?.let { return it.id }
        val id = dao.insertTitle(create())
        return if (id > 0) id else dao.title(type, tmdbId)!!.id
    }

    private fun titleOf(
        type: MediaType,
        item: TmdbItem,
        complete: Boolean,
        now: Long,
    ) = TitleEntity(
        mediaType = type,
        tmdbId = item.id,
        name = item.displayTitle,
        originalName = item.original,
        year = item.year,
        overview = item.overview?.takeIf { complete && it.isNotBlank() },
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        rating = item.voteAverage,
        voteCount = item.voteCount,
        popularity = item.popularity,
        genreIds = item.allGenreIds.joinToString(",").ifEmpty { null },
        originalLanguage = item.originalLanguage,
        imdbId = item.imdb,
        detailsFetched = complete,
        updatedAt = now,
    )

    private fun parsed(entry: PendingEntry): ParsedTitle =
        if (entry.title != null) {
            ParsedTitle(
                title = entry.title,
                year = entry.year,
                languages = entry.languages?.split(',').orEmpty(),
            )
        } else {
            // Intrare importată înainte de etapa 3 (încă neparsată).
            TitleParser.parse(entry.name, entry.groupTitle)
        }

    private fun query(entry: PendingEntry): MatchQuery? {
        val p = parsed(entry)
        if (p.title.isBlank()) return null
        return MatchQuery(mediaType(entry.kind), p.title, p.year, p.languages.singleOrNull())
    }

    private fun mediaType(kind: VodKind) = if (kind == VodKind.MOVIE) MediaType.MOVIE else MediaType.TV

    private companion object {
        const val BATCH = 500
        const val DETAILS_BATCH = 100
        const val PARALLEL = 6
        const val MAX_CANDIDATES = 10
        const val SQL_CHUNK = 500
    }
}
