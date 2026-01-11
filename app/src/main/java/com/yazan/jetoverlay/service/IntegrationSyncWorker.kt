package com.yazan.jetoverlay.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yazan.jetoverlay.JetOverlayApplication
import com.yazan.jetoverlay.service.integration.EmailIntegration
import com.yazan.jetoverlay.service.integration.GitHubIntegration
import com.yazan.jetoverlay.service.integration.NotionIntegration
import com.yazan.jetoverlay.service.integration.SlackIntegration
import com.yazan.jetoverlay.util.Logger
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based worker for battery-efficient periodic sync of integrations.
 *
 * This replaces constant foreground polling with system-scheduled background work
 * that respects doze mode and battery optimization.
 */
class IntegrationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val COMPONENT = "IntegrationSyncWorker"
        private const val WORK_NAME = "integration_sync"

        // Minimum interval for periodic work (WorkManager requires >= 15 minutes)
        private const val SYNC_INTERVAL_MINUTES = 15L

        /**
         * Schedules periodic integration sync work.
         * Uses ExistingPeriodicWorkPolicy.UPDATE to update if already scheduled.
         */
        fun schedule(context: Context) {
            Logger.i(COMPONENT, "Scheduling periodic integration sync")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<IntegrationSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )

            Logger.i(COMPONENT, "Periodic sync scheduled (interval: ${SYNC_INTERVAL_MINUTES}m)")
        }

        /**
         * Cancels scheduled integration sync work.
         */
        fun cancel(context: Context) {
            Logger.i(COMPONENT, "Cancelling periodic integration sync")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Logger.lifecycle(COMPONENT, "doWork started")

        return try {
            val repository = JetOverlayApplication.instance.repository

            // Sync each connected integration
            var anySuccess = false

            if (SlackIntegration.isConnected()) {
                Logger.d(COMPONENT, "Syncing Slack")
                // Trigger a single poll cycle instead of continuous polling
                SlackIntegration.syncOnce(repository)
                anySuccess = true
            }

            if (EmailIntegration.isConnected()) {
                Logger.d(COMPONENT, "Syncing Email")
                EmailIntegration.syncOnce()
                anySuccess = true
            }

            if (NotionIntegration.isConnected()) {
                Logger.d(COMPONENT, "Syncing Notion")
                NotionIntegration.syncOnce()
                anySuccess = true
            }

            if (GitHubIntegration.isConnected()) {
                Logger.d(COMPONENT, "Syncing GitHub")
                GitHubIntegration.syncOnce()
                anySuccess = true
            }

            Logger.lifecycle(COMPONENT, "doWork completed successfully")
            Result.success()
        } catch (e: Exception) {
            Logger.e(COMPONENT, "doWork failed", e)
            // Retry on failure
            Result.retry()
        }
    }
}
