package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.DiscoverCacheEntity
import io.github.radumariusi.akvodhub.data.db.MatchState
import io.github.radumariusi.akvodhub.data.db.PlaylistSourceEntity
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamAccount
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamException
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
import io.github.radumariusi.akvodhub.parser.playlist.M3uParser
import io.github.radumariusi.akvodhub.parser.playlist.VodClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Cât a avansat un import. [scanned] = intrări citite (inclusiv Live TV ignorat), [kept] = VOD păstrat. */
data class ImportProgress(
    val sourceId: Long,
    val phase: String,
    val scanned: Int,
    val kept: Int,
)

data class ImportResult(
    val movies: Int,
    val series: Int,
    val episodes: Int,
    val added: Int,
    val removed: Int,
    val durationMs: Long,
)

class ImportException(
    message: String,
) : Exception(message)

/**
 * Importă un playlist în baza de date: citește în flux, păstrează doar filmele și serialele,
 * scrie în loturi și publică modificările numai după citirea completă a răspunsului.
 */
class PlaylistImporter(
    private val context: Context,
    private val db: AkVodDatabase,
    private val cipher: SecretCipher,
    private val http: OkHttpClient,
    private val xtream: XtreamClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.playlistDao()
    private val mutex = Mutex()
    private val episodeLocks = List(64) { Mutex() }
    private val episodeRequests = Semaphore(3)
    private val contentPrefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    suspend fun import(
        sourceId: Long,
        onProgress: suspend (ImportProgress) -> Unit,
    ): ImportResult =
        mutex.withLock {
            val source = dao.source(sourceId) ?: throw ImportException("Playlist-ul nu mai există.")
            val start = clock()
            var operation = "Pregătire"
            try {
                withContext(Dispatchers.IO) {
                    val catalog =
                        if (source.type == PlaylistType.XTREAM) {
                            prepareXtream(source, onProgress)
                        } else {
                            null
                        }
                    try {
                        operation = if (catalog == null) "Citire M3U" else "Aplicare Xtream"
                        db.withTransaction {
                            val current = dao.source(sourceId) ?: throw ImportException("Playlist-ul nu mai există.")
                            val run = maxOf(start, (current.lastImportAt ?: 0) + 1)
                            val writer = BatchWriter(sourceId, run)
                            val kinds =
                                if (catalog == null) {
                                    importM3u(source, writer, onProgress)
                                } else {
                                    importXtream(sourceId, catalog, writer, onProgress)
                                }
                            writer.flush()
                            if (writer.total == 0) {
                                throw ImportException("Playlist-ul nu conține filme sau seriale.")
                            }
                            operation = "Finalizare"
                            onProgress(ImportProgress(sourceId, "Se finalizează…", writer.scanned, writer.total))
                            var removed = dao.deleteStale(sourceId, run, kinds)
                            if (catalog != null) removed += dao.deleteOrphanEpisodes(sourceId)
                            val result =
                                ImportResult(
                                    movies = writer.count(VodKind.MOVIE),
                                    series = writer.count(VodKind.SERIES),
                                    episodes = writer.count(VodKind.EPISODE),
                                    added = writer.added,
                                    removed = removed,
                                    durationMs = clock() - start,
                                )
                            dao.updateSource(
                                current.copy(
                                    lastImportAt = maxOf(clock(), run),
                                    lastImportError = null,
                                    lastAdded = result.added,
                                    lastRemoved = result.removed,
                                    lastDurationMs = result.durationMs,
                                ),
                            )
                            result
                        }
                    } finally {
                        catalog?.directory?.deleteRecursively()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = userMessage(operation, e)
                dao.setImportError(sourceId, message)
                throw ImportException(message)
            }
        }

    /** Episoadele unui serial Xtream, cerute la nevoie (un apel pe serial) și salvate ca intrări EPISODE. */
    suspend fun loadXtreamEpisodes(
        sourceId: Long,
        seriesId: Long,
        onlyIfStale: Boolean = false,
    ): List<RawEntryEntity> =
        episodeLocks[((sourceId * 31 + seriesId).hashCode() and Int.MAX_VALUE) % episodeLocks.size].withLock {
            val source = dao.source(sourceId) ?: return@withLock emptyList()
            if (source.type != PlaylistType.XTREAM) return@withLock dao.episodes(sourceId, seriesId)
            val parent = dao.xtreamSeries(sourceId, seriesId) ?: return@withLock emptyList()
            if (onlyIfStale && episodeSnapshotCurrent(parent)) return@withLock dao.episodes(sourceId, seriesId)
            val account = account(source)
            val episodes = episodeRequests.withPermit { xtream.episodes(account, seriesId) }
            db.withTransaction {
                val series = dao.xtreamSeries(sourceId, seriesId) ?: return@withTransaction emptyList()
                val now = clock()
                val firstSeen =
                    if (dao.hasEpisodes(sourceId, seriesId)) {
                        now
                    } else {
                        series.firstSeenAt
                    }
                val writer = BatchWriter(sourceId, run = now)
                episodes.forEach { e ->
                    writer.add(
                        RawEntryEntity(
                            sourceId = sourceId,
                            providerKey = "episode:${e.id}",
                            kind = VodKind.EPISODE,
                            name = e.title,
                            groupTitle = null,
                            url = null,
                            logo = null,
                            streamId = e.id,
                            seriesId = seriesId,
                            season = e.season,
                            episode = e.episode,
                            containerExt = e.containerExtension,
                            providerAddedAt = e.added,
                            providerQuality = e.quality,
                            providerLanguages = e.language,
                            firstSeenAt = firstSeen,
                            seenRun = now,
                            matchState = MatchState.INHERITED,
                        ),
                    )
                }
                writer.flush()
                dao.inheritSeriesTitle(sourceId, seriesId)
                applyEpisodeVisibility(sourceId, seriesId, series.visible)
                val cache = db.discoverDao()
                cache.putCache(
                    DiscoverCacheEntity("episodes-check:$sourceId:$seriesId", parent.seenRun.toString(), now),
                )
                cache.trimEpisodeChecks()
                dao.episodes(sourceId, seriesId)
            }
        }

    suspend fun episodeSnapshotCurrent(parent: RawEntryEntity): Boolean {
        val run = db.discoverDao().cached("episodes-check:${parent.sourceId}:${parent.streamId}") ?: return false
        return run.data == parent.seenRun.toString()
    }

    private fun applyEpisodeVisibility(sourceId: Long, seriesId: Long, visible: Boolean) {
        val sql = db.openHelper.writableDatabase
        val scope = "sourceId = ? AND seriesId = ? AND kind = 'EPISODE'"
        sql.execSQL(
            "UPDATE raw_entry SET visible = ? WHERE $scope",
            arrayOf(if (visible) 1 else 0, sourceId, seriesId),
        )
        if (visible) {
            val languages = contentPrefs.getString("hidden_languages", "").orEmpty()
            val hidden = ContentVisibility.hiddenSources(languages, "")
            sql.execSQL("${hidden.sql} AND $scope", (hidden.args + listOf(sourceId, seriesId)).toTypedArray())
        }
    }

    private suspend fun importM3u(
        source: PlaylistSourceEntity,
        writer: BatchWriter,
        onProgress: suspend (ImportProgress) -> Unit,
    ): List<VodKind> {
        val now = writer.run
        val phase = "Se citește playlist-ul…"
        onProgress(ImportProgress(source.id, phase, 0, 0))
        openM3u(source).use { input ->
            val reader = input.bufferedReader(Charsets.UTF_8)
            for (entry in M3uParser.entries(reader)) {
                writer.scanned++
                val kind = VodClassifier.classify(entry.url, entry.groupTitle, entry.name)
                if (kind != null) {
                    writer.add(
                        RawEntryEntity(
                            sourceId = source.id,
                            providerKey = entry.url,
                            kind = kind,
                            name = entry.name,
                            groupTitle = entry.groupTitle,
                            url = entry.url,
                            logo = entry.logo,
                            tmdbId = (entry.attributes["tvg-tmdb"] ?: entry.attributes["tmdb"])?.toLongOrNull(),
                            firstSeenAt = now,
                            seenRun = now,
                        ),
                    )
                }
                if (writer.scanned % PROGRESS_EVERY == 0) {
                    onProgress(ImportProgress(source.id, phase, writer.scanned, writer.total + writer.pending))
                }
            }
        }
        return listOf(VodKind.MOVIE, VodKind.EPISODE)
    }

    private fun openM3u(source: PlaylistSourceEntity): InputStream {
        val location = cipher.decrypt(source.location)
        return when (source.type) {
            PlaylistType.M3U_FILE -> {
                context.contentResolver.openInputStream(Uri.parse(location))
                    ?: throw ImportException("Nu pot deschide fișierul. Adaugă-l din nou.")
            }

            else -> {
                val response = http.newCall(Request.Builder().url(location).build()).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw ImportException("Serverul a răspuns cu eroarea ${response.code}.")
                }
                response.body.byteStream()
            }
        }
    }

    private data class XtreamCatalog(
        val directory: File,
        val movies: File,
        val series: File,
        val movieGroups: Map<String, String>,
        val seriesGroups: Map<String, String>,
    )

    private suspend fun prepareXtream(
        source: PlaylistSourceEntity,
        onProgress: suspend (ImportProgress) -> Unit,
    ): XtreamCatalog {
        val account = account(source)
        onProgress(ImportProgress(source.id, "Se conectează la server…", 0, 0))
        xtream.authenticate(account)
        val movieGroups = xtream.vodCategories(account)
        val seriesGroups = xtream.seriesCategories(account)
        val directory = File(context.cacheDir, "xtream-import")
        directory.deleteRecursively()
        if (!directory.mkdir()) throw ImportException("Nu pot pregăti fișierele temporare pentru import.")
        val movies = File(directory, "movies.json")
        val series = File(directory, "series.json")
        var complete = false
        try {
            onProgress(ImportProgress(source.id, "Se descarcă lista de filme…", 0, 0))
            xtream.downloadCatalog(account, "get_vod_streams", movies)
            onProgress(ImportProgress(source.id, "Se descarcă lista de seriale…", 0, 0))
            xtream.downloadCatalog(account, "get_series", series)
            complete = true
            return XtreamCatalog(directory, movies, series, movieGroups, seriesGroups)
        } finally {
            if (!complete) directory.deleteRecursively()
        }
    }

    private suspend fun importXtream(
        sourceId: Long,
        catalog: XtreamCatalog,
        writer: BatchWriter,
        onProgress: suspend (ImportProgress) -> Unit,
    ): List<VodKind> {
        val now = writer.run
        onProgress(ImportProgress(sourceId, "Filme…", writer.scanned, writer.total))
        xtream.movies(catalog.movies) { movies ->
            for (m in movies) {
                writer.scanned++
                writer.add(
                    RawEntryEntity(
                        sourceId = sourceId,
                        providerKey = "movie:${m.streamId}",
                        kind = VodKind.MOVIE,
                        name = m.name,
                        groupTitle = m.categoryId?.let(catalog.movieGroups::get),
                        url = null,
                        logo = m.icon,
                        streamId = m.streamId,
                        containerExt = m.containerExtension,
                        tmdbId = m.tmdbId,
                        providerAddedAt = m.added,
                        providerQuality = m.quality,
                        firstSeenAt = now,
                        seenRun = now,
                    ),
                )
                if (writer.scanned % PROGRESS_EVERY == 0) {
                    onProgress(ImportProgress(sourceId, "Filme…", writer.scanned, writer.total + writer.pending))
                }
            }
        }

        onProgress(ImportProgress(sourceId, "Seriale…", writer.scanned, writer.total + writer.pending))
        xtream.series(catalog.series) { series ->
            for (s in series) {
                writer.scanned++
                writer.add(
                    RawEntryEntity(
                        sourceId = sourceId,
                        providerKey = "series:${s.seriesId}",
                        kind = VodKind.SERIES,
                        name = s.name,
                        groupTitle = s.categoryId?.let(catalog.seriesGroups::get),
                        url = null,
                        logo = s.cover,
                        streamId = s.seriesId,
                        tmdbId = s.tmdbId,
                        providerAddedAt = s.lastModified,
                        providerQuality = s.quality,
                        firstSeenAt = now,
                        seenRun = now,
                    ),
                )
                if (writer.scanned % PROGRESS_EVERY == 0) {
                    onProgress(ImportProgress(sourceId, "Seriale…", writer.scanned, writer.total + writer.pending))
                }
            }
        }
        return listOf(VodKind.MOVIE, VodKind.SERIES)
    }

    fun account(source: PlaylistSourceEntity): XtreamAccount =
        XtreamAccount(
            server = cipher.decrypt(source.location),
            username = source.username?.let(cipher::decrypt).orEmpty(),
            password = source.password?.let(cipher::decrypt).orEmpty(),
        )

    /**
     * Scrie intrările în loturi și separă ce e nou de ce exista deja (pentru diferențe la reîmprospătare).
     *
     * Ca reîmprospătarea să fie rapidă, o intrare primită identic ca data trecută doar se marchează „văzută”
     * (fără re-parsare și fără rescrierea rândului), iar potrivirea TMDB rămâne neatinsă. Numele se parsează
     * doar pentru intrările noi sau schimbate.
     */
    private inner class BatchWriter(
        private val sourceId: Long,
        val run: Long,
    ) {
        private val buffer = LinkedHashMap<String, RawEntryEntity>(BATCH_SIZE * 2)
        private val counts = IntArray(VodKind.entries.size)
        private var hadEntries: Boolean? = null
        var scanned = 0
        var total = 0
            private set
        var added = 0
            private set
        val pending: Int get() = buffer.size

        fun count(kind: VodKind) = counts[kind.ordinal]

        suspend fun add(entry: RawEntryEntity) {
            buffer[entry.providerKey] = entry
            if (buffer.size >= BATCH_SIZE) flush()
        }

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val batch = buffer.values.toList()
            buffer.clear()
            // La primul import nu există nimic de comparat: sărim peste căutări.
            val compare = total > 0 || (hadEntries ?: dao.hasEntries(sourceId).also { hadEntries = it })
            db.withTransaction {
                val existing =
                    if (compare) {
                        batch
                            .chunked(SQL_CHUNK)
                            .flatMap { chunk -> dao.existing(sourceId, chunk.map { it.providerKey }) }
                            .associateBy { it.providerKey }
                    } else {
                        emptyMap()
                    }
                val inserts = ArrayList<RawEntryEntity>(batch.size)
                val updates = ArrayList<RawEntryEntity>()
                val unchanged = ArrayList<Long>()
                for (entry in batch) {
                    val old = existing[entry.providerKey]
                    when {
                        old == null -> {
                            inserts += entry.parsed()
                        }

                        old.title != null && old.sameFromProvider(entry) -> {
                            unchanged += old.id
                        }

                        else -> {
                            val parsed = entry.parsed()
                            // Păstrează potrivirea confirmată dacă importul precedă reparsarea după un update.
                            val previous = if (old.matchState == MatchState.MATCHED) old.parsed() else old
                            val sameTitle =
                                parsed.title == previous.title && parsed.year == previous.year &&
                                    parsed.tmdbId == old.tmdbId
                            val sameStream =
                                sameTitle && old.url == parsed.url && old.streamId == parsed.streamId &&
                                    old.containerExt == parsed.containerExt && old.season == parsed.season &&
                                    old.episode == parsed.episode
                            val confirmed = old.confirmedQuality.takeIf { sameStream }
                            val confirmedLanguages = old.confirmedLanguages.takeIf { sameStream }
                            updates +=
                                parsed.copy(
                                    quality = confirmed ?: parsed.quality,
                                    confirmedQuality = confirmed,
                                    languages = confirmedLanguages ?: parsed.languages,
                                    confirmedLanguages = confirmedLanguages,
                                    id = old.id,
                                    firstSeenAt = old.firstSeenAt,
                                    visible = old.visible,
                                    titleId = if (sameTitle) old.titleId else null,
                                    matchState = if (sameTitle) old.matchState else entry.matchState,
                                )
                        }
                    }
                }
                if (inserts.isNotEmpty()) dao.insertEntries(inserts)
                if (updates.isNotEmpty()) dao.updateEntries(updates)
                unchanged.chunked(SQL_CHUNK).forEach { dao.markSeen(it, run) }
                added += inserts.size
            }
            batch.forEach { counts[it.kind.ordinal]++ }
            total += batch.size
        }
    }

    private fun userMessage(operation: String, e: Exception): String =
        when (e) {
            is ImportException, is XtreamException -> e.message.orEmpty()
            is IOException -> "$operation: eroare I/O (${e.javaClass.simpleName})"
            is IllegalArgumentException -> "$operation: date de intrare invalide"
            is SecurityException -> "$operation: acces refuzat"
            else -> "$operation: import eșuat (${e.javaClass.simpleName})"
        }

    private companion object {
        const val BATCH_SIZE = 2_000

        /** Android 6 (SQLite vechi) acceptă cel mult 999 de parametri într-o interogare. */
        const val SQL_CHUNK = 500
        const val PROGRESS_EVERY = 2_000
    }
}
