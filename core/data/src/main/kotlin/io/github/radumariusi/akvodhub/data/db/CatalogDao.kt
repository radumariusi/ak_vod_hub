package io.github.radumariusi.akvodhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
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

    @Query("UPDATE title SET imdbId = :imdbId, imdbRating = NULL, imdbVotes = NULL, imdbBadge = NULL WHERE id = :id")
    suspend fun setImdbId(
        id: Long,
        imdbId: String,
    )

    /** Întoarce -1 dacă titlul există deja. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitle(title: TitleEntity): Long

    @Update
    suspend fun updateTitle(title: TitleEntity)

    @Query("DELETE FROM title WHERE id = :id")
    suspend fun deleteTitle(id: Long)

    @Query(
        "SELECT * FROM title WHERE tmdbId > 0 AND metadataFetched = 0 AND id > :afterId " +
            "AND EXISTS(SELECT 1 FROM raw_entry r WHERE r.titleId = title.id) ORDER BY id LIMIT :limit",
    )
    suspend fun titlesWithoutDetails(
        afterId: Long,
        limit: Int,
    ): List<TitleEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM title WHERE sourceCount > 0 AND imdbId IS NOT NULL)")
    suspend fun hasImdbTitles(): Boolean

    @Query("SELECT DISTINCT imdbId FROM title WHERE sourceCount > 0 AND imdbId IN (:ids)")
    suspend fun availableImdbIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putImdbStage(entries: List<ImdbRatingStage>)

    @Query("DELETE FROM imdb_rating_stage")
    suspend fun clearImdbStage()

    /** Numai după citirea completă: înlocuiește notele și elimină cache-ul vechi absent din setul nou. */
    @Query(
        "UPDATE title SET imdbRating = (SELECT rating FROM imdb_rating_stage s WHERE s.imdbId = title.imdbId), " +
            "imdbVotes = (SELECT votes FROM imdb_rating_stage s WHERE s.imdbId = title.imdbId), " +
            "imdbBadge = (SELECT badge FROM imdb_rating_stage s WHERE s.imdbId = title.imdbId) " +
            "WHERE imdbRating IS NOT NULL OR imdbId IN (SELECT imdbId FROM imdb_rating_stage)",
    )
    suspend fun applyImdbRatings(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM person_credit WHERE personId = :personId)")
    suspend fun hasPersonCredits(personId: Long): Boolean

    @Query(
        "DELETE FROM person_credit WHERE CAST(personId AS TEXT) NOT IN " +
            "(SELECT substr(cacheKey, 7) FROM discover_cache WHERE cacheKey LIKE 'actor:%' " +
            "ORDER BY fetchedAt DESC LIMIT 24)",
    )
    suspend fun trimPersonCredits()

    @Query(
        "DELETE FROM discover_cache WHERE cacheKey LIKE 'actor:%' AND cacheKey NOT IN " +
            "(SELECT cacheKey FROM discover_cache WHERE cacheKey LIKE 'actor:%' ORDER BY fetchedAt DESC LIMIT 24)",
    )
    suspend fun trimPersonCache()

    @Query("DELETE FROM person_credit WHERE personId = :personId")
    suspend fun clearPersonCredits(personId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPersonCredits(entries: List<PersonCreditEntity>)

    // ------------------------------------------------------------ surse și statistici

    /** Toate sursele unui titlu, din toate playlist-urile (fără episoade). */
    @Query("SELECT * FROM raw_entry WHERE titleId = :titleId AND kind <> 'EPISODE'")
    suspend fun sources(titleId: Long): List<RawEntryEntity>

    @Query("SELECT * FROM raw_entry WHERE titleId = :titleId AND kind = 'EPISODE' ORDER BY season, episode")
    suspend fun episodes(titleId: Long): List<RawEntryEntity>

    @Query("SELECT matchState AS state, COUNT(*) AS count FROM raw_entry WHERE matchState <> 3 GROUP BY matchState")
    fun observeStateCounts(): Flow<List<StateCount>>

    /** Câte filme/seriale TMDB unice (după unirea duplicatelor) au surse în playlist-uri. */
    @Query("SELECT COUNT(DISTINCT titleId) FROM raw_entry WHERE titleId IS NOT NULL AND matchState = 1")
    fun observeTitleCount(): Flow<Int>

    // ------------------------------------------------------------ hub (etapa 5)

    /** Liste de titluri construite de `CatalogQuery` (filtre, căutare FTS, ordonare, paginare). */
    @RawQuery
    suspend fun cards(query: SupportSQLiteQuery): List<TitleCard>

    @Query(
        "SELECT t.id, t.mediaType, t.name, t.year, t.posterPath, t.backdropPath, t.rating, t.bestQuality, " +
            "t.overview, t.dubLabel, t.imdbRating, t.imdbVotes, t.tmdbBadge, t.imdbBadge, t.hdrBadge, t.isNew, " +
            "t.watchProgress FROM watch_state w JOIN title t ON t.id = w.titleId " +
            "WHERE t.sourceCount > 0 " +
            "ORDER BY w.updatedAt DESC LIMIT :limit",
    )
    suspend fun continueWatching(limit: Int): List<TitleCard>

    /** „Continuă vizionarea” cu poziția și episodul (pentru rândul Watch Next de pe ecranul principal). */
    @Query(
        "SELECT t.id AS titleId, t.mediaType, t.name, t.year, t.posterPath, t.backdropPath, t.overview, " +
            "w.season, w.episode, w.positionMs, w.durationMs, w.updatedAt FROM watch_state w " +
            "JOIN title t ON t.id = w.titleId WHERE t.sourceCount > 0 ORDER BY w.updatedAt DESC LIMIT :limit",
    )
    suspend fun continueItems(limit: Int): List<ContinueItem>

    @Query("SELECT * FROM raw_entry WHERE id = :id")
    suspend fun entry(id: Long): RawEntryEntity?

    // ------------------------------------------------------------ player (etapa 6)

    @Query("SELECT * FROM watch_state WHERE titleId = :titleId")
    suspend fun watchState(titleId: Long): WatchStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putWatchState(state: WatchStateEntity)

    @Query("UPDATE title SET watchProgress = :progress WHERE id = :titleId")
    suspend fun setPosterProgress(titleId: Long, progress: Double)

    @Query("DELETE FROM watch_state WHERE titleId = :titleId")
    suspend fun deleteWatchState(titleId: Long)

    /** Combinațiile de limbi din playlist-uri, cele mai frecvente primele (pentru filtrul „Limbă”). */
    @Query(
        "SELECT languages FROM raw_entry WHERE languages IS NOT NULL AND titleId IS NOT NULL " +
            "GROUP BY languages ORDER BY COUNT(*) DESC LIMIT 80",
    )
    suspend fun languageCombinations(): List<String>

    // ------------------------------------------------------------ indexarea catalogului

    /** Intrări fără titlu încă (de potrivit sau negăsite pe TMDB), pentru titlurile locale. */
    @Query(
        "SELECT id, kind, name, groupTitle, title, year, languages, tmdbId, matchState FROM raw_entry " +
            "WHERE titleId IS NULL AND matchState IN (0, 2, 4) AND id > :afterId ORDER BY id LIMIT :limit",
    )
    suspend fun unlinked(
        afterId: Long,
        limit: Int,
    ): List<PendingEntry>

    /** Primul logo din playlist al unor intrări (posterul unui titlu local). */
    @Query("SELECT logo FROM raw_entry WHERE id IN (:ids) AND logo IS NOT NULL AND logo <> '' LIMIT 1")
    suspend fun firstLogo(ids: List<Long>): String?

    /** Leagă intrările de un titlu local, fără să schimbe starea potrivirii (rămân de potrivit / negăsite). */
    @Query("UPDATE raw_entry SET titleId = :titleId WHERE id IN (:ids)")
    suspend fun linkLocal(
        ids: List<Long>,
        titleId: Long,
    )

    /** Rezumatele surselor pentru fiecare titlu (vezi [TitleEntity]). `bestQuality` = `VideoQuality.ordinal`. */
    @Query(
        "UPDATE title SET " +
            "sourceCount = (SELECT COUNT(*) FROM raw_entry r WHERE r.titleId = title.id), " +
            "addedAt = (SELECT MAX(CASE WHEN r.providerAddedAt IS NULL THEN r.firstSeenAt " +
            "WHEN r.providerAddedAt < 100000000000 THEN r.providerAddedAt * 1000 ELSE r.providerAddedAt END) " +
            "FROM raw_entry r WHERE r.titleId = title.id), " +
            "bestQuality = (SELECT MAX(CASE r.quality WHEN 'UHD' THEN 3 WHEN 'FHD' THEN 2 WHEN 'HD' THEN 1 " +
            "WHEN 'SD' THEN 0 END) FROM raw_entry r WHERE r.titleId = title.id), " +
            "qualities = (SELECT ',' || group_concat(DISTINCT r.quality) || ',' FROM raw_entry r " +
            "WHERE r.titleId = title.id), " +
            "audioLanguages = (SELECT ',' || group_concat(DISTINCT r.languages) || ',' FROM raw_entry r " +
            "WHERE r.titleId = title.id)",
    )
    suspend fun updateSummaries()

    /** Titlurile locale rămase fără surse (potrivite între timp pe TMDB sau scoase din playlist). */
    @Query("DELETE FROM title WHERE tmdbId < 0 AND sourceCount = 0")
    suspend fun deleteEmptyLocalTitles(): Int

    @Query("UPDATE title SET isNew = 0 WHERE isNew = 1 AND (addedAt <= :cutoff OR addedAt > :now)")
    suspend fun expireNewBadges(cutoff: Long, now: Long): Int

    /** O „amprentă” ieftină a catalogului: dacă nu s-a schimbat, indexarea nu are ce reface. */
    @Query(
        "SELECT (SELECT COUNT(*) || ':' || TOTAL(id) || ':' || TOTAL(titleId) || ':' || " +
            "IFNULL(MAX(seenRun), 0) FROM raw_entry) || '|' || " +
            "(SELECT COUNT(*) || ':' || IFNULL(MAX(updatedAt), 0) || ':' || TOTAL(sourceCount > 0) FROM title)",
    )
    suspend fun fingerprint(): String
}
