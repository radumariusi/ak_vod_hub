package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.PlaylistSourceEntity
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamAccount
import io.github.radumariusi.akvodhub.data.playlist.xtream.XtreamClient
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import io.github.radumariusi.akvodhub.parser.VodKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Ce vede utilizatorul despre un playlist în Setări. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val label: String,
    val movies: Int,
    val series: Int,
    val episodes: Int,
    val lastImportAt: Long?,
    val lastImportError: String?,
    val lastAdded: Int,
    val lastRemoved: Int,
    val lastDurationMs: Long,
    /** Import în curs: null = nu; altfel progresul (sau progres null dacă doar așteaptă). */
    val activity: ImportActivity?,
)

/** Un playlist în fișierul de backup (link și cont în clar: fișierul trebuie păstrat ca o parolă). */
@Serializable
data class PlaylistBackup(
    val name: String,
    val type: String,
    val location: String,
    val username: String? = null,
    val password: String? = null,
)

data class LibraryTotals(
    val playlists: Int,
    val movies: Int,
    val series: Int,
    val episodes: Int,
)

class PlaylistRepository(
    private val context: Context,
    private val db: AkVodDatabase,
    private val cipher: SecretCipher,
    private val xtream: XtreamClient,
    private val importer: PlaylistImporter,
    private val scheduler: PlaylistScheduler,
    private val indexer: CatalogIndexer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.playlistDao()

    val playlists: Flow<List<PlaylistSummary>> =
        combine(dao.observeSources(), dao.observeCounts(), scheduler.activity()) { sources, counts, activity ->
            sources.map { s ->
                fun count(kind: VodKind) = counts.firstOrNull { it.sourceId == s.id && it.kind == kind }?.count ?: 0
                PlaylistSummary(
                    id = s.id,
                    name = s.name,
                    type = s.type,
                    label = s.label,
                    movies = count(VodKind.MOVIE),
                    series = count(VodKind.SERIES),
                    episodes = count(VodKind.EPISODE),
                    lastImportAt = s.lastImportAt,
                    lastImportError = s.lastImportError,
                    lastAdded = s.lastAdded,
                    lastRemoved = s.lastRemoved,
                    lastDurationMs = s.lastDurationMs,
                    activity =
                        activity
                            .filter { (it.progress?.sourceId ?: it.sourceId) == s.id }
                            .maxByOrNull { if (it.progress != null) 1 else 0 },
                )
            }
        }

    val totals: Flow<LibraryTotals> =
        combine(dao.observeSources(), dao.observeCounts()) { sources, counts ->
            LibraryTotals(
                playlists = sources.size,
                movies = counts.filter { it.kind == VodKind.MOVIE }.sumOf { it.count },
                series = counts.filter { it.kind == VodKind.SERIES }.sumOf { it.count },
                episodes = counts.filter { it.kind == VodKind.EPISODE }.sumOf { it.count },
            )
        }

    /** Adaugă un link M3U și pornește importul. */
    suspend fun addM3uUrl(
        name: String,
        url: String,
    ): Long {
        val clean = url.trim()
        val parsed =
            clean.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Linkul trebuie să înceapă cu http:// sau https://")
        return add(name, PlaylistType.M3U_URL, parsed.host, clean)
    }

    /** Adaugă un fișier M3U ales cu selectorul de fișiere ([uri] cu permisiune persistentă). */
    suspend fun addM3uFile(
        name: String,
        uri: Uri,
    ): Long {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val fileName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Fișier local"
        return add(
            name.ifBlank { fileName },
            PlaylistType.M3U_FILE,
            "Fișier local",
            uri.toString(),
            needsNetwork = false,
        )
    }

    /** Verifică contul Xtream (aruncă excepție cu mesaj dacă nu e bun), apoi îl adaugă și pornește importul. */
    suspend fun addXtream(
        name: String,
        server: String,
        username: String,
        password: String,
    ): Long {
        val account = XtreamAccount(XtreamAccount.normalizeServer(server), username.trim(), password.trim())
        val host =
            account.server.toHttpUrlOrNull()?.host ?: throw IllegalArgumentException("Adresa serverului nu e validă.")
        xtream.authenticate(account)
        return add(name, PlaylistType.XTREAM, host, account.server, account.username, account.password)
    }

    private suspend fun add(
        name: String,
        type: PlaylistType,
        label: String,
        location: String,
        username: String? = null,
        password: String? = null,
        needsNetwork: Boolean = true,
    ): Long {
        val id =
            dao.insertSource(
                PlaylistSourceEntity(
                    name = name.trim().ifBlank { label },
                    type = type,
                    label = label,
                    location = cipher.encrypt(location),
                    username = username?.let(cipher::encrypt),
                    password = password?.let(cipher::encrypt),
                    createdAt = clock(),
                ),
            )
        scheduler.importNow(id, needsNetwork)
        scheduler.schedulePeriodic()
        return id
    }

    suspend fun refresh(id: Long) {
        val source = dao.source(id) ?: return
        scheduler.importNow(id, needsNetwork = source.type != PlaylistType.M3U_FILE)
    }

    suspend fun delete(id: Long) {
        val source = dao.source(id) ?: return
        scheduler.cancel(id)
        db.withTransaction {
            dao.deleteEntries(id)
            dao.deleteSource(id)
        }
        if (source.type == PlaylistType.M3U_FILE) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(cipher.decrypt(source.location)),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        if (dao.sources().isEmpty()) scheduler.cancelPeriodic()
        // Titlurile playlist-ului șters dispar din hub.
        indexer.refresh()
    }

    /** Playlist-urile pentru backup, cu datele decriptate (fișierele locale nu se pot muta pe alt TV). */
    suspend fun backup(): List<PlaylistBackup> =
        dao.sources().filter { it.type != PlaylistType.M3U_FILE }.map { s ->
            PlaylistBackup(
                name = s.name,
                type = s.type.name,
                location = cipher.decrypt(s.location),
                username = s.username?.let(cipher::decrypt),
                password = s.password?.let(cipher::decrypt),
            )
        }

    /**
     * Adaugă un playlist din backup, dacă nu există deja (același link și utilizator). Contul Xtream nu se
     * verifică acum: dacă serverul nu răspunde, importul spune de ce, iar playlist-ul rămâne de reîmprospătat.
     */
    suspend fun restore(backup: PlaylistBackup): Boolean {
        val type =
            PlaylistType.entries.firstOrNull { it.name == backup.type && it != PlaylistType.M3U_FILE }
                ?: return false
        val existing =
            dao.sources().any { s ->
                s.type == type &&
                    runCatching { cipher.decrypt(s.location) }.getOrNull() == backup.location &&
                    s.username?.let { runCatching { cipher.decrypt(it) }.getOrNull() } == backup.username
            }
        if (existing) return false
        val host = backup.location.toHttpUrlOrNull()?.host ?: return false
        add(backup.name, type, host, backup.location, backup.username, backup.password)
        return true
    }

    /** Episoadele unui serial (la Xtream se cer de la server la prima deschidere). */
    suspend fun episodes(
        sourceId: Long,
        seriesId: Long,
    ): List<RawEntryEntity> = importer.loadXtreamEpisodes(sourceId, seriesId)
}
