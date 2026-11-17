package io.github.radumariusi.akvodhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.radumariusi.akvodhub.parser.match.MediaType

/** Un titlu disponibil (cu surse), cu tot ce trebuie pentru scorul recomandărilor și pentru card. */
data class DiscoverTitle(
    val id: Long,
    val mediaType: MediaType,
    val tmdbId: Long,
    val name: String,
    val originalName: String?,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double?,
    val voteCount: Int?,
    val popularity: Double?,
    val genreIds: String?,
    val bestQuality: Int?,
    val overview: String?,
    val imdbId: String?,
    val dubLabel: String?,
) {
    fun card() = TitleCard(id, mediaType, name, year, posterPath, backdropPath, rating, bestQuality, overview, dubLabel)
}

/** Un titlu din istoric, cu fișa lui. */
data class HistoryTitle(
    val titleId: Long,
    val progress: Double,
    val finished: Boolean,
    val watchedAt: Long,
    val mediaType: MediaType,
    val tmdbId: Long,
    val name: String,
    val year: Int?,
    val genreIds: String?,
)

const val DISCOVER_COLUMNS =
    "t.id, t.mediaType, t.tmdbId, t.name, t.originalName, t.year, t.posterPath, t.backdropPath, t.rating, " +
        "t.voteCount, t.popularity, t.genreIds, t.bestQuality, t.overview, t.imdbId, t.dubLabel"

@Dao
interface DiscoverDao {
    // ------------------------------------------------------------ istoric

    @Query("SELECT * FROM watch_history WHERE titleId = :titleId")
    suspend fun history(titleId: Long): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHistory(entry: WatchHistoryEntity)

    @Query(
        "SELECT h.titleId, h.progress, h.finished, h.watchedAt, t.mediaType, t.tmdbId, t.name, t.year, t.genreIds " +
            "FROM watch_history h JOIN title t ON t.id = h.titleId ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    suspend fun recentHistory(limit: Int): List<HistoryTitle>

    @Query("SELECT titleId FROM watch_state")
    suspend fun inProgress(): List<Long>

    // ------------------------------------------------------------ titluri disponibile

    @Query(
        "SELECT $DISCOVER_COLUMNS FROM title t WHERE t.sourceCount > 0 AND t.mediaType = :type AND t.tmdbId IN (:ids)",
    )
    suspend fun availableByTmdb(
        type: MediaType,
        ids: List<Long>,
    ): List<DiscoverTitle>

    @Query("SELECT $DISCOVER_COLUMNS FROM title t WHERE t.sourceCount > 0 AND t.imdbId IN (:ids)")
    suspend fun availableByImdb(ids: List<String>): List<DiscoverTitle>

    /** Cele mai populare titluri disponibile dintr-un gen ([pattern] = `%,28,%`). */
    @Query(
        "SELECT $DISCOVER_COLUMNS FROM title t WHERE t.sourceCount > 0 AND t.tmdbId > 0 " +
            "AND (',' || t.genreIds || ',') LIKE :pattern ORDER BY t.popularity DESC LIMIT :limit",
    )
    suspend fun popularInGenre(
        pattern: String,
        limit: Int,
    ): List<DiscoverTitle>

    /** Căutare după nume în indexul FTS (`title_search` nu e entitate Room, deci interogare „brută”). */
    @RawQuery
    suspend fun titles(query: SupportSQLiteQuery): List<DiscoverTitle>

    // ------------------------------------------------------------ cache

    @Query("SELECT * FROM discover_cache WHERE cacheKey = :key")
    suspend fun cached(key: String): DiscoverCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entry: DiscoverCacheEntity)

    @Query("DELETE FROM discover_cache WHERE fetchedAt < :before")
    suspend fun deleteCacheBefore(before: Long): Int

    @Query("SELECT * FROM imdb_map WHERE imdbId IN (:ids)")
    suspend fun imdbMap(ids: List<String>): List<ImdbMapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putImdbMap(entries: List<ImdbMapEntity>)
}
