package com.sudipmhx.notifysync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sudipmhx.notifysync.worker.SyncScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncScheduler.scheduleAfterBoot(context)
        }
    }
}
