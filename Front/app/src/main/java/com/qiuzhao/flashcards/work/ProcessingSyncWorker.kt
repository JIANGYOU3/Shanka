package com.qiuzhao.flashcards.work

import android.content.Context
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
import com.qiuzhao.flashcards.FlashcardsApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * WorkManager backstop for the observation engine (V25-D-34): one forced reconcile of every
 * in-flight resource (parsing projects, non-terminal tasks) so finished server-side processing
 * reaches the Room projection even when the app sits in the background with no pollers running.
 * Reads are session-scoped inside the repository, so one app-wide unique worker is enough; a
 * signed-out run is a harmless no-op. The foreground ON_RESUME reconcile is the primary path.
 */
class ProcessingSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        (applicationContext as FlashcardsApplication).container.v25Repository.refreshProcessing()
        Result.success()
    } catch (failure: Throwable) {
        if (failure is CancellationException) throw failure
        // Transient (network/5xx) failures ride WorkManager's exponential backoff.
        Result.retry()
    }

    companion object {
        private const val ONE_TIME_NAME = "processing-sync/on-sign-in"
        private const val PERIODIC_NAME = "processing-sync/periodic"

        /** Sign-in kick: reconcile once soon after connectivity allows. */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProcessingSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Bounded periodic backstop; WorkManager's floor is 15 minutes. */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProcessingSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
