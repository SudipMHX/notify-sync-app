package com.sudipmhx.notifysync.data.model

import com.google.gson.annotations.SerializedName

data class NotificationPayload(
    @SerializedName("packageName")
    val packageName: String,

    @SerializedName("appName")
    val appName: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("timestamp")
    val timestamp: String
)
