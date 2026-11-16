package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.PlaylistSourceEntity
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamAccount
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamException
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.playlist.M3uParser
import io.github.radumariusi.akvodhub.parser.playlist.VodClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * scrie în loturi și la final șterge ce a dispărut de la furnizor (diferențe față de importul anterior).
 * Dacă importul eșuează la jumătate, nu se șterge nimic.
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

    suspend fun import(
        sourceId: Long,
        onProgress: suspend (ImportProgress) -> Unit,
    ): ImportResult =
        mutex.withLock {
            val source = dao.source(sourceId) ?: throw ImportException("Playlist-ul nu mai există.")
            val start = clock()
            try {
                val writer = BatchWriter(sourceId, run = start)
                val kinds =
                    withContext(Dispatchers.IO) {
                        when (source.type) {
                            PlaylistType.M3U_URL, PlaylistType.M3U_FILE -> importM3u(source, writer, onProgress)
                            PlaylistType.XTREAM -> importXtream(source, writer, onProgress)
                        }
                    }
                writer.flush()
                if (writer.total == 0) {
                    throw ImportException("Playlist-ul nu conține filme sau seriale (poate doar Live TV?).")
                }
                onProgress(ImportProgress(sourceId, "Se finalizează…", writer.scanned, writer.total))
                val removed =
                    db.withTransaction {
                        var count = dao.deleteStale(sourceId, start, kinds)
                        if (source.type == PlaylistType.XTREAM) count += dao.deleteOrphanEpisodes(sourceId)
                        count
                    }
                val result =
                    ImportResult(
                        movies = writer.count(VodKind.MOVIE),
                        series = writer.count(VodKind.SERIES),
                        episodes = writer.count(VodKind.EPISODE),
                        added = writer.added,
                        removed = removed,
                        durationMs = clock() - start,
                    )
                dao.source(sourceId)?.let {
                    dao.updateSource(
                        it.copy(
                            lastImportAt = clock(),
                            lastImportError = null,
                            lastAdded = result.added,
                            lastRemoved = result.removed,
                            lastDurationMs = result.durationMs,
                        ),
                    )
                }
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = userMessage(e)
                dao.source(sourceId)?.let { dao.updateSource(it.copy(lastImportError = message)) }
                throw ImportException(message)
            }
        }

    /** Episoadele unui serial Xtream, cerute la nevoie (un apel pe serial) și salvate ca intrări EPISODE. */
    suspend fun loadXtreamEpisodes(
        sourceId: Long,
        seriesId: Long,
    ): List<RawEntryEntity> {
        val source = dao.source(sourceId) ?: return emptyList()
        if (source.type != PlaylistType.XTREAM) return dao.episodes(sourceId, seriesId)
        val account = account(source)
        val now = clock()
        val writer = BatchWriter(sourceId, run = now)
        xtream.episodes(account, seriesId).forEach { e ->
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
                    firstSeenAt = now,
                    seenRun = now,
                ),
            )
        }
        writer.flush()
        return dao.episodes(sourceId, seriesId)
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

    private suspend fun importXtream(
        source: PlaylistSourceEntity,
        writer: BatchWriter,
        onProgress: suspend (ImportProgress) -> Unit,
    ): List<VodKind> {
        val account = account(source)
        val now = writer.run
        onProgress(ImportProgress(source.id, "Se conectează la server…", 0, 0))
        xtream.authenticate(account)

        val movieGroups = runCatching { xtream.vodCategories(account) }.getOrDefault(emptyMap())
        onProgress(ImportProgress(source.id, "Filme…", writer.scanned, writer.total))
        xtream.movies(account) { movies ->
            for (m in movies) {
                writer.scanned++
                writer.add(
                    RawEntryEntity(
                        sourceId = source.id,
                        providerKey = "movie:${m.streamId}",
                        kind = VodKind.MOVIE,
                        name = m.name,
                        groupTitle = m.categoryId?.let(movieGroups::get),
                        url = null,
                        logo = m.icon,
                        streamId = m.streamId,
                        containerExt = m.containerExtension,
                        tmdbId = m.tmdbId,
                        providerAddedAt = m.added,
                        firstSeenAt = now,
                        seenRun = now,
                    ),
                )
                if (writer.scanned % PROGRESS_EVERY == 0) {
                    onProgress(ImportProgress(source.id, "Filme…", writer.scanned, writer.total + writer.pending))
                }
            }
        }

        val seriesGroups = runCatching { xtream.seriesCategories(account) }.getOrDefault(emptyMap())
        onProgress(ImportProgress(source.id, "Seriale…", writer.scanned, writer.total + writer.pending))
        xtream.series(account) { series ->
            for (s in series) {
                writer.scanned++
                writer.add(
                    RawEntryEntity(
                        sourceId = source.id,
                        providerKey = "series:${s.seriesId}",
                        kind = VodKind.SERIES,
                        name = s.name,
                        groupTitle = s.categoryId?.let(seriesGroups::get),
                        url = null,
                        logo = s.cover,
                        streamId = s.seriesId,
                        tmdbId = s.tmdbId,
                        providerAddedAt = s.lastModified,
                        firstSeenAt = now,
                        seenRun = now,
                    ),
                )
                if (writer.scanned % PROGRESS_EVERY == 0) {
                    onProgress(ImportProgress(source.id, "Seriale…", writer.scanned, writer.total + writer.pending))
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

    /** Scrie intrările în loturi; separă ce e nou de ce exista deja (pentru diferențe la reîmprospătare). */
    private inner class BatchWriter(
        private val sourceId: Long,
        val run: Long,
    ) {
        private val buffer = LinkedHashMap<String, RawEntryEntity>(BATCH_SIZE * 2)
        private val counts = IntArray(VodKind.entries.size)
        var scanned = 0
        var total = 0
            private set
        var added = 0
            private set
        val pending: Int get() = buffer.size

        fun count(kind: VodKind) = counts[kind.ordinal]

        suspend fun add(entry: RawEntryEntity) {
            buffer[entry.providerKey] = entry.parsed()
            if (buffer.size >= BATCH_SIZE) flush()
        }

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val batch = buffer.values.toList()
            buffer.clear()
            db.withTransaction {
                val existing = dao.existing(sourceId, batch.map { it.providerKey }).associateBy { it.providerKey }
                val inserts = ArrayList<RawEntryEntity>(batch.size)
                val updates = ArrayList<RawEntryEntity>()
                for (entry in batch) {
                    val old = existing[entry.providerKey]
                    if (old ==
                        null
                    ) {
                        inserts += entry
                    } else {
                        updates += entry.copy(id = old.id, firstSeenAt = old.firstSeenAt)
                    }
                }
                if (inserts.isNotEmpty()) dao.insertEntries(inserts)
                if (updates.isNotEmpty()) dao.updateEntries(updates)
                added += inserts.size
            }
            batch.forEach { counts[it.kind.ordinal]++ }
            total += batch.size
        }
    }

    private fun userMessage(e: Exception): String =
        when (e) {
            is ImportException, is XtreamException -> e.message.orEmpty()
            is SerializationException -> "Serverul a trimis un răspuns pe care nu-l pot citi."
            is IOException -> "Eroare de rețea: ${e.message ?: e.javaClass.simpleName}"
            is IllegalArgumentException -> "Link invalid: ${e.message.orEmpty()}"
            is SecurityException -> "Nu mai am acces la fișier. Adaugă-l din nou."
            else -> "Import eșuat: ${e.message ?: e.javaClass.simpleName}"
        }

    private companion object {
        const val BATCH_SIZE = 500
        const val PROGRESS_EVERY = 2_000
    }
}
