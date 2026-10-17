package com.sudipmhx.notifysync.data.model

data class SmsPayload(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simSlot: Int
)
