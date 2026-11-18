package io.github.radumariusi.akvodhub.data.catalog

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.ContinueItem
import io.github.radumariusi.akvodhub.data.db.DiscoverCacheEntity
import io.github.radumariusi.akvodhub.data.db.EpisodeProgressEntity
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.db.WatchHistoryEntity
import io.github.radumariusi.akvodhub.data.db.WatchStateEntity
import io.github.radumariusi.akvodhub.data.db.WatchlistEntity
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.catalog.CatalogCursor
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogQuery
import io.github.radumariusi.akvodhub.parser.catalog.CatalogSort
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeEntry
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeMerge
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.catalog.LocalizedOverview
import io.github.radumariusi.akvodhub.parser.catalog.LogoCandidate
import io.github.radumariusi.akvodhub.parser.catalog.MergedSeason
import io.github.radumariusi.akvodhub.parser.catalog.NewsCursor
import io.github.radumariusi.akvodhub.parser.catalog.PersonalLibrary
import io.github.radumariusi.akvodhub.parser.catalog.PlaylistNews
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.catalog.TitleLogo
import io.github.radumariusi.akvodhub.parser.catalog.Trailer
import io.github.radumariusi.akvodhub.parser.catalog.TrailerCandidate
import io.github.radumariusi.akvodhub.parser.match.QualityGroup
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.player.SourceProfile
import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Un actor (sau regizor) de pe pagina de detalii. */
data class Person(
    val name: String,
    val role: String?,
    val profilePath: String?,
    val id: Long = 0,
)

data class Credits(
    val directors: List<String>,
    val cast: List<Person>,
)

/** O sursă de pe pagina de detalii: intrarea din playlist + numele playlist-ului. */
data class SourceDetails(
    val entry: RawEntryEntity,
    val playlist: String,
)

data class TitleDetails(
    val title: TitleEntity,
    /** Sursele filmului, grupate după calitate, apoi limbă (goale la seriale: acolo contează episoadele). */
    val groups: List<QualityGroup>,
    /** Rezumatul pe un rând, ex. „4K: EN, DE · 1080p: RO, FR, IT, +4”. */
    val summary: String,
    val sources: Map<Long, SourceDetails>,
    val genres: List<String>,
)

data class CatalogPage(val items: List<TitleCard>, val next: CatalogCursor?)

data class NewsPage(val items: List<TitleCard>, val cursor: NewsCursor?, val endReached: Boolean)

data class SeriesEpisodes(
    val seasons: List<MergedSeason>,
    val sources: Map<Long, SourceDetails>,
)

/**
 * Datele pentru hub: liste paginate (Acasă, Filme, Seriale, Căutare), pagina de detalii cu sursele grupate,
 * episoadele unite ale serialelor, distribuția (de la TMDB, la cerere) și linkul de redare al unei surse.
 */
class HubRepository(
    private val db: AkVodDatabase,
    private val indexer: CatalogIndexer,
    private val importer: PlaylistImporter,
    private val tmdb: TmdbClient,
    private val settings: TmdbSettings,
    private val playback: PlaybackSettings,
) {
    private val dao = db.catalogDao()
    private val playlistDao = db.playlistDao()
    private val logoMutex = Mutex()
    private val trailerMutex = Mutex()
    private val creditsCache = ConcurrentHashMap<Long, Credits>()

    /** Episoadele Xtream cerute de la server în sesiunea curentă (o dată pe serial și playlist). */
    private val episodesLoaded: MutableSet<Long> = Collections.newSetFromMap(ConcurrentHashMap())

    val catalogVersion: StateFlow<Long> get() = indexer.version

    val sourcePreferences get() = playback.state

    /** Limbile preferate la ordonarea surselor: cele din profilul de redare, limba sistemului, apoi engleza. */
    val preferredLanguages: List<String> get() = playback.preferredLanguages()

    /** Profilul de alegere automată a sursei (Setări → Redare). */
    val profile: SourceProfile get() = playback.state.value.profile

    /** Sursele în ordinea în care le încearcă playerul (profilul, apoi calitate și limbă). */
    fun playOrder(
        sources: List<SourceInfo>,
        originalLanguage: String? = null,
    ): List<SourceInfo> =
        profile.order(
            sources,
            preferredLanguages,
            originalLanguage.takeIf { playback.state.value.preferOriginalLanguage },
        )

    fun sourceReason(source: SourceInfo, originalLanguage: String?): String {
        val original = originalLanguage?.lowercase()
        return when {
            playback.state.value.preferOriginalLanguage && original in source.languages ->
                "limba originală declarată în playlist"
            else -> profile.rules.firstOrNull { it.matches(source) }?.let { "regula ${it.label()} din profil" }
                ?: "ordinea calității și a limbilor preferate"
        }
    }

    /** Agregări SQL la deschiderea panoului; doar grupurile de limbi se parcurg în flux. */
    suspend fun statistics(): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sql = db.openHelper.readableDatabase
            val totals =
                sql
                    .query(
                        "SELECT COUNT(CASE WHEN mediaType = 'MOVIE' THEN 1 END), " +
                            "COUNT(CASE WHEN mediaType = 'TV' THEN 1 END), " +
                            "COUNT(CASE WHEN qualities LIKE '%,UHD,%' THEN 1 END), " +
                            "COUNT(CASE WHEN tmdbId > 0 THEN 1 END), COUNT(*) FROM title WHERE sourceCount > 0",
                    ).use { c ->
                        c.moveToFirst()
                        "${c.getLong(0)} filme · ${c.getLong(1)} seriale · ${c.getLong(2)} titluri 4K\n" +
                            "Potrivite TMDB: ${c.getLong(3)} din ${c.getLong(4)} titluri vizibile"
                    }
            val languages = mutableMapOf<String, Long>()
            sql.query("SELECT languages, COUNT(*) FROM raw_entry WHERE visible = 1 GROUP BY languages").use { c ->
                while (c.moveToNext()) {
                    val codes =
                        c
                            .getString(0)
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .distinct()
                    if (codes.isEmpty()) languages["necunoscută"] = (languages["necunoscută"] ?: 0L) + c.getLong(1)
                    codes.forEach { code -> languages[code] = (languages[code] ?: 0L) + c.getLong(1) }
                }
            }
            totals + "\nSurse vizibile pe limbi (etichete din playlist):\n" +
                languages.entries
                    .sortedByDescending { it.value }
                    .take(
                        30,
                    ).joinToString(" · ") { "${it.key.uppercase()}: ${it.value}" }
        }

    suspend fun inWatchlist(titleId: Long): Boolean = dao.inWatchlist(titleId)

    suspend fun isWatched(titleId: Long): Boolean = db.discoverDao().history(titleId)?.finished == true

    suspend fun setInWatchlist(titleId: Long, included: Boolean) {
        if (included) {
            dao.addToWatchlist(WatchlistEntity(titleId, System.currentTimeMillis()))
        } else {
            dao.removeFromWatchlist(titleId)
        }
        watchChanged.update { it + 1 }
    }

    suspend fun markWatched(titleId: Long, watched: Boolean) {
        val progress = PersonalLibrary.manualProgress(watched)
        db.withTransaction {
            db.discoverDao().putHistory(WatchHistoryEntity(titleId, progress, watched, System.currentTimeMillis()))
            // Acțiune explicită a utilizatorului: scoate reluarea veche, fără a inventa poziția/episodul.
            dao.deleteWatchState(titleId)
            dao.setPosterProgress(titleId, progress)
        }
        mutablePosterProgress.value = titleId to progress
        watchChanged.update { it + 1 }
    }

    suspend fun watchlistPage(afterId: Long, limit: Int): List<TitleCard> {
        val query = PersonalLibrary.page(afterId, limit)
        return dao.cards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    suspend fun episodeProgress(titleId: Long): List<EpisodeProgressEntity> = dao.episodeProgress(titleId)

    suspend fun episodeProgress(titleId: Long, season: Int, episode: Int): EpisodeProgressEntity? =
        dao.episodeProgress(titleId, season, episode)

    suspend fun watchState(titleId: Long): WatchStateEntity? = dao.watchState(titleId)

    private val watchChanged = MutableStateFlow(0L)
    private val mutablePosterProgress = MutableStateFlow<Pair<Long, Double>?>(null)
    val posterProgress = mutablePosterProgress.asStateFlow()

    /** Crește după fiecare salvare a progresului (Acasă și Detalii își reîncarcă „Continuă”). */
    val watchVersion: StateFlow<Long> = watchChanged.asStateFlow()

    suspend fun saveWatchState(state: WatchStateEntity) {
        db.withTransaction {
            dao.putWatchState(state)
            dao.setPosterProgress(state.titleId, PosterBadges.progress(state.positionMs, state.durationMs))
        }
        mutablePosterProgress.value = state.titleId to PosterBadges.progress(state.positionMs, state.durationMs)
        watchChanged.update { it + 1 }
    }

    /** Istoricul pentru recomandări: cât s-a văzut din titlu ([progress] 0–1) și dacă s-a terminat. */
    suspend fun recordWatch(
        titleId: Long,
        progress: Double,
        finished: Boolean,
        at: Long,
        episode: EpisodeProgressEntity? = null,
    ) {
        val history = db.discoverDao()
        db.withTransaction {
            // Păstrăm maximul progresului și „terminat”: o revedere parțială nu șterge ce s-a văzut.
            val old = history.history(titleId)
            val value = progress.coerceIn(0.0, 1.0)
            if (finished) dao.setPosterProgress(titleId, 1.0)
            if (episode != null) {
                val previous = dao.episodeProgress(titleId, episode.season, episode.episode)
                if (previous == null || episode.updatedAt >= previous.updatedAt) {
                    dao.putEpisodeProgress(episode.copy(finished = episode.finished || previous?.finished == true))
                }
            }
            history.putHistory(
                WatchHistoryEntity(
                    titleId = titleId,
                    progress = maxOf(old?.progress ?: 0.0, value),
                    finished = finished || old?.finished == true,
                    watchedAt = at,
                ),
            )
        }
        if (finished) mutablePosterProgress.value = titleId to 1.0
    }

    suspend fun clearWatchState(titleId: Long) {
        dao.deleteWatchState(titleId)
        watchChanged.update { it + 1 }
    }

    /** Pregătește catalogul (rezumate, titluri locale, căutare) dacă s-a schimbat ceva de la ultima dată. */
    suspend fun prepare() = indexer.refresh()

    suspend fun page(
        filter: CatalogFilter,
        limit: Int,
        after: CatalogCursor? = null,
    ): CatalogPage {
        val sections = CatalogQuery.sections(filter)
        val items = ArrayList<TitleCard>(limit)
        for (section in sections.dropWhile { after != null && it != after.section }) {
            val cursor = after?.takeIf { it.section == section }
            val query = CatalogQuery.page(filter, limit - items.size, section, cursor)
            val rows = dao.catalogCards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
            rows.mapTo(items) { it.card }
            if (items.size == limit) {
                val last = rows.last()
                val value =
                    when (filter.sort) {
                        CatalogSort.POPULAR -> last.popularity
                        CatalogSort.RECENT -> last.addedAt
                        CatalogSort.RATING -> last.card.rating
                        CatalogSort.TITLE -> last.card.name
                        CatalogSort.IMDB -> last.card.imdbRating
                        CatalogSort.RELEASE -> last.card.year
                        CatalogSort.PLAYLIST -> last.playlistAddedAt
                    }
                return CatalogPage(items, CatalogCursor(section, value, last.card.id))
            }
        }
        return CatalogPage(items, null)
    }

    suspend fun newsPage(episodes: Boolean, after: NewsCursor?, limit: Int): NewsPage {
        val query = PlaylistNews.page(episodes, after, limit, System.currentTimeMillis())
        val rows = dao.newsCards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
        return NewsPage(
            rows.map { it.card },
            rows.lastOrNull()?.let { NewsCursor(it.newsAt, it.card.id) },
            rows.size < limit,
        )
    }

    suspend fun continueWatching(limit: Int = 20): List<TitleCard> = dao.continueWatching(limit)

    /** „Continuă vizionarea” cu poziția și episodul (rândul Watch Next de pe ecranul principal Android TV). */
    suspend fun continueItems(limit: Int): List<ContinueItem> = dao.continueItems(limit)

    /** Limbile audio din playlist-uri, cele mai frecvente primele (ISO 639-1, `mul` = mai multe). */
    suspend fun languages(): List<String> =
        dao
            .languageCombinations()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_LANGUAGES)

    suspend fun details(titleId: Long): TitleDetails? {
        val title = dao.titleById(titleId) ?: return null
        val entries = dao.sources(titleId).filter { it.kind == VodKind.MOVIE }
        val sources = describe(entries)
        val groups = SourceGrouping.group(entries.map { it.toSourceInfo() }, preferredLanguages)
        return TitleDetails(
            title = title,
            groups = groups,
            summary = SourceGrouping.summary(groups),
            sources = sources,
            genres = Genres.names(title.genreIds),
        )
    }

    /** Numai apăsarea butonului cere trailerul; cache inclusiv pentru rezultate absente. */
    suspend fun trailer(titleId: Long): String? =
        trailerMutex.withLock {
            val title = dao.titleById(titleId) ?: return@withLock null
            if (title.isLocal) return@withLock null
            val cache = db.discoverDao()
            val cacheKey = "trailer:${title.mediaType}:${title.tmdbId}"
            val old = cache.cached(cacheKey)
            val now = System.currentTimeMillis()
            if (old != null && LocalizedOverview.isCached(old.fetchedAt, now)) return@withLock old.data.ifBlank { null }
            try {
                val key = settings.apiKey() ?: throw java.io.IOException("Cheia TMDB lipsește.")
                val videos = tmdb.videos(key, title.mediaType, title.tmdbId)
                val id =
                    Trailer.choose(
                        videos.results.map {
                            TrailerCandidate(it.key, it.site, it.type, it.official, it.language)
                        },
                    )
                db.withTransaction {
                    cache.putCache(DiscoverCacheEntity(cacheKey, id.orEmpty(), now))
                    cache.trimTrailers()
                }
                id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (old != null) old.data.ifBlank { null } else throw e
            }
        }

    /** Numai hero stabil/fișă: rezultat (inclusiv absent) salvat 30 zile, maximum 128 titluri. */
    suspend fun logo(titleId: Long): String? =
        logoMutex.withLock {
            val title = dao.titleById(titleId) ?: return@withLock null
            if (title.isLocal) return@withLock null
            val cache = db.discoverDao()
            val cacheKey = "logo:en:${title.mediaType}:${title.tmdbId}"
            val old = cache.cached(cacheKey)
            val now = System.currentTimeMillis()
            if (old != null && LocalizedOverview.isCached(old.fetchedAt, now)) return@withLock old.data.ifBlank { null }
            try {
                val key = settings.apiKey() ?: return@withLock old?.data?.ifBlank { null }
                val images = tmdb.images(key, title.mediaType, title.tmdbId)
                val path =
                    TitleLogo.choose(
                        images.logos.map { LogoCandidate(it.path, it.language, it.votes) },
                        title.originalLanguage,
                    )
                db.withTransaction {
                    cache.putCache(DiscoverCacheEntity(cacheKey, path.orEmpty(), now))
                    cache.trimLogos()
                }
                path
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                old?.data?.ifBlank { null }
            }
        }

    /**
     * Episoadele unui serial din toate sursele, unite pe sezon + episod. La Xtream, lista de episoade se cere
     * de la server la prima deschidere în sesiune ([refresh] = true), apoi vine din baza locală.
     */
    suspend fun episodes(
        titleId: Long,
        refresh: Boolean,
    ): SeriesEpisodes {
        if (refresh) {
            loadXtreamEpisodes(titleId)
            indexer.refresh()
        }
        val entries = dao.episodes(titleId)
        val sources = describe(entries)
        val seasons =
            EpisodeMerge.merge(
                entries.map { EpisodeEntry(it.season, it.episode, it.name, it.toSourceInfo()) },
                preferredLanguages,
            )
        return SeriesEpisodes(seasons, sources)
    }

    /** Cere episoadele serialelor Xtream legate de titlu (în paralel, câte un apel pe playlist). */
    private suspend fun loadXtreamEpisodes(titleId: Long) {
        val series = dao.sources(titleId).filter { it.kind == VodKind.SERIES && it.streamId != null }
        if (series.isEmpty()) return
        coroutineScope {
            series
                .filter { episodesLoaded.add(it.id) }
                .map { s ->
                    async {
                        try {
                            importer.loadXtreamEpisodes(s.sourceId, s.streamId!!)
                        } catch (e: CancellationException) {
                            episodesLoaded.remove(s.id)
                            throw e
                        } catch (e: Exception) {
                            // Serverul nu răspunde: rămân episoadele știute; reîncercăm la următoarea deschidere.
                            episodesLoaded.remove(s.id)
                        }
                    }
                }.awaitAll()
        }
    }

    suspend fun episodeOverview(title: TitleEntity, season: Int, episode: Int): String? {
        if (title.isLocal || season < 0 || episode <= 0) return null
        val cache = db.discoverDao()
        val cacheKey = "episode:en:${title.tmdbId}:$season:$episode"
        val now = System.currentTimeMillis()
        val old = cache.cached(cacheKey)
        if (old != null && LocalizedOverview.isCached(old.fetchedAt, now)) return old.data.ifBlank { null }
        val key = settings.apiKey() ?: return old?.data?.ifBlank { null }
        val text = tmdb.episodeOverview(key, title.tmdbId, season, episode)
        db.withTransaction {
            cache.putCache(DiscoverCacheEntity(cacheKey, text.orEmpty(), now))
            cache.trimEpisodes()
        }
        return text
    }

    /** Distribuția de la TMDB (o cerere, ținută în memorie). null dacă nu avem cheie sau titlul e local. */
    suspend fun credits(title: TitleEntity): Credits? {
        if (title.isLocal) return null
        creditsCache[title.id]?.let { return it }
        val key = settings.apiKey() ?: return null
        val credits =
            try {
                tmdb.credits(key, title.mediaType, title.tmdbId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return null
        val result =
            Credits(
                directors =
                    credits.crew
                        .filter { it.job == "Director" }
                        .map { it.name }
                        .distinct()
                        .take(3),
                cast = credits.cast.take(MAX_CAST).map { Person(it.name, it.role, it.profilePath, it.id) },
            )
        creditsCache[title.id] = result
        return result
    }

    /**
     * ID-ul IMDb al titlului (pentru subtitrările din addon-uri). Dacă potrivirea nu l-a adus încă, îl cerem
     * de la TMDB o dată și îl salvăm. null la titlurile locale sau fără cheie TMDB.
     */
    suspend fun imdbId(title: TitleEntity): String? {
        title.imdbId?.takeIf { StremioIds.isImdb(it) }?.let { return it }
        if (title.isLocal) return null
        dao
            .titleById(title.id)
            ?.imdbId
            ?.takeIf { StremioIds.isImdb(it) }
            ?.let { return it }
        val key = settings.apiKey() ?: return null
        val imdb =
            try {
                tmdb.details(key, title.mediaType, title.tmdbId)?.imdb
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }?.takeIf { StremioIds.isImdb(it) } ?: return null
        dao.setImdbId(title.id, imdb)
        return imdb
    }

    /** Linkul de redare al unei surse (la Xtream se construiește din cont, ca parola să nu stea în fiecare intrare). */
    suspend fun streamUrl(entryId: Long): String? {
        val entry = dao.entry(entryId) ?: return null
        entry.url?.let { return it }
        val source = playlistDao.source(entry.sourceId) ?: return null
        if (source.type != PlaylistType.XTREAM || entry.streamId == null) return null
        val kind =
            when (entry.kind) {
                VodKind.MOVIE -> "movie"
                VodKind.EPISODE -> "series"
                VodKind.SERIES -> return null
            }
        return XtreamClient.streamUrl(importer.account(source), kind, entry.streamId, entry.containerExt)
    }

    private suspend fun describe(entries: List<RawEntryEntity>): Map<Long, SourceDetails> {
        if (entries.isEmpty()) return emptyMap()
        val names = playlistDao.sources().associate { it.id to it.name }
        return entries.associate { it.id to SourceDetails(it, names[it.sourceId].orEmpty()) }
    }

    private companion object {
        const val MAX_LANGUAGES = 30
        const val MAX_CAST = 15
    }
}
