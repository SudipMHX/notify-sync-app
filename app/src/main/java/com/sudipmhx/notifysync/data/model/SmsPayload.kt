package com.sudipmhx.notifysync.data.model

import com.google.gson.annotations.SerializedName

data class SmsPayload(
    @SerializedName("sender")
    val sender: String,

    @SerializedName("body")
    val body: String,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("simSlot")
    val simSlot: Int
)
