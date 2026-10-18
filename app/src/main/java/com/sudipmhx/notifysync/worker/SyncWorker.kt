package com.sudipmhx.notifysync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sudipmhx.notifysync.data.DirectSyncResult
import com.sudipmhx.notifysync.data.ImmediateSyncManager

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (ImmediateSyncManager.syncDirect(applicationContext)) {
            is DirectSyncResult.Success -> Result.success()
            is DirectSyncResult.AuthError -> Result.failure()
            is DirectSyncResult.NetworkError,
            is DirectSyncResult.Error -> Result.retry()
        }
    }
}
