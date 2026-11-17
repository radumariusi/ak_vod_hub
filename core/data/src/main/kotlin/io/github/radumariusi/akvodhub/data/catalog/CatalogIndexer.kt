package io.github.radumariusi.akvodhub.data.catalog

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.PendingEntry
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.catalog.DubLabel
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.TitleSimilarity
import io.github.radumariusi.akvodhub.parser.title.TitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pregătește catalogul pentru hub, după import și după fiecare rundă de potrivire TMDB:
 *
 * 1. **Titluri locale:** intrările încă nepotrivite (sau negăsite pe TMDB) se grupează după nume + an și
 *    primesc un titlu „local” (ID TMDB negativ), ca orice film din playlist să poată fi găsit și deschis.
 * 2. **Rezumate:** pentru fiecare titlu, numărul surselor, data adăugării, calitățile și limbile.
 * 3. **Căutare:** reface indexul FTS `title_search`.
 *
 * Totul rulează doar dacă s-a schimbat ceva (vezi `CatalogDao.fingerprint`).
 */
class CatalogIndexer(
    context: Context,
    private val db: AkVodDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.catalogDao()
    private val prefs = context.getSharedPreferences("catalog_index", Context.MODE_PRIVATE)
    private val contentPrefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableVersion = MutableStateFlow(0L)

    /** Crește după fiecare reindexare: ecranele își reîncarcă listele. */
    val version: StateFlow<Long> = mutableVersion.asStateFlow()

    /** Notele schimbă doar cardurile: nu refacem rezumatele surselor sau FTS. */
    fun notifyCardsChanged() = mutableVersion.update { maxOf(clock(), it + 1) }

    suspend fun refresh(force: Boolean = false) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = clock()
                val day = now / 86_400_000L
                val hiddenLanguages = contentPrefs.getString("hidden_languages", "").orEmpty()
                val hiddenCategories = contentPrefs.getString("hidden_categories", "").orEmpty()
                val visibility = "$hiddenLanguages|$hiddenCategories"
                if (!force && visibility == prefs.getString("visibility", "") &&
                    dao.fingerprint() == prefs.getString(FINGERPRINT, null) &&
                    searchIndexReady()
                ) {
                    if (prefs.getLong("badge_day", -1L) != day) {
                        val changed = dao.expireNewBadges(now - PosterBadges.NEW_WINDOW_MS, now)
                        prefs.edit().putLong("badge_day", day).apply()
                        if (changed > 0) notifyCardsChanged()
                    }
                    return@withContext
                }
                val start = clock()
                linkLocalTitles()
                db.playlistDao().inheritSeriesTitles()
                db.withTransaction {
                    dao.updateSummaries()
                    updateDubLabels()
                    updateEpisodeNews()
                    dao.deleteEmptyLocalTitles()
                    val hidden =
                        io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility.hiddenTitles(
                            hiddenLanguages,
                            hiddenCategories,
                        )
                    db.openHelper.writableDatabase.execSQL(hidden.sql, hidden.args.toTypedArray())
                    rebuildSearchIndex()
                }
                prefs
                    .edit()
                    .putString(FINGERPRINT, dao.fingerprint())
                    .putString("visibility", visibility)
                    .putLong("badge_day", day)
                    .apply()
                mutableVersion.value = clock()
                Log.i(TAG, "Catalog reindexat în ${clock() - start} ms")
            }
        }

    private suspend fun linkLocalTitles() {
        var after = 0L
        while (true) {
            val batch = dao.unlinked(after, BATCH)
            if (batch.isEmpty()) return
            after = batch.last().id
            val now = clock()
            db.withTransaction {
                for ((key, entries) in batch.groupBy(::localKey)) {
                    if (key == null) continue
                    val first = entries.first()
                    val type = key.type
                    val tmdbId = localTmdbId(key)
                    val ids = entries.map { it.id }
                    val titleId =
                        dao.title(type, tmdbId)?.id
                            ?: dao
                                .insertTitle(
                                    TitleEntity(
                                        mediaType = type,
                                        tmdbId = tmdbId,
                                        name = displayTitle(first),
                                        year = key.year,
                                        posterPath = dao.firstLogo(ids),
                                        detailsFetched = true,
                                        updatedAt = now,
                                    ),
                                ).takeIf { it > 0 }
                            ?: dao.title(type, tmdbId)!!.id
                    dao.linkLocal(ids, titleId)
                }
            }
        }
    }

    /** Un singur parcurs după cheia titlului; nici liste complete, nici interogări pe poster. */
    private fun updateDubLabels() {
        val sql = db.openHelper.writableDatabase
        val update =
            sql.compileStatement(
                "UPDATE title SET dubLabel = ?, hasOriginalAudio = ?, tmdbBadge = ?, imdbBadge = ?, " +
                    "hdrBadge = ?, isNew = ? WHERE id = ?",
            )
        update.use {
            sql
                .query(
                    "SELECT t.id, t.originalLanguage, r.languages, t.rating, t.imdbRating, t.addedAt, r.hdr " +
                        "FROM title t " +
                        "JOIN raw_entry r ON r.titleId = t.id ORDER BY t.id",
                ).use { cursor ->
                    var titleId = -1L
                    var label: DubLabel? = null
                    var tmdbBadge: String? = null
                    var imdbBadge: String? = null
                    var hdrRank = 0
                    var isNew = false

                    fun bindText(index: Int, value: String?) {
                        if (value == null) update.bindNull(index) else update.bindString(index, value)
                    }

                    fun save() {
                        if (titleId < 0) return
                        val value = label?.label()
                        if (value == null) update.bindNull(1) else update.bindString(1, value)
                        update.bindLong(2, if (label?.hasOriginalAudio == true) 1L else 0L)
                        bindText(3, tmdbBadge)
                        bindText(4, imdbBadge)
                        bindText(5, PosterBadges.hdrLabel(hdrRank))
                        update.bindLong(6, if (isNew) 1L else 0L)
                        update.bindLong(7, titleId)
                        update.executeUpdateDelete()
                    }
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        if (id != titleId) {
                            save()
                            titleId = id
                            label = DubLabel(cursor.getString(1))
                            tmdbBadge = PosterBadges.rating("★", if (cursor.isNull(3)) null else cursor.getDouble(3))
                            imdbBadge = PosterBadges.rating("IMDb", if (cursor.isNull(4)) null else cursor.getDouble(4))
                            isNew = PosterBadges.isNew(if (cursor.isNull(5)) null else cursor.getLong(5), clock())
                            hdrRank = 0
                        }
                        label?.addSource(cursor.getString(2))
                        hdrRank = maxOf(hdrRank, PosterBadges.hdrRank(cursor.getString(6)))
                    }
                    save()
                }
        }
        sql.execSQL(
            "UPDATE title SET dubLabel = NULL, hasOriginalAudio = 0, hdrBadge = NULL, isNew = 0 WHERE sourceCount = 0",
        )
    }

    /** MIN pe episod evită ca o nouă variantă de calitate/limbă să pară un episod nou. */
    private fun updateEpisodeNews() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE title SET newEpisodeAt = NULL WHERE newEpisodeAt IS NOT NULL")
        sql.compileStatement("UPDATE title SET newEpisodeAt = ? WHERE id = ?").use { update ->
            sql
                .query(
                    "SELECT titleId, season, episode, MIN(firstSeenAt) FROM raw_entry " +
                        "WHERE kind = 'EPISODE' AND titleId IS NOT NULL " +
                        "AND season IS NOT NULL AND episode IS NOT NULL " +
                        "GROUP BY titleId, season, episode ORDER BY titleId",
                ).use { cursor ->
                    var titleId = -1L
                    var latest = 0L

                    fun save() {
                        if (titleId <= 0) return
                        update.bindLong(1, latest)
                        update.bindLong(2, titleId)
                        update.executeUpdateDelete()
                    }
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        if (id != titleId) {
                            save()
                            titleId = id
                            latest = 0L
                        }
                        latest = maxOf(latest, cursor.getLong(3))
                    }
                    save()
                }
        }
    }

    private fun rebuildSearchIndex() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DELETE FROM title_search")
        sql.execSQL(
            "INSERT INTO title_search(docid, name, alt) SELECT t.id, t.name, " +
                "IFNULL(t.originalName, '') || ' ' || IFNULL((SELECT group_concat(DISTINCT r.title) " +
                "FROM raw_entry r WHERE r.titleId = t.id), '') FROM title t WHERE t.sourceCount > 0",
        )
    }

    private fun searchIndexReady(): Boolean =
        db.openHelper.readableDatabase.query("SELECT EXISTS(SELECT 1 FROM title_search)").use { c ->
            c.moveToFirst() && c.getInt(0) == 1
        }

    private data class LocalKey(
        val type: MediaType,
        val title: String,
        val year: Int?,
    )

    private fun displayTitle(entry: PendingEntry): String =
        entry.title?.ifBlank { null } ?: TitleParser.parse(entry.name, entry.groupTitle).title.ifBlank { entry.name }

    private fun localKey(entry: PendingEntry): LocalKey? {
        val title = TitleSimilarity.normalize(displayTitle(entry))
        if (title.isEmpty()) return null
        val type = if (entry.kind == VodKind.MOVIE) MediaType.MOVIE else MediaType.TV
        // La seriale, anul din numele episoadelor e adesea anul episodului: grupăm doar după nume.
        return LocalKey(type, title, if (type == MediaType.MOVIE) entry.year else null)
    }

    /** ID negativ stabil (FNV-1a pe 63 de biți), ca același titlu local să fie refolosit la reîmprospătare. */
    private fun localTmdbId(key: LocalKey): Long {
        var hash = -0x340d631b7bdddcdbL
        for (ch in "${key.type}|${key.title}|${key.year ?: ""}") {
            hash = (hash xor ch.code.toLong()) * 0x100000001b3L
        }
        return -((hash and Long.MAX_VALUE).coerceAtLeast(1L))
    }

    companion object {
        private const val TAG = "CatalogIndexer"
        private const val FINGERPRINT = "fingerprint_v14"
        private const val BATCH = 2_000
    }
}
