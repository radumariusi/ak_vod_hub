package io.github.radumariusi.akvodhub.data.playlist

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Starea unui import în curs: în așteptare (fără progres) sau rulează (cu progres). */
data class ImportActivity(
    val sourceId: Long?,
    val progress: ImportProgress?,
)

/** Pornește importurile prin WorkManager: imediat (la cerere) și periodic (o dată pe zi). */
class PlaylistScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun importNow(
        sourceId: Long,
        needsNetwork: Boolean = true,
    ) {
        val request =
            OneTimeWorkRequestBuilder<PlaylistImportWorker>()
                .setInputData(workDataOf(PlaylistImportWorker.KEY_SOURCE_ID to sourceId))
                .setConstraints(constraints(needsNetwork))
                .addTag(TAG)
                .addTag(SOURCE_TAG_PREFIX + sourceId)
                .build()
        workManager.enqueueUniqueWork(uniqueName(sourceId), ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<PlaylistImportWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints(needsNetwork = true))
                .addTag(TAG)
                .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(sourceId: Long) {
        workManager.cancelUniqueWork(uniqueName(sourceId))
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PERIODIC_NAME)
    }

    /** Importurile care așteaptă sau rulează acum. */
    fun activity(): Flow<List<ImportActivity>> =
        workManager.getWorkInfosByTagFlow(TAG).map { infos ->
            infos.mapNotNull { info ->
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        ImportActivity(sourceIdFromTags(info.tags), PlaylistImportWorker.progressOf(info.progress))
                    }

                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        // Rularea periodică stă mereu „în așteptare”; nu o arătăm ca import în curs.
                        sourceIdFromTags(info.tags)?.let { ImportActivity(it, null) }
                    }

                    else -> {
                        null
                    }
                }
            }
        }

    private fun sourceIdFromTags(tags: Set<String>): Long? =
        tags.firstNotNullOfOrNull { tag -> tag.removePrefix(SOURCE_TAG_PREFIX).takeIf { it != tag }?.toLongOrNull() }

    private fun constraints(needsNetwork: Boolean) =
        Constraints
            .Builder()
            .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()

    private fun uniqueName(sourceId: Long) = "playlist-import-$sourceId"

    private companion object {
        const val TAG = "playlist-import"
        const val SOURCE_TAG_PREFIX = "playlist-source:"
        const val PERIODIC_NAME = "playlist-refresh-daily"
    }
}
