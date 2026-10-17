package com.sudipmhx.notifysync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import com.sudipmhx.notifysync.data.ImmediateSyncManager
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.local.QueueEntity
import com.sudipmhx.notifysync.data.model.SmsPayload
import com.sudipmhx.notifysync.worker.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                val firstMessage = messages.first()
                val entity = QueueEntity(
                    type = "sms",
                    jsonPayload = Gson().toJson(
                        SmsPayload(
                            sender = firstMessage.originatingAddress ?: "Unknown",
                            body = messages.joinToString(separator = "") { it.messageBody.orEmpty() },
                            timestamp = Instant.ofEpochMilli(firstMessage.timestampMillis).toString(),
                            simSlot = 0
                        )
                    )
                )
                QueueDatabase.getDatabase(appContext).queueDao().insert(entity)
                ImmediateSyncManager.triggerSync(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not queue received SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
