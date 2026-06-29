package com.daintyz.timerwidget.billing

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class EntitlementSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = EntitlementSync.syncFromPlayAndCleanup(applicationContext)
        return if (result.synced) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "EntitlementSyncWorker"
        private const val UNIQUE_WORK_NAME = "entitlement_sync"
        private const val REPEAT_INTERVAL_HOURS = 12L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<EntitlementSyncWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }.onFailure {
                Log.w(TAG, "failed to enqueue entitlement sync", it)
            }
        }
    }
}
