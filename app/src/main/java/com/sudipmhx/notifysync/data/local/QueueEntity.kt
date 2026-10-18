package com.sudipmhx.notifysync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,        // "notification" or "sms"
    val jsonPayload: String, // serialized payload
    val synced: Boolean = false,
    val failureCount: Int = 0
)
