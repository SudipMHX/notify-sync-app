package com.sudipmhx.notifysync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sudipmhx.notifysync.data.ApiClient
import com.sudipmhx.notifysync.data.DeviceSocketClient
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.model.NotificationPayload
import com.sudipmhx.notifysync.data.model.SmsPayload
import com.sudipmhx.notifysync.util.PrefsManager
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val BATCH_SIZE = 50
    }

    private val db = QueueDatabase.getDatabase(context)
    private val prefs = PrefsManager(context)
    private val gson = Gson()
    private val service = ApiClient.getService(context)

    override suspend fun doWork(): Result {
        if (prefs.getApiKey() == null) {
            Log.w(TAG, "Waiting for API key before syncing queued events")
            return Result.retry()
        }

        val socketClient = try {
            DeviceSocketClient(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Could not initialize the realtime connection", e)
            null
        }

        try {
            val socketConnected = try {
                socketClient?.connect() == true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Socket.IO connection failed; trying the HTTP API", e)
                false
            }

            var heartbeatFailed = false
            if (!socketConnected) {
                try {
                    val response = service.heartbeat()
                    if (!response.isSuccessful) {
                        val statusCode = response.code()
                        if (response.code() == 401 || response.code() == 403) {
                            Log.e(TAG, "Server rejected device heartbeat with HTTP $statusCode")
                            if (!prefs.clearRegistration()) {
                                Log.e(TAG, "Could not clear rejected registration credentials")
                            }
                            return Result.failure()
                        }
                        if (statusCode == 404 || statusCode == 405) {
                            Log.w(TAG, "Server does not support device heartbeats (HTTP $statusCode)")
                        } else {
                            Log.e(TAG, "Heartbeat failed with HTTP $statusCode")
                            heartbeatFailed = true
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.w(TAG, "Device heartbeat failed", e)
                    heartbeatFailed = true
                }
            }

            while (true) {
                val pendingItems = db.queueDao().getUnsynced(BATCH_SIZE)
                if (pendingItems.isEmpty()) {
                    if (heartbeatFailed) return Result.retry()
                    prefs.saveLastSyncTime(System.currentTimeMillis())
                    return Result.success()
                }

                var hasFailure = heartbeatFailed

                for (item in pendingItems) {
                    try {
                        when (item.type) {
                            "notification" -> {
                                val payload = gson.fromJson<NotificationPayload>(
                                    item.jsonPayload,
                                    NotificationPayload::class.java
                                ) ?: throw JsonParseException("Notification payload is null")
                                val normalizedPayload =
                                    payload.copy(timestamp = normalizeTimestamp(payload.timestamp))
                                if (
                                    socketConnected &&
                                    socketClient?.send("device:notification", normalizedPayload) == true
                                ) {
                                    db.queueDao().markSynced(item.id)
                                    continue
                                }

                                val response = service.sendNotification(normalizedPayload)
                                if (!handleResponse(item.id, response.code(), response.isSuccessful, response.errorBody()?.string())) {
                                    if (response.code() == 401 || response.code() == 403) return Result.failure()
                                    hasFailure = true
                                }
                            }
                            "sms" -> {
                                val payload = gson.fromJson<SmsPayload>(
                                    item.jsonPayload,
                                    SmsPayload::class.java
                                ) ?: throw JsonParseException("SMS payload is null")
                                val normalizedPayload =
                                    payload.copy(timestamp = normalizeTimestamp(payload.timestamp))
                                if (
                                    socketConnected &&
                                    socketClient?.send("device:sms", normalizedPayload) == true
                                ) {
                                    db.queueDao().markSynced(item.id)
                                    continue
                                }

                                val response = service.sendSms(normalizedPayload)
                                if (!handleResponse(item.id, response.code(), response.isSuccessful, response.errorBody()?.string())) {
                                    if (response.code() == 401 || response.code() == 403) return Result.failure()
                                    hasFailure = true
                                }
                            }
                            else -> {
                                Log.e(TAG, "Discarding queue item ${item.id} with unknown type '${item.type}'")
                                db.queueDao().delete(item.id)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        return Result.retry()
                    } catch (e: JsonParseException) {
                        Log.e(TAG, "Discarding malformed queue item ${item.id}", e)
                        db.queueDao().delete(item.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync queue item ${item.id}", e)
                        hasFailure = true
                    }
                }

                db.queueDao().deleteSynced()
                if (hasFailure) return Result.retry()
            }
        } finally {
            socketClient?.close()
        }
    }

    private suspend fun handleResponse(
        itemId: Int,
        statusCode: Int,
        successful: Boolean,
        errorBody: String?
    ): Boolean {
        if (successful) {
            db.queueDao().markSynced(itemId)
            return true
        }

        Log.e(
            TAG,
            "Server returned $statusCode for queue item $itemId: " +
                (errorBody ?: "no error details")
        )
        if (statusCode == 401 || statusCode == 403) {
            if (!prefs.clearRegistration()) {
                Log.e(TAG, "Could not clear rejected registration credentials")
            }
        }
        return false
    }

    private fun normalizeTimestamp(timestamp: String): String {
        return try {
            val epochMillis = timestamp.toLongOrNull()
            if (epochMillis != null) {
                Instant.ofEpochMilli(epochMillis).toString()
            } else {
                Instant.parse(timestamp).toString()
            }
        } catch (e: DateTimeException) {
            throw JsonParseException("Event timestamp is not valid ISO-8601 or epoch milliseconds", e)
        }
    }
}
