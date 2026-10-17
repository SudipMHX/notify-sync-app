package com.sudipmhx.notifysync.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("deviceName")
    val deviceName: String,

    @SerializedName("androidVersion")
    val androidVersion: String
)
