package io.github.radumariusi.akvodhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.flow.Flow

/** O intrare care așteaptă potrivirea TMDB (doar coloanele de care are nevoie potrivirea). */
data class PendingEntry(
    val id: Long,
    val kind: VodKind,
    val name: String,
    val groupTitle: String?,
    val title: String?,
    val year: Int?,
    val languages: String?,
    val tmdbId: Long?,
    val matchState: Int,
)

data class StateCount(
    val state: Int,
    val count: Int,
)

@Dao
interface CatalogDao {
    // ------------------------------------------------------------ potrivire

    /** Intrări cu ID TMDB de la furnizor: se leagă direct, fără căutare. */
    @Query(
        "SELECT id, kind, name, groupTitle, title, year, languages, tmdbId, matchState FROM raw_entry " +
            "WHERE matchState = 0 AND tmdbId IS NOT NULL LIMIT :limit",
    )
    suspend fun pendingWithProviderId(limit: Int): List<PendingEntry>

    /** Intrări de căutat pe TMDB după nume. */
    @Query(
        "SELECT id, kind, name, groupTitle, title, year, languages, tmdbId, matchState FROM raw_entry " +
            "WHERE (matchState = 0 AND tmdbId IS NULL) OR matchState = 4 LIMIT :limit",
    )
    suspend fun pendingSearch(limit: Int): List<PendingEntry>

    @Query("UPDATE raw_entry SET titleId = :titleId, matchState = 1 WHERE id IN (:ids)")
    suspend fun link(
        ids: List<Long>,
        titleId: Long,
    )

    @Query("UPDATE raw_entry SET titleId = NULL, matchState = :state WHERE id IN (:ids)")
    suspend fun setState(
        ids: List<Long>,
        state: Int,
    )

    /** ID-ul de la furnizor era greșit: intrările titlului se caută din nou după nume. */
    @Query("UPDATE raw_entry SET titleId = NULL, matchState = 4 WHERE titleId = :titleId")
    suspend fun unlinkTitle(titleId: Long)

    @Query("UPDATE raw_entry SET matchState = 0 WHERE matchState = 2")
    suspend fun resetUnmatched(): Int

    @Query("SELECT * FROM match_cache WHERE queryKey IN (:keys)")
    suspend fun cached(keys: List<String>): List<MatchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entries: List<MatchCacheEntity>)

    @Query("DELETE FROM match_cache WHERE tmdbId IS NULL")
    suspend fun clearNegativeCache()

    // ------------------------------------------------------------ titluri

    @Query("SELECT * FROM title WHERE mediaType = :type AND tmdbId = :tmdbId")
    suspend fun title(
        type: MediaType,
        tmdbId: Long,
    ): TitleEntity?

    @Query("SELECT * FROM title WHERE id = :id")
    suspend fun titleById(id: Long): TitleEntity?

    /** Întoarce -1 dacă titlul există deja. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitle(title: TitleEntity): Long

    @Update
    suspend fun updateTitle(title: TitleEntity)

    @Query("DELETE FROM title WHERE id = :id")
    suspend fun deleteTitle(id: Long)

    @Query("SELECT * FROM title WHERE detailsFetched = 0 LIMIT :limit")
    suspend fun titlesWithoutDetails(limit: Int): List<TitleEntity>

    // ------------------------------------------------------------ surse și statistici

    /** Toate sursele unui titlu, din toate playlist-urile (fără episoade). */
    @Query("SELECT * FROM raw_entry WHERE titleId = :titleId AND kind <> 'EPISODE'")
    suspend fun sources(titleId: Long): List<RawEntryEntity>

    @Query("SELECT * FROM raw_entry WHERE titleId = :titleId AND kind = 'EPISODE' ORDER BY season, episode")
    suspend fun episodes(titleId: Long): List<RawEntryEntity>

    @Query("SELECT matchState AS state, COUNT(*) AS count FROM raw_entry WHERE matchState <> 3 GROUP BY matchState")
    fun observeStateCounts(): Flow<List<StateCount>>

    /** Câte filme/seriale unice (după unirea duplicatelor) au surse în playlist-uri. */
    @Query("SELECT COUNT(DISTINCT titleId) FROM raw_entry WHERE titleId IS NOT NULL")
    fun observeTitleCount(): Flow<Int>
}
