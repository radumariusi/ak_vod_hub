package io.github.radumariusi.akvodhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist_source ORDER BY id")
    fun observeSources(): Flow<List<PlaylistSourceEntity>>

    @Query("SELECT * FROM playlist_source ORDER BY id")
    suspend fun sources(): List<PlaylistSourceEntity>

    // ---------------------------------------------------------------- diagnostic (Setări, export)

    @Query(
        "SELECT kind, COUNT(*) AS total, SUM(CASE WHEN languages IS NULL THEN 1 ELSE 0 END) AS noLanguage, " +
            "SUM(CASE WHEN quality IS NULL THEN 1 ELSE 0 END) AS noQuality FROM raw_entry GROUP BY kind",
    )
    suspend fun diagnosticCounts(): List<DiagnosticCount>

    @Query(
        "SELECT groupTitle, kind, COUNT(*) AS total, " +
            "SUM(CASE WHEN languages IS NULL THEN 1 ELSE 0 END) AS noLanguage FROM raw_entry " +
            "WHERE groupTitle IS NOT NULL GROUP BY groupTitle, kind ORDER BY total DESC LIMIT :limit",
    )
    suspend fun diagnosticCategories(limit: Int): List<DiagnosticCategory>

    @Query(
        "SELECT r.name, r.groupTitle, r.kind, r.languages, r.quality, r.streamId, s.type AS playlistType, " +
            "r.sourceId FROM raw_entry r JOIN playlist_source s ON s.id = r.sourceId " +
            "WHERE r.kind = :kind ORDER BY random() LIMIT :limit",
    )
    suspend fun diagnosticNames(kind: VodKind, limit: Int): List<DiagnosticName>

    @Query("SELECT * FROM playlist_source WHERE id = :id")
    suspend fun source(id: Long): PlaylistSourceEntity?

    @Insert
    suspend fun insertSource(source: PlaylistSourceEntity): Long

    @Update
    suspend fun updateSource(source: PlaylistSourceEntity)

    @Query("UPDATE playlist_source SET lastImportError = :message WHERE id = :id")
    suspend fun setImportError(id: Long, message: String)

    @Query("DELETE FROM playlist_source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT sourceId, kind, COUNT(*) AS count FROM raw_entry GROUP BY sourceId, kind")
    fun observeCounts(): Flow<List<KindCount>>

    @Query("SELECT * FROM raw_entry WHERE sourceId = :sourceId AND providerKey IN (:keys)")
    suspend fun existing(
        sourceId: Long,
        keys: List<String>,
    ): List<RawEntryEntity>

    @Query("SELECT * FROM raw_entry WHERE id > :after ORDER BY id LIMIT :limit")
    suspend fun sourceMetadataBatch(after: Long, limit: Int): List<RawEntryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM raw_entry WHERE sourceId = :sourceId)")
    suspend fun hasEntries(sourceId: Long): Boolean

    /** Intrările neschimbate de la furnizor: doar le marcăm ca văzute la importul [run]. */
    @Query("UPDATE raw_entry SET seenRun = :run WHERE id IN (:ids)")
    suspend fun markSeen(
        ids: List<Long>,
        run: Long,
    )

    /** REPLACE: același link poate apărea de două ori într-un M3U (în loturi diferite la primul import). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<RawEntryEntity>)

    @Update
    suspend fun updateEntries(entries: List<RawEntryEntity>)

    /** Șterge ce nu a mai apărut la importul [run] (doar tipurile importate atunci). */
    @Query("DELETE FROM raw_entry WHERE sourceId = :sourceId AND seenRun <> :run AND kind IN (:kinds)")
    suspend fun deleteStale(
        sourceId: Long,
        run: Long,
        kinds: List<VodKind>,
    ): Int

    /** Episoadele serialelor Xtream care nu mai există în playlist. */
    @Query(
        "DELETE FROM raw_entry WHERE sourceId = :sourceId AND kind = 'EPISODE' AND seriesId NOT IN " +
            "(SELECT streamId FROM raw_entry WHERE sourceId = :sourceId AND kind = 'SERIES')",
    )
    suspend fun deleteOrphanEpisodes(sourceId: Long): Int

    @Query("DELETE FROM raw_entry WHERE sourceId = :sourceId")
    suspend fun deleteEntries(sourceId: Long)

    /** Episoadele Xtream iau titlul TMDB al serialului lor (rescrie doar episoadele care chiar se schimbă). */
    @Query(
        "UPDATE raw_entry SET titleId = (SELECT s.titleId FROM raw_entry s WHERE s.sourceId = raw_entry.sourceId " +
            "AND s.providerKey = 'series:' || raw_entry.seriesId) " +
            "WHERE kind = 'EPISODE' AND matchState = 3 AND seriesId IS NOT NULL AND titleId IS NOT " +
            "(SELECT s.titleId FROM raw_entry s WHERE s.sourceId = raw_entry.sourceId " +
            "AND s.providerKey = 'series:' || raw_entry.seriesId)",
    )
    suspend fun inheritSeriesTitles()

    @Query(
        "UPDATE raw_entry SET titleId = (SELECT s.titleId FROM raw_entry s WHERE s.sourceId = :sourceId " +
            "AND s.providerKey = 'series:' || :seriesId) WHERE kind = 'EPISODE' AND matchState = 3 " +
            "AND sourceId = :sourceId AND seriesId = :seriesId",
    )
    suspend fun inheritSeriesTitle(sourceId: Long, seriesId: Long)

    @Query(
        "SELECT r.* FROM raw_entry r JOIN playlist_source p ON p.id = r.sourceId " +
            "WHERE p.type = 'XTREAM' AND r.kind = 'SERIES' AND r.streamId IS NOT NULL AND r.id > :afterId " +
            "AND (EXISTS(SELECT 1 FROM watchlist w WHERE w.titleId = r.titleId) " +
            "OR EXISTS(SELECT 1 FROM watch_history h WHERE h.titleId = r.titleId) " +
            "OR EXISTS(SELECT 1 FROM watch_state s WHERE s.titleId = r.titleId)) ORDER BY r.id LIMIT :limit",
    )
    suspend fun followedSeries(afterId: Long, limit: Int): List<RawEntryEntity>

    @Query(
        "SELECT r.* FROM title t JOIN raw_entry r ON r.id = (" +
            "SELECT x.id FROM raw_entry x JOIN playlist_source p ON p.id = x.sourceId " +
            "WHERE x.titleId = t.id AND x.visible = 1 AND x.kind = 'SERIES' " +
            "AND x.streamId IS NOT NULL AND p.type = 'XTREAM' " +
            "AND NOT EXISTS(SELECT 1 FROM raw_entry e " +
            "WHERE e.titleId = t.id AND e.kind = 'EPISODE' AND e.visible = 1) " +
            "ORDER BY x.id LIMIT 1) WHERE t.mediaType = 'TV' AND t.sourceCount > 0 " +
            "ORDER BY t.popularity DESC LIMIT :limit",
    )
    suspend fun seriesToWarm(limit: Int): List<RawEntryEntity>

    @Query(
        "SELECT * FROM raw_entry WHERE sourceId = :sourceId AND providerKey = 'series:' || :seriesId LIMIT 1",
    )
    suspend fun xtreamSeries(sourceId: Long, seriesId: Long): RawEntryEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM raw_entry WHERE sourceId = :sourceId AND kind = 'EPISODE' AND seriesId = :seriesId)",
    )
    suspend fun hasEpisodes(sourceId: Long, seriesId: Long): Boolean

    @Query("SELECT * FROM raw_entry WHERE sourceId = :sourceId AND kind = 'EPISODE' AND seriesId = :seriesId")
    suspend fun episodes(
        sourceId: Long,
        seriesId: Long,
    ): List<RawEntryEntity>
}

data class DiagnosticCount(
    val kind: VodKind,
    val total: Int,
    val noLanguage: Int,
    val noQuality: Int,
)

data class DiagnosticCategory(
    val groupTitle: String,
    val kind: VodKind,
    val total: Int,
    val noLanguage: Int,
)

data class DiagnosticName(
    val name: String,
    val groupTitle: String?,
    val kind: VodKind,
    val languages: String?,
    val quality: VideoQuality?,
    val streamId: Long?,
    val playlistType: PlaylistType,
    val sourceId: Long,
)
