package io.github.radumariusi.akvodhub.data.catalog

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Rulează potrivirea TMDB în fundal. Android oprește un job după 10 minute, așa că fiecare rulare
 * lucrează cel mult [BUDGET_MS] și, dacă mai e de lucru, programează continuarea.
 */
class MatchWorker(
    context: Context,
    params: WorkerParameters,
    private val matcher: TitleMatcher,
    private val scheduler: MatchScheduler,
    private val indexer: CatalogIndexer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = matcher.run(BUDGET_MS)
        // Hub-ul vede rezultatele după fiecare rundă (și titlurile locale, chiar fără cheie TMDB).
        indexer.refresh()
        return when (outcome) {
            MatchOutcome.MORE -> {
                scheduler.enqueue()
                Result.success()
            }

            MatchOutcome.NETWORK -> {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            }

            MatchOutcome.DONE, MatchOutcome.NEEDS_KEY, MatchOutcome.BAD_KEY -> {
                Result.success()
            }
        }
    }

    private companion object {
        const val BUDGET_MS = 8 * 60_000L
        const val MAX_RETRIES = 5
    }
}

/** Pornește potrivirea TMDB (după import, după salvarea cheii sau la cerere). */
class MatchScheduler(
    context: Context,
) {
    // Lazy: fabrica de worker-e depinde de această clasă, iar WorkManager se inițializează cu fabrica.
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun enqueue() {
        val request =
            OneTimeWorkRequestBuilder<MatchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
        // APPEND: dacă rulează deja, următoarea rulare pornește după ea (și prinde intrările noi).
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    val running: Flow<Boolean>
        get() =
            workManager.getWorkInfosByTagFlow(TAG).map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private companion object {
        const val NAME = "tmdb-match"
        const val TAG = "tmdb-match"
    }
}
