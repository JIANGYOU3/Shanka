package com.qiuzhao.flashcards.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qiuzhao.flashcards.FlashcardsApplication
import com.qiuzhao.flashcards.data.offline.DeletionSyncOutcome
import java.util.concurrent.TimeUnit

/**
 * WorkManager backstop for the deletion outbox (the in-process coordinator is the fast path).
 * Per-user unique work (`deletion-sync/<user_id>`), CONNECTED constraint, exponential backoff:
 * the worker is a retry harness around the same serial
 * [com.qiuzhao.flashcards.data.offline.DeletionSyncCoordinator.syncOnce], so an optimistic
 * delete and a connectivity wake can never double-send the same tombstone.
 */
class DeletionSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val sync = (applicationContext as FlashcardsApplication).container.deletionSync
        return when (sync.syncOnce()) {
            DeletionSyncOutcome.DRAINED -> Result.success()
            // Transient failures ride WorkManager's exponential backoff.
            DeletionSyncOutcome.RETRY_SCHEDULED -> Result.retry()
            // The session is dead: stop until a fresh sign-in re-enqueues.
            DeletionSyncOutcome.PAUSED_AUTH, DeletionSyncOutcome.NO_SESSION -> Result.success()
        }
    }

    companion object {
        fun uniqueName(userId: String) = "deletion-sync/$userId"

        /** Enqueues (or keeps) the per-user unique sync backstop. */
        fun enqueue(context: Context, userId: String) {
            val request = OneTimeWorkRequestBuilder<DeletionSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(userId), ExistingWorkPolicy.KEEP, request)
        }
    }
}
