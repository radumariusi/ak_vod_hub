package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.catalog.MatchScheduler
import io.github.radumariusi.akvodhub.data.catalog.MatchWorker
import io.github.radumariusi.akvodhub.data.catalog.TitleMatcher
import io.github.radumariusi.akvodhub.data.db.PlaylistDao
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsScheduler
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsSettings
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsUpdater
import io.github.radumariusi.akvodhub.data.imdb.ImdbRatingsWorker
import kotlinx.coroutines.CancellationException

/**
 * Importă un playlist (dacă primește [KEY_SOURCE_ID]) sau le reîmprospătează pe toate (rularea periodică).
 * Erorile se salvează la playlist și se văd în Setări; nu reîncercăm automat, ca să nu bombardăm serverul.
 */
class PlaylistImportWorker(
    context: Context,
    params: WorkerParameters,
    private val importer: PlaylistImporter,
    private val dao: PlaylistDao,
    private val onImported: () -> Unit,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val single = inputData.getLong(KEY_SOURCE_ID, -1L)
        val ids = if (single >= 0) listOf(single) else dao.sources().map { it.id }
        var imported = false
        for (id in ids) {
            try {
                importer.import(id) { progress -> setProgress(progress.toData()) }
                imported = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import eșuat pentru playlist-ul $id: ${e.message}")
            }
        }
        // Potrivirea TMDB rulează separat, ca importul să nu dureze mai mult.
        if (imported) onImported()
        return Result.success()
    }

    companion object {
        private const val TAG = "PlaylistImport"
        const val KEY_SOURCE_ID = "sourceId"
        private const val KEY_PHASE = "phase"
        private const val KEY_SCANNED = "scanned"
        private const val KEY_KEPT = "kept"

        fun ImportProgress.toData(): Data =
            workDataOf(KEY_SOURCE_ID to sourceId, KEY_PHASE to phase, KEY_SCANNED to scanned, KEY_KEPT to kept)

        fun progressOf(data: Data): ImportProgress? {
            val id = data.getLong(KEY_SOURCE_ID, -1L)
            if (id < 0) return null
            return ImportProgress(
                id,
                data.getString(KEY_PHASE).orEmpty(),
                data.getInt(KEY_SCANNED, 0),
                data.getInt(KEY_KEPT, 0),
            )
        }
    }
}

/** Creează worker-ele noastre cu dependențele lor (WorkManager e inițializat manual, vezi aplicația). */
class PlaylistWorkerFactory(
    private val importer: PlaylistImporter,
    private val dao: PlaylistDao,
    private val matcher: TitleMatcher,
    private val matchScheduler: MatchScheduler,
    private val indexer: CatalogIndexer,
    private val imdbUpdater: ImdbRatingsUpdater,
    private val imdbSettings: ImdbRatingsSettings,
    private val imdbScheduler: ImdbRatingsScheduler,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            PlaylistImportWorker::class.java.name -> {
                PlaylistImportWorker(appContext, workerParameters, importer, dao, matchScheduler::enqueue)
            }

            MatchWorker::class.java.name -> {
                MatchWorker(appContext, workerParameters, matcher, matchScheduler, indexer, imdbScheduler)
            }

            ImdbRatingsWorker::class.java.name -> {
                ImdbRatingsWorker(appContext, workerParameters, imdbUpdater, imdbSettings, imdbScheduler)
            }

            else -> {
                null
            }
        }
}
