package com.buslive.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.buslive.app.data.repo.StaticDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Refreshes cached stops + lines once a day (and on first launch). */
@HiltWorker
class StaticSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: StaticDataRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repo.sync(force = inputData.getBoolean(KEY_FORCE, false))
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 4) Result.retry() else Result.failure()
    }

    companion object {
        private const val KEY_FORCE = "force"
        private const val PERIODIC = "static-sync-daily"
        const val ONE_TIME = "static-sync-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StaticSyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .setInitialDelay(6, TimeUnit.HOURS)
                    .build(),
            )
        }

        fun runNow(context: Context, force: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StaticSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(androidx.work.workDataOf(KEY_FORCE to force))
                    .build(),
            )
        }
    }
}
