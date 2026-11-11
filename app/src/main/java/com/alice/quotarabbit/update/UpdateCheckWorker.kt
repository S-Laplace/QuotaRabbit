package com.alice.quotarabbit.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alice.quotarabbit.domain.update.QuotaRabbitRepository

class UpdateCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result =
        if (!QuotaRabbitRepository.isConfigured) Result.success()
        else when (UpdateCheckOutcome.run(applicationContext as? UpdateCheckDependenciesProvider)) {
            UpdateCheckOutcome.Success -> Result.success()
            UpdateCheckOutcome.Retry -> Result.retry()
        }
}
