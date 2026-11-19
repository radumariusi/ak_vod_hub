package io.github.radumariusi.akvodhub.data.catalog

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.MatchState
import io.github.radumariusi.akvodhub.data.db.PendingEntry
import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.catalog.CatalogDirty
import io.github.radumariusi.akvodhub.parser.catalog.ContentVisibility
import io.github.radumariusi.akvodhub.parser.catalog.DubLabel
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.match.MatchQuery
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.match.TitleSimilarity
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import io.github.radumariusi.akvodhub.parser.player.SeriesPlayback
import io.github.radumariusi.akvodhub.parser.title.CategoryMode
import io.github.radumariusi.akvodhub.parser.title.CategoryRules
import io.github.radumariusi.akvodhub.parser.title.ParsedTitle
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
 * După import (amprenta `CatalogDao.structureFingerprint` schimbată) se reface tot catalogul. După
 * potriviri TMDB sau calitate confirmată la redare se refac doar titlurile notate în `catalog_dirty`
 * (vezi `CatalogDirty`), într-o tranzacție mică.
 */
class CatalogIndexer(
    context: Context,
    private val db: AkVodDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.catalogDao()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
                val hideAdult = contentPrefs.getBoolean("hide_adult_movies", false)
                val visibility = "$hiddenLanguages|$hiddenCategories|$hideAdult"
                var full =
                    force || visibility != prefs.getString("visibility", "") ||
                        dao.structureFingerprint() != prefs.getString(FINGERPRINT, null) ||
                        !searchIndexReady()
                if (!full && dao.dirtyCount() == 0) {
                    // Nimic de reindexat: doar insignele „Nou” expirate și posterele/descrierile actualizate.
                    var cardsChanged = false
                    if (prefs.getLong("badge_day", -1L) != day) {
                        cardsChanged = dao.expireNewBadges(now - PosterBadges.NEW_WINDOW_MS, now) > 0
                        prefs.edit().putLong("badge_day", day).apply()
                    }
                    val stamp = dao.titlesStamp()
                    if (stamp != prefs.getLong(TITLES_STAMP, -1L)) {
                        prefs.edit().putLong(TITLES_STAMP, stamp).apply()
                        cardsChanged = true
                    }
                    if (cardsChanged) notifyCardsChanged()
                    return@withContext
                }
                val start = clock()
                linkLocalTitles()
                db.playlistDao().inheritSeriesTitles()
                var dirty = 0
                db.withTransaction {
                    // Snapshot în tranzacție: ce se notează după commit intră la următoarea reindexare.
                    if (!full) {
                        dirty = dao.dirtyCount()
                        full = dirty > PARTIAL_LIMIT
                    }
                    val scoped = !full
                    updateSourceVisibility(hiddenLanguages, hiddenCategories, hideAdult, scoped)
                    if (scoped) dao.updateDirtySummaries() else dao.updateSummaries()
                    rebuildSourceOptions(scoped)
                    updateDubLabels(scoped)
                    updateEpisodeNews(scoped)
                    dao.deleteEmptyLocalTitles()
                    rebuildSearchIndex(scoped)
                    if (scoped && prefs.getLong("badge_day", -1L) != day) {
                        dao.expireNewBadges(now - PosterBadges.NEW_WINDOW_MS, now)
                    }
                    dao.clearDirty()
                }
                prefs
                    .edit()
                    .putString(FINGERPRINT, dao.structureFingerprint())
                    .putString("visibility", visibility)
                    .putLong("badge_day", day)
                    .putLong(TITLES_STAMP, dao.titlesStamp())
                    .apply()
                mutableVersion.value = clock()
                val scope = if (full) "complet" else "incremental, $dirty titluri"
                Log.i(TAG, "Catalog reindexat ($scope) în ${clock() - start} ms")
            }
        }

    /** Regulile pe categorii din Setări → Conținut; reparsăm toate intrările salvate. */
    fun setCategoryRules(rules: Map<String, CategoryMode>) {
        CategoryRules.rules = rules.filterValues { it != CategoryMode.AUTO }
        prefs
            .edit()
            .putString(CATEGORY_RULES, CategoryRules.encode(rules))
            .putLong(SOURCE_METADATA_CURSOR, 0L)
            .apply()
    }

    /** Reparsează intrările salvate după actualizarea regulilor, în loturi reluabile. */
    suspend fun refreshSourceMetadata(): Boolean =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val key = SOURCE_METADATA_CURSOR
                var after = prefs.getLong(key, 0L)
                if (after < 0L) return@withContext true
                val playlist = db.playlistDao()
                val deadline = SystemClock.elapsedRealtime() + 60_000L
                while (!PlaybackActivityState.active && SystemClock.elapsedRealtime() < deadline) {
                    val lastId =
                        db.withTransaction {
                            val batch = playlist.sourceMetadataBatch(after, 500)
                            val regroup = mutableListOf<Triple<LocalKey, String, Long>>()
                            val updates =
                                batch.mapNotNull { entry ->
                                    // Sezonul și episodul fac parte din identitatea de redare deja salvată.
                                    val parsed = entry.parsed().copy(season = entry.season, episode = entry.episode)
                                    val nameChanged = entry.title != parsed.title || entry.year != parsed.year
                                    val local =
                                        entry.matchState != MatchState.MATCHED &&
                                            entry.matchState != MatchState.INHERITED
                                    val title = parsed.title.orEmpty()
                                    val type = if (entry.kind == VodKind.MOVIE) MediaType.MOVIE else MediaType.TV
                                    val shouldRetry =
                                        entry.matchState == MatchState.UNMATCHED &&
                                            (
                                                nameChanged || entry.languages != parsed.languages ||
                                                    MatchQuery(
                                                        type,
                                                        title,
                                                        parsed.year,
                                                        ParsedTitle.nameLanguage(
                                                            parsed.languages?.split(',').orEmpty(),
                                                            parsed.subtitles?.split(',').orEmpty(),
                                                        ),
                                                    ).alternatives.isNotEmpty()
                                            )
                                    if (local && nameChanged && entry.titleId != null) {
                                        localKey(entry.kind, title, parsed.year)?.let { keyForTitle ->
                                            regroup += Triple(keyForTitle, title, entry.id)
                                        }
                                    }
                                    val updated =
                                        if (shouldRetry) {
                                            parsed.copy(
                                                matchState = MatchState.PENDING_SEARCH,
                                            )
                                        } else {
                                            parsed
                                        }
                                    updated.takeUnless { it == entry }
                                }
                            if (updates.isNotEmpty()) playlist.updateEntries(updates)
                            for ((localKey, entries) in regroup.groupBy { it.first }) {
                                val ids = entries.map { it.third }
                                val target = ensureLocalTitle(localKey, entries.first().second, ids, clock())
                                carryLibrary(ids, target, localKey.type)
                                dao.linkLocal(ids, target)
                            }
                            batch.lastOrNull()?.id ?: -1L
                        }
                    // Cursorul poate rămâne în urmă la întrerupere. Repetarea lotului este idempotentă.
                    prefs
                        .edit()
                        .putLong(key, lastId)
                        .remove(FINGERPRINT)
                        .apply()
                    if (lastId < 0L) return@withContext true
                    after = lastId
                }
                false
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
                    val ids = entries.map { it.id }
                    val titleId = ensureLocalTitle(key, displayTitle(entries.first()), ids, now)
                    dao.linkLocal(ids, titleId)
                }
            }
        }
    }

    private suspend fun ensureLocalTitle(key: LocalKey, name: String, ids: List<Long>, now: Long): Long {
        val tmdbId = localTmdbId(key)
        return dao.title(key.type, tmdbId)?.id
            ?: dao
                .insertTitle(
                    TitleEntity(
                        mediaType = key.type,
                        tmdbId = tmdbId,
                        name = name,
                        year = key.year,
                        posterPath = dao.firstLogo(ids),
                        detailsFetched = true,
                        updatedAt = now,
                    ),
                ).takeIf { it > 0 }
            ?: dao.title(key.type, tmdbId)!!.id
    }

    /** Se apelează în aceeași tranzacție cu legarea surselor. Preferințele deja alese pe destinație au prioritate. */
    internal suspend fun carryLibrary(ids: List<Long>, titleId: Long, type: MediaType) {
        dao.carryWatchlist(ids, titleId)
        dao.carryHistory(ids, titleId)
        dao.carryEpisodeProgress(ids, titleId)
        dao.carryWatchState(ids, titleId)
        dao.updateCarriedProgress(titleId)
        if (type != MediaType.TV) return
        val settings = contentPrefs.all
        if (settings.keys.none { it.startsWith("series.local_") }) return
        val target = dao.titleById(titleId) ?: return
        val targetKey = SeriesPlayback.key(target.tmdbId, target.originalName ?: target.name, target.year)
        val missing = listOf("intro", "audio", "text").filterTo(mutableSetOf()) { "series.$targetKey.$it" !in settings }
        if (missing.isEmpty()) return
        val edit = contentPrefs.edit()
        for (old in dao.localTitlesForEntries(ids)) {
            if (old.id == titleId) continue
            val oldKey = SeriesPlayback.key(old.tmdbId, old.originalName ?: old.name, old.year)
            val keys = missing.iterator()
            while (keys.hasNext()) {
                val typeKey = keys.next()
                val value = settings["series.$oldKey.$typeKey"] as? String ?: continue
                val newKey = "series.$targetKey.$typeKey"
                if (!SeriesPlayback.validSetting(newKey, value)) continue
                edit.putString(newKey, value)
                keys.remove()
            }
            if (missing.isEmpty()) break
        }
        edit.apply()
    }

    /** [scoped] = doar sursele titlurilor din `catalog_dirty` (o potrivire poate muta o sursă ascunsă la alt titlu). */
    private fun updateSourceVisibility(languages: String, categories: String, hideAdult: Boolean, scoped: Boolean) {
        val sql = db.openHelper.writableDatabase
        val scope = if (scoped) " AND raw_entry.titleId IN ${CatalogDirty.IDS}" else ""
        sql.execSQL("UPDATE raw_entry SET visible = 1 WHERE visible = 0$scope")
        val hidden = ContentVisibility.hiddenSources(languages, categories)
        sql.execSQL(hidden.sql + scope, hidden.args.toTypedArray())
        if (hideAdult) {
            val adults = ContentVisibility.adultMovieSources()
            sql.execSQL(adults.sql + scope, adults.args.toTypedArray())
        }
        sql.execSQL(
            "UPDATE raw_entry SET visible = 0 WHERE kind = 'EPISODE' AND seriesId IS NOT NULL AND " +
                "EXISTS(SELECT 1 FROM raw_entry s WHERE s.sourceId = raw_entry.sourceId AND " +
                "s.providerKey = 'series:' || raw_entry.seriesId AND s.visible = 0)$scope",
        )
    }

    private fun rebuildSourceOptions(scoped: Boolean) {
        val sql = db.openHelper.writableDatabase
        if (scoped) {
            sql.execSQL("DELETE FROM title_source_option WHERE titleId IN ${CatalogDirty.IDS}")
            sql.execSQL(ContentVisibility.DIRTY_SOURCE_OPTIONS)
        } else {
            sql.execSQL("DELETE FROM title_source_option")
            sql.execSQL(ContentVisibility.SOURCE_OPTIONS)
        }
    }

    /** Un singur parcurs după cheia titlului; nici liste complete, nici interogări pe poster. */
    private fun updateDubLabels(scoped: Boolean) {
        val scope = if (scoped) " AND t.id IN ${CatalogDirty.IDS}" else ""
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
                        "JOIN raw_entry r ON r.titleId = t.id WHERE r.visible = 1$scope ORDER BY t.id",
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
            "UPDATE title SET dubLabel = NULL, hasOriginalAudio = 0, hdrBadge = NULL, isNew = 0 WHERE sourceCount = 0" +
                if (scoped) " AND id IN ${CatalogDirty.IDS}" else "",
        )
    }

    /** MIN pe episod evită ca o nouă variantă de calitate/limbă să pară un episod nou. */
    private fun updateEpisodeNews(scoped: Boolean) {
        val sql = db.openHelper.writableDatabase
        val scope = if (scoped) " AND titleId IN ${CatalogDirty.IDS}" else ""
        sql.execSQL(
            "UPDATE title SET newEpisodeAt = NULL WHERE newEpisodeAt IS NOT NULL" +
                if (scoped) " AND id IN ${CatalogDirty.IDS}" else "",
        )
        sql.compileStatement("UPDATE title SET newEpisodeAt = ? WHERE id = ?").use { update ->
            sql
                .query(
                    "SELECT titleId, season, episode, MIN(firstSeenAt) FROM raw_entry " +
                        "WHERE kind = 'EPISODE' AND visible = 1 AND titleId IS NOT NULL " +
                        "AND season IS NOT NULL AND episode IS NOT NULL$scope " +
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

    private fun rebuildSearchIndex(scoped: Boolean) {
        val sql = db.openHelper.writableDatabase
        if (scoped) {
            sql.execSQL("DELETE FROM title_search WHERE docid IN ${CatalogDirty.IDS}")
        } else {
            sql.execSQL("DELETE FROM title_search")
        }
        sql.execSQL(
            "INSERT INTO title_search(docid, name, alt) SELECT t.id, t.name, " +
                "IFNULL(t.originalName, '') || ' ' || IFNULL((SELECT group_concat(DISTINCT r.title) " +
                "FROM raw_entry r WHERE r.titleId = t.id AND r.visible = 1), '') FROM title t WHERE t.sourceCount > 0" +
                if (scoped) " AND t.id IN ${CatalogDirty.IDS}" else "",
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

    private fun localKey(entry: PendingEntry): LocalKey? = localKey(entry.kind, displayTitle(entry), entry.year)

    private fun localKey(kind: VodKind, name: String, year: Int?): LocalKey? {
        val title = TitleSimilarity.normalize(name)
        if (title.isEmpty()) return null
        val type = if (kind == VodKind.MOVIE) MediaType.MOVIE else MediaType.TV
        // La seriale, anul din numele episoadelor e adesea anul episodului: grupăm doar după nume.
        return LocalKey(type, title, if (type == MediaType.MOVIE) year else null)
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
        private const val PREFS = "catalog_index"
        private const val CATEGORY_RULES = "category_rules"
        private const val SOURCE_METADATA_CURSOR = "source_metadata_v3_after_id"

        /** La pornirea aplicației, înainte de orice import sau reparsare. */
        fun loadCategoryRules(context: Context) {
            CategoryRules.rules =
                CategoryRules.decode(
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CATEGORY_RULES, null),
                )
        }

        private const val FINGERPRINT = "fingerprint_v19"
        private const val TITLES_STAMP = "titles_stamp"

        /** Peste atât, o reindexare completă (fără `IN`) e mai rapidă decât una incrementală. */
        private const val PARTIAL_LIMIT = 20_000
        private const val BATCH = 2_000
    }
}
