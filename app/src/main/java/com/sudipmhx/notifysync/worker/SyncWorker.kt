package com.sudipmhx.notifysync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.sudipmhx.notifysync.data.ApiClient
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.model.NotificationPayload
import com.sudipmhx.notifysync.data.model.SmsPayload
import com.sudipmhx.notifysync.util.PrefsManager
import retrofit2.HttpException
import java.io.IOException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val db = QueueDatabase.getDatabase(context)
    private val prefs = PrefsManager(context)
    private val gson = Gson()
    private val service = ApiClient.getService(context)

    override suspend fun doWork(): Result {
        val apiKey = prefs.getApiKey() ?: return Result.failure()
        val pendingItems = db.queueDao().getUnsynced()

        if (pendingItems.isEmpty()) return Result.success()

        var hasFailure = false

        pendingItems.forEach { item ->
            try {
                val response = when (item.type) {
                    "notification" -> {
                        val payload = gson.fromJson(item.jsonPayload, NotificationPayload::class.java)
                        service.sendNotification(payload)
                    }
                    "sms" -> {
                        val payload = gson.fromJson(item.jsonPayload, SmsPayload::class.java)
                        service.sendSms(payload)
                    }
                    else -> null
                }

                if (response?.isSuccessful == true) {
                    db.queueDao().markSynced(item.id)
                } else if (response?.code() == 401) {
                    // Unauthorized: ApiKey might be invalid/removed
                    Log.e("SyncWorker", "Unauthorized 401: Clearing API Key")
                    prefs.clear()
                    return Result.failure()
                } else {
                    hasFailure = true
                }
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    prefs.clear()
                    return Result.failure()
                }
                hasFailure = true
            } catch (e: IOException) {
                // Network error or timeout - retry later
                return Result.retry()
            } catch (e: Exception) {
                e.printStackTrace()
                hasFailure = true
            }
        }

        return if (hasFailure) {
            Result.retry()
        } else {
            prefs.saveLastSyncTime(System.currentTimeMillis())
            db.queueDao().deleteSynced()
            Result.success()
        }
    }
}
