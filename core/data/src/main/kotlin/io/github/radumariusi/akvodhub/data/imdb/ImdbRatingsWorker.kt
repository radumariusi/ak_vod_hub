package io.github.radumariusi.akvodhub.data.imdb

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImdbRatingsWorker(
    context: Context,
    params: WorkerParameters,
    private val updater: ImdbRatingsUpdater,
    private val settings: ImdbRatingsSettings,
    private val scheduler: ImdbRatingsScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(FORCE, false)
        try {
            if (PlaybackActivityState.active) throw RatingsPaused()
            updater.refresh(force)
            return Result.success()
        } catch (e: RatingsPaused) {
            scheduler.afterPlayback(force)
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            settings.failure("Notele IMDb nu au putut fi actualizate. Notele anterioare sunt păstrate.")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: IllegalArgumentException) {
            settings.failure("Fișierul IMDb nu are formatul așteptat. Notele anterioare sunt păstrate.")
            return Result.failure()
        }
    }

    companion object {
        const val FORCE = "force"
    }
}

class ImdbRatingsScheduler(context: Context, private val settings: ImdbRatingsSettings) {
    private val work by lazy { WorkManager.getInstance(context) }
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Verificare zilnică ieftină; descărcarea se face numai dacă au trecut șapte zile de la reușită. */
    fun ensureScheduled() {
        val periodic =
            PeriodicWorkRequestBuilder<ImdbRatingsWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .addTag(TAG)
                .addTag(PERIODIC_TAG)
                .build()
        work.enqueueUniquePeriodicWork("imdb-ratings-periodic", ExistingPeriodicWorkPolicy.KEEP, periodic)
        enqueueIfDue()
    }

    fun enqueueIfDue() {
        if (settings.isDue()) schedule(force = false, delayMs = 0L, policy = ExistingWorkPolicy.KEEP)
    }

    fun updateNow() = schedule(force = true, delayMs = 0L, policy = ExistingWorkPolicy.KEEP)

    fun afterPlayback(force: Boolean) = schedule(force, 60_000L, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun schedule(force: Boolean, delayMs: Long, policy: ExistingWorkPolicy) {
        val request =
            OneTimeWorkRequestBuilder<ImdbRatingsWorker>()
                .setInputData(workDataOf(ImdbRatingsWorker.FORCE to force))
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
        work.enqueueUniqueWork("imdb-ratings", policy, request)
    }

    val running get() =
        work.getWorkInfosByTagFlow(TAG).map { infos ->
            infos.any {
                it.state == WorkInfo.State.RUNNING ||
                    (it.state == WorkInfo.State.ENQUEUED && PERIODIC_TAG !in it.tags)
            }
        }

    companion object {
        private const val TAG = "imdb-ratings"
        private const val PERIODIC_TAG = "imdb-ratings-periodic"
    }
}
