package com.sudipmhx.notifysync.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.local.QueueEntity
import com.sudipmhx.notifysync.data.model.NotificationPayload
import com.sudipmhx.notifysync.worker.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class MyNotificationListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val gson = Gson()
    private val appNameCache = ConcurrentHashMap<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val appName = getAppName(packageName)
        val title = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        ).ifBlank { appName }
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(separator = " ") { it.toString() }
        val text = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            textLines,
            sbn.notification.tickerText
        )

        val payload = NotificationPayload(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = Instant.ofEpochMilli(sbn.postTime).toString()
        )

        scope.launch {
            try {
                val db = QueueDatabase.getDatabase(applicationContext)
                db.queueDao().insert(
                    QueueEntity(
                        type = "notification",
                        jsonPayload = gson.toJson(payload)
                    )
                )
                SyncScheduler.enqueue(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not queue notification from $packageName", e)
            }
        }
    }

    private fun firstNonBlank(vararg values: CharSequence?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.toString().orEmpty()

    private fun getAppName(packageName: String): String {
        appNameCache[packageName]?.let { return it }

        val resolvedName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(ai).toString().trim()
            if (appLabel.isNotEmpty() && appLabel != packageName && !appLabel.startsWith("com.")) {
                appLabel
            } else {
                null
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val name = resolvedName ?: buildFriendlyAppName(packageName)
        appNameCache[packageName] = name
        return name
    }

    private fun buildFriendlyAppName(packageName: String): String {
        val lastSegment = packageName.substringAfterLast('.').ifBlank { packageName }
        val words = lastSegment.replace('_', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { word ->
                word.lowercase().replaceFirstChar { character ->
                    character.titlecase()
                }
            }

        return if (words.isEmpty()) {
            "Unknown app"
        } else {
            words.joinToString(" ")
        }
    }

    companion object {
        private const val TAG = "NotificationListener"
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
