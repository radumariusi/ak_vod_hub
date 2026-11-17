package io.github.radumariusi.akvodhub.data.imdb

import androidx.room.withTransaction
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.ImdbRatingStage
import io.github.radumariusi.akvodhub.parser.catalog.ImdbRatings
import io.github.radumariusi.akvodhub.parser.catalog.PosterBadges
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class RatingsPaused : Exception()

/** Arhiva nu se salvează. Tabela de lucru conține numai notele titlurilor disponibile în playlist. */
class ImdbRatingsUpdater(
    private val db: AkVodDatabase,
    private val http: OkHttpClient,
    private val settings: ImdbRatingsSettings,
    private val indexer: CatalogIndexer,
) {
    private val mutex = Mutex()

    suspend fun refresh(force: Boolean) =
        mutex.withLock {
            if (!force && !settings.isDue()) return@withLock
            val dao = db.catalogDao()
            if (!dao.hasImdbTitles()) return@withLock
            withContext(Dispatchers.IO) {
                dao.clearImdbStage()
                try {
                    if (PlaybackActivityState.active) throw RatingsPaused()
                    val call =
                        http.newCall(
                            Request
                                .Builder()
                                .url(DATASET)
                                .header("Accept-Encoding", "identity")
                                .build(),
                        )
                    call.timeout().timeout(8, TimeUnit.MINUTES)
                    var seen = 0L
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("IMDb a răspuns cu HTTP ${response.code}.")
                        GZIPInputStream(response.body.byteStream()).bufferedReader(Charsets.UTF_8).use { reader ->
                            for (batch in ImdbRatings.read(reader).chunked(BATCH)) {
                                currentCoroutineContext().ensureActive()
                                if (PlaybackActivityState.active) throw RatingsPaused()
                                seen += batch.size
                                val available = dao.availableImdbIds(batch.map { it.imdbId }).toHashSet()
                                val matched = batch.filter { it.imdbId in available }
                                if (matched.isNotEmpty()) {
                                    dao.putImdbStage(
                                        matched.map {
                                            ImdbRatingStage(
                                                it.imdbId,
                                                it.rating,
                                                it.votes,
                                                PosterBadges.rating("IMDb", it.rating),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (seen == 0L) throw IOException("Fișierul IMDb nu conține note valide.")
                    currentCoroutineContext().ensureActive()
                    if (PlaybackActivityState.active) throw RatingsPaused()
                    val updated =
                        db.withTransaction {
                            dao.applyImdbRatings().also { dao.clearImdbStage() }
                        }
                    settings.success(System.currentTimeMillis(), updated)
                    indexer.notifyCardsChanged()
                } finally {
                    // Nici eroarea, nici anularea nu lasă o listă incompletă vizibilă utilizatorului.
                    withContext(NonCancellable) { dao.clearImdbStage() }
                }
            }
        }

    companion object {
        const val DATASET = "https://datasets.imdbws.com/title.ratings.tsv.gz"
        private const val BATCH = 500
    }
}
