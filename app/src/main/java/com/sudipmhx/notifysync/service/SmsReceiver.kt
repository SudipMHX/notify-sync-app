package com.sudipmhx.notifysync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.data.local.QueueEntity
import com.sudipmhx.notifysync.data.model.SmsPayload
import com.sudipmhx.notifysync.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val gson = Gson()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages.forEach { sms ->
                val payload = SmsPayload(
                    sender = sms.originatingAddress ?: "Unknown",
                    body = sms.messageBody ?: "",
                    timestamp = sms.timestampMillis,
                    simSlot = 0 // Basic single SIM handling for now
                )

                scope.launch {
                    val db = QueueDatabase.getDatabase(context.applicationContext)
                    db.queueDao().insert(
                        QueueEntity(
                            type = "sms",
                            jsonPayload = gson.toJson(payload)
                        )
                    )
                    triggerSync(context)
                }
            }
        }
    }

    private fun triggerSync(context: Context) {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(syncRequest)
    }
}
