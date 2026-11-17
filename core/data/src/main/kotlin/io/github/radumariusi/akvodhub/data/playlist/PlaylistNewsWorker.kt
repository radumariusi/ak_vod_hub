package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.db.PlaylistDao
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Numai serialele urmărite: progres după ID, fără catalog complet sau cereri pe postere. */
class PlaylistNewsWorker(
    context: Context,
    params: WorkerParameters,
    private val importer: PlaylistImporter,
    private val dao: PlaylistDao,
    private val indexer: CatalogIndexer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var after = inputData.getLong("after", 0L)
        val started = System.currentTimeMillis()
        while (true) {
            if (PlaybackActivityState.active || System.currentTimeMillis() - started >= 120_000L) {
                enqueue(applicationContext, after, if (PlaybackActivityState.active) 60_000 else 0)
                if (!PlaybackActivityState.active) indexer.refresh()
                return Result.success()
            }
            val batch = dao.followedSeries(after, 8)
            if (batch.isEmpty()) break
            for (series in batch) {
                if (PlaybackActivityState.active || System.currentTimeMillis() - started >= 120_000L) break
                try {
                    importer.loadXtreamEpisodes(series.sourceId, series.streamId!!)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Păstrăm episoadele locale; următorul refresh încearcă din nou acest serial.
                }
                after = series.id
            }
        }
        if (PlaybackActivityState.active) enqueue(applicationContext, after, 60_000) else indexer.refresh()
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, after: Long = 0, delayMs: Long = 0) {
            val request =
                OneTimeWorkRequestBuilder<PlaylistNewsWorker>()
                    .setInputData(workDataOf("after" to after))
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager
                .getInstance(
                    context,
                ).enqueueUniqueWork("playlist-news", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
