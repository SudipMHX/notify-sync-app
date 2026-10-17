package com.sudipmhx.notifysync.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.local.QueueEntity
import com.sudipmhx.notifysync.data.model.NotificationPayload
import com.sudipmhx.notifysync.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyNotificationListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val gson = Gson()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""

        // Skip if both title and text are empty
        if (title.isEmpty() && text.isEmpty()) return

        val payload = NotificationPayload(
            packageName = packageName,
            appName = getAppName(packageName, this),
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        scope.launch {
            val db = QueueDatabase.getDatabase(applicationContext)
            db.queueDao().insert(
                QueueEntity(
                    type = "notification",
                    jsonPayload = gson.toJson(payload)
                )
            )
            triggerSync()
        }
    }

    private fun getAppName(packageName: String, context: Context): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun triggerSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
