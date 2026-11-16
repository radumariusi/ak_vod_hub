package io.github.radumariusi.akvodhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.radumariusi.akvodhub.parser.VodKind
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist_source ORDER BY id")
    fun observeSources(): Flow<List<PlaylistSourceEntity>>

    @Query("SELECT * FROM playlist_source ORDER BY id")
    suspend fun sources(): List<PlaylistSourceEntity>

    @Query("SELECT * FROM playlist_source WHERE id = :id")
    suspend fun source(id: Long): PlaylistSourceEntity?

    @Insert
    suspend fun insertSource(source: PlaylistSourceEntity): Long

    @Update
    suspend fun updateSource(source: PlaylistSourceEntity)

    @Query("DELETE FROM playlist_source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT sourceId, kind, COUNT(*) AS count FROM raw_entry GROUP BY sourceId, kind")
    fun observeCounts(): Flow<List<KindCount>>

    @Query("SELECT * FROM raw_entry WHERE sourceId = :sourceId AND providerKey IN (:keys)")
    suspend fun existing(
        sourceId: Long,
        keys: List<String>,
    ): List<RawEntryEntity>

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

    /** Episoadele Xtream iau titlul TMDB al serialului lor. */
    @Query(
        "UPDATE raw_entry SET titleId = (SELECT s.titleId FROM raw_entry s WHERE s.sourceId = raw_entry.sourceId " +
            "AND s.providerKey = 'series:' || raw_entry.seriesId) " +
            "WHERE kind = 'EPISODE' AND matchState = 3 AND seriesId IS NOT NULL",
    )
    suspend fun inheritSeriesTitles()

    @Query("SELECT * FROM raw_entry WHERE sourceId = :sourceId AND kind = 'EPISODE' AND seriesId = :seriesId")
    suspend fun episodes(
        sourceId: Long,
        seriesId: Long,
    ): List<RawEntryEntity>
}
