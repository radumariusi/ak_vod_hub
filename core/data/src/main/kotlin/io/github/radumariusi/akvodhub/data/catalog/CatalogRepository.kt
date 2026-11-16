package io.github.radumariusi.akvodhub.data.catalog

import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.MatchState
import io.github.radumariusi.akvodhub.data.db.RawEntryEntity
import io.github.radumariusi.akvodhub.data.tmdb.TmdbClient
import io.github.radumariusi.akvodhub.data.tmdb.TmdbSettings
import io.github.radumariusi.akvodhub.parser.match.QualityGroup
import io.github.radumariusi.akvodhub.parser.match.SourceGrouping
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.IOException

/** Starea potrivirii TMDB, pentru Setări. */
data class MatchStatus(
    val hasKey: Boolean,
    val running: Boolean,
    /** Filme/seriale unice (duplicatele unite). */
    val titles: Int,
    val matched: Int,
    val unmatched: Int,
    val pending: Int,
    val lastError: String?,
)

class CatalogRepository(
    private val db: AkVodDatabase,
    private val tmdb: TmdbClient,
    private val settings: TmdbSettings,
    private val scheduler: MatchScheduler,
) {
    private val dao = db.catalogDao()

    val status: Flow<MatchStatus> =
        combine(
            dao.observeStateCounts(),
            dao.observeTitleCount(),
            settings.state,
            scheduler.running,
        ) { counts, titles, state, running ->
            fun count(vararg states: Int) = counts.filter { it.state in states }.sumOf { it.count }
            MatchStatus(
                hasKey = state.hasKey,
                running = running,
                titles = titles,
                matched = count(MatchState.MATCHED),
                unmatched = count(MatchState.UNMATCHED),
                pending = count(MatchState.PENDING, MatchState.PENDING_SEARCH),
                lastError = state.lastError,
            )
        }

    /** Verifică cheia la TMDB, o salvează și pornește potrivirea. Aruncă excepție cu mesaj dacă nu e bună. */
    suspend fun saveTmdbKey(key: String) {
        val clean = key.trim()
        require(clean.length >= 16) { "Cheia pare incompletă." }
        val ok =
            try {
                tmdb.validate(clean)
            } catch (e: IOException) {
                throw IOException("Nu pot contacta TMDB: ${e.message ?: "eroare de rețea"}")
            }
        require(ok) { "TMDB a respins cheia. Copiaz-o din nou din contul TMDB." }
        settings.saveKey(clean)
        scheduler.enqueue()
    }

    fun clearTmdbKey() = settings.clearKey()

    fun matchNow() = scheduler.enqueue()

    /** Caută din nou intrările nepotrivite (ex. după ce TMDB a adăugat filme noi). */
    suspend fun retryUnmatched() {
        db.withTransaction {
            dao.clearNegativeCache()
            dao.resetUnmatched()
        }
        scheduler.enqueue()
    }

    /** Sursele unui film/serial din toate playlist-urile, grupate după calitate, apoi limbă. */
    suspend fun sourceGroups(
        titleId: Long,
        preferredLanguages: List<String> = emptyList(),
    ): List<QualityGroup> = SourceGrouping.group(dao.sources(titleId).map { it.toSourceInfo() }, preferredLanguages)
}

fun RawEntryEntity.toSourceInfo() =
    SourceInfo(
        id = id,
        quality = quality,
        hdr = hdr,
        languages = languages?.split(',').orEmpty(),
        subtitles = subtitles?.split(',').orEmpty(),
    )
