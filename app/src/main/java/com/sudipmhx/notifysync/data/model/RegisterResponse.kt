package com.sudipmhx.notifysync.data.model

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName(value = "deviceId", alternate = ["device_id"])
    val deviceId: String,

    @SerializedName(value = "apiKey", alternate = ["api_key"])
    val apiKey: String
)
