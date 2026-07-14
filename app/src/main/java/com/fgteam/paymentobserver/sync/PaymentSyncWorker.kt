package com.fgteam.paymentobserver.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PaymentSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (
        PaymentSyncRepository.getInstance(applicationContext).syncPending()
    ) {
        SyncOutcome.COMPLETE, SyncOutcome.AUTH_REQUIRED -> Result.success()
        SyncOutcome.RETRY -> Result.retry()
    }
}

object PaymentSyncScheduler {
    private const val UNIQUE_WORK = "payment-observer-pending-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PaymentSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
