package com.sudipmhx.notifysync.data

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.model.NotificationPayload
import com.sudipmhx.notifysync.data.model.SmsPayload
import com.sudipmhx.notifysync.util.PrefsManager
import com.sudipmhx.notifysync.worker.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

sealed class DirectSyncResult {
    data class Success(val syncedCount: Int) : DirectSyncResult()
    data class NetworkError(val message: String) : DirectSyncResult()
    data class AuthError(val statusCode: Int) : DirectSyncResult()
    data class Error(val message: String) : DirectSyncResult()
}

object ImmediateSyncManager {
    private const val TAG = "ImmediateSyncManager"
    private const val BATCH_SIZE = 50
    private const val MAX_ITEM_FAILURES = 5
    private const val WAKELOCK_TAG = "notifysync:immediate_sync_wakelock"
    private const val WAKELOCK_TIMEOUT_MS = 25_000L

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private val triggerLock = Any()
    private val syncRequested = AtomicBoolean(false)
    private var triggerJob: Job? = null
    private val gson = Gson()

    /**
     * Trigger immediate asynchronous sync with a WakeLock.
     * Used by NotificationListenerService and SmsReceiver when new events arrive in real-time.
     */
    fun triggerSync(context: Context) {
        val appContext = context.applicationContext
        syncRequested.set(true)
        synchronized(triggerLock) {
            if (triggerJob?.isActive == true) return
            triggerJob = syncScope.launch {
                val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
                try {
                    while (syncRequested.getAndSet(false)) {
                        when (syncPendingEvents(appContext)) {
                            is DirectSyncResult.NetworkError,
                            is DirectSyncResult.Error -> SyncScheduler.enqueue(appContext)
                            is DirectSyncResult.Success,
                            is DirectSyncResult.AuthError -> Unit
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Immediate sync error", e)
                    SyncScheduler.enqueue(appContext)
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) wakeLock.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not release immediate sync wake lock", e)
                    }
                    synchronized(triggerLock) {
                        triggerJob = null
                        if (syncRequested.get()) triggerSync(appContext)
                    }
                }
            }
        }
    }

    /**
     * Direct sync for UI (e.g. Refresh button in MainActivity).
     */
    suspend fun syncDirect(context: Context): DirectSyncResult {
        return syncPendingEvents(context.applicationContext)
    }

    private suspend fun syncPendingEvents(appContext: Context): DirectSyncResult {
        return syncMutex.withLock {
            val prefs = PrefsManager(appContext)
            val db = QueueDatabase.getDatabase(appContext)

            // Step 1: Check registration
            if (!DeviceRegistration.isRegistered(prefs)) {
                try {
                    DeviceRegistration.register(appContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    return@withLock DirectSyncResult.NetworkError("Network unreachable during registration: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Registration failed during sync", e)
                    return@withLock DirectSyncResult.AuthError(401)
                }
            }

            val service = ApiClient.getService(appContext)

            // Step 2: Test heartbeat
            var reRegistered = false
            try {
                val heartbeatResponse = service.heartbeat()
                if (!heartbeatResponse.isSuccessful) {
                    val code = heartbeatResponse.code()
                    if (code == 401 || code == 403) {
                        Log.w(TAG, "Heartbeat rejected ($code); re-registering device...")
                        prefs.clearRegistration()
                        DeviceRegistration.register(appContext)
                        reRegistered = true
                        val retryHeartbeat = service.heartbeat()
                        if (!retryHeartbeat.isSuccessful) {
                            return@withLock DirectSyncResult.AuthError(retryHeartbeat.code())
                        }
                    } else if (code != 404 && code != 405 && code != 429) {
                        Log.w(TAG, "Heartbeat warning: HTTP $code")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Network unavailable during heartbeat: ${e.message}")
                return@withLock DirectSyncResult.NetworkError(e.message ?: "Network unreachable")
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat exception", e)
            }

            // Step 3: Try to connect Socket.IO for real-time push (with graceful fallback to HTTP)
            var socketClient: DeviceSocketClient? = null
            var socketConnected = false
            try {
                socketClient = DeviceSocketClient(appContext)
                socketConnected = socketClient.connect()
            } catch (e: Exception) {
                Log.w(TAG, "Socket.IO connection skipped or failed; using HTTP API", e)
            }

            var totalSynced = 0
            try {
                while (true) {
                    val pendingItems = db.queueDao().getUnsynced(BATCH_SIZE)
                    if (pendingItems.isEmpty()) {
                        prefs.saveLastSyncTime(System.currentTimeMillis())
                        return@withLock DirectSyncResult.Success(totalSynced)
                    }

                    var hasItemFailure = false
                    for (item in pendingItems) {
                        try {
                            when (item.type) {
                                "notification" -> {
                                    val payload = gson.fromJson(
                                        item.jsonPayload,
                                        NotificationPayload::class.java
                                    ) ?: throw JsonParseException("Null payload")
                                    val normalized = payload.copy(timestamp = normalizeTimestamp(payload.timestamp))

                                    // Try Socket.IO first if connected
                                    if (socketConnected && socketClient?.send("device:notification", normalized) == true) {
                                        db.queueDao().markSynced(item.id)
                                        totalSynced++
                                    } else {
                                        // Fall back to HTTP
                                        var resp = service.sendNotification(normalized)
                                        if ((resp.code() == 401 || resp.code() == 403) && !reRegistered) {
                                            prefs.clearRegistration()
                                            DeviceRegistration.register(appContext)
                                            reRegistered = true
                                            resp = service.sendNotification(normalized)
                                        }
                                        if (resp.isSuccessful) {
                                            db.queueDao().markSynced(item.id)
                                            totalSynced++
                                        } else {
                                            Log.e(TAG, "Notification send failed: HTTP ${resp.code()}")
                                            if (resp.code() == 401 || resp.code() == 403) {
                                                return@withLock DirectSyncResult.AuthError(resp.code())
                                            }
                                            recordItemFailure(db, item.id, item.failureCount)
                                            hasItemFailure = true
                                        }
                                    }
                                }
                                "sms" -> {
                                    val payload = gson.fromJson(
                                        item.jsonPayload,
                                        SmsPayload::class.java
                                    ) ?: throw JsonParseException("Null payload")
                                    val normalized = payload.copy(timestamp = normalizeTimestamp(payload.timestamp))

                                    // Try Socket.IO first if connected
                                    if (socketConnected && socketClient?.send("device:sms", normalized) == true) {
                                        db.queueDao().markSynced(item.id)
                                        totalSynced++
                                    } else {
                                        // Fall back to HTTP
                                        var resp = service.sendSms(normalized)
                                        if ((resp.code() == 401 || resp.code() == 403) && !reRegistered) {
                                            prefs.clearRegistration()
                                            DeviceRegistration.register(appContext)
                                            reRegistered = true
                                            resp = service.sendSms(normalized)
                                        }
                                        if (resp.isSuccessful) {
                                            db.queueDao().markSynced(item.id)
                                            totalSynced++
                                        } else {
                                            Log.e(TAG, "SMS send failed: HTTP ${resp.code()}")
                                            if (resp.code() == 401 || resp.code() == 403) {
                                                return@withLock DirectSyncResult.AuthError(resp.code())
                                            }
                                            recordItemFailure(db, item.id, item.failureCount)
                                            hasItemFailure = true
                                        }
                                    }
                                }
                                else -> {
                                    Log.w(TAG, "Deleting unknown queue item ${item.id}")
                                    db.queueDao().delete(item.id)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            Log.w(TAG, "Network disconnected while syncing items: ${e.message}")
                            recordItemFailure(db, item.id, item.failureCount)
                            db.queueDao().deleteSynced()
                            return@withLock DirectSyncResult.NetworkError("Network error: ${e.message}")
                        } catch (e: JsonParseException) {
                            Log.e(TAG, "Malformed item ${item.id}, discarding", e)
                            db.queueDao().delete(item.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing item ${item.id}", e)
                            recordItemFailure(db, item.id, item.failureCount)
                            hasItemFailure = true
                        }
                    }

                    db.queueDao().deleteSynced()
                    if (hasItemFailure) {
                        return@withLock DirectSyncResult.Error(
                            "One or more queued events could not be sent"
                        )
                    }
                }
            } finally {
                socketClient?.close()
                prefs.saveLastSyncTime(System.currentTimeMillis())
            }
            DirectSyncResult.Success(totalSynced)
        }
    }

    private suspend fun recordItemFailure(
        db: QueueDatabase,
        itemId: Int,
        previousFailureCount: Int
    ) {
        val updated = db.queueDao().incrementFailureCount(itemId)
        if (updated == 0) return

        val failureCount = previousFailureCount + 1
        if (failureCount >= MAX_ITEM_FAILURES) {
            db.queueDao().delete(itemId)
            Log.e(TAG, "Discarded queue item $itemId after $failureCount failed attempts")
        } else {
            Log.w(TAG, "Queue item $itemId failed ($failureCount/$MAX_ITEM_FAILURES)")
        }
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
            throw JsonParseException("Invalid timestamp: $timestamp", e)
        }

    }
}
