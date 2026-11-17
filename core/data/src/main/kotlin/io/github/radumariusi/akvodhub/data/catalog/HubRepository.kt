package io.github.radumariusi.akvodhub.data.catalog

import androidx.sqlite.db.SimpleSQLiteQuery
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.db.TitleCard
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.db.WatchStateEntity
import io.github.radumariusi.akvodhub.data.db.isLocal
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.catalog.CatalogFilter
import io.github.radumariusi.akvodhub.parser.catalog.CatalogQuery
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeEntry
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeMerge
import io.github.radumariusi.akvodhub.parser.catalog.Genres
import io.github.radumariusi.akvodhub.parser.catalog.MergedSeason
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Un actor (sau regizor) de pe pagina de detalii. */
data class Person(
    val name: String,
    val role: String?,
    val profilePath: String?,
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
    private val creditsCache = ConcurrentHashMap<Long, Credits>()

    /** Episoadele Xtream cerute de la server în sesiunea curentă (o dată pe serial și playlist). */
    private val episodesLoaded: MutableSet<Long> = Collections.newSetFromMap(ConcurrentHashMap())

    val catalogVersion: StateFlow<Long> get() = indexer.version

    /** Limbile preferate la ordonarea surselor: cele din profilul de redare, limba sistemului, apoi engleza. */
    val preferredLanguages: List<String> get() = playback.preferredLanguages()

    /** Profilul de alegere automată a sursei (Setări → Redare). */
    val profile: SourceProfile get() = playback.state.value.profile

    /** Sursele în ordinea în care le încearcă playerul (profilul, apoi calitate și limbă). */
    fun playOrder(sources: List<SourceInfo>): List<SourceInfo> = profile.order(sources, preferredLanguages)

    suspend fun watchState(titleId: Long): WatchStateEntity? = dao.watchState(titleId)

    private val watchChanged = MutableStateFlow(0L)

    /** Crește după fiecare salvare a progresului (Acasă și Detalii își reîncarcă „Continuă”). */
    val watchVersion: StateFlow<Long> = watchChanged.asStateFlow()

    suspend fun saveWatchState(state: WatchStateEntity) {
        dao.putWatchState(state)
        watchChanged.update { it + 1 }
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
        offset: Int,
    ): List<TitleCard> {
        val q = CatalogQuery.page(filter, limit, offset)
        return dao.cards(SimpleSQLiteQuery(q.sql, q.args.toTypedArray()))
    }

    suspend fun continueWatching(limit: Int = 20): List<TitleCard> = dao.continueWatching(limit)

    /** Limbile audio din playlist-uri, cele mai frecvente primele (ISO 639-1, `mul` = mai multe). */
    suspend fun languages(): List<String> =
        dao
            .languageCombinations()
            .flatMap { it.split(',') }
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

    /**
     * Episoadele unui serial din toate sursele, unite pe sezon + episod. La Xtream, lista de episoade se cere
     * de la server la prima deschidere în sesiune ([refresh] = true), apoi vine din baza locală.
     */
    suspend fun episodes(
        titleId: Long,
        refresh: Boolean,
    ): SeriesEpisodes {
        if (refresh) loadXtreamEpisodes(titleId)
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
                            throw e
                        } catch (e: Exception) {
                            // Serverul nu răspunde: rămân episoadele știute; reîncercăm la următoarea deschidere.
                            episodesLoaded.remove(s.id)
                        }
                    }
                }.awaitAll()
        }
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
                cast = credits.cast.take(MAX_CAST).map { Person(it.name, it.role, it.profilePath) },
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
