package com.sudipmhx.notifysync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "sync-queued-events"
    private const val PERIODIC_WORK_NAME = "periodic-queued-event-sync"

    fun enqueue(context: Context) {
        val appContext = context.applicationContext
        schedulePeriodicSync(appContext)

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun syncNow(context: Context): WorkInfo.State {
        val appContext = context.applicationContext
        schedulePeriodicSync(appContext)

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        val workManager = WorkManager.getInstance(appContext)

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        return withTimeoutOrNull(SYNC_NOW_TIMEOUT_MS) {
            workManager.getWorkInfoByIdFlow(request.id)
                .filterNotNull()
                .first {
                    it.state.isFinished ||
                        (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0)
                }
                .state
        } ?: WorkInfo.State.ENQUEUED
    }

    fun scheduleAfterBoot(context: Context) {
        val appContext = context.applicationContext
        schedulePeriodicSync(appContext)
        enqueue(appContext)
    }

    private fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private const val SYNC_NOW_TIMEOUT_MS = 60_000L
}
