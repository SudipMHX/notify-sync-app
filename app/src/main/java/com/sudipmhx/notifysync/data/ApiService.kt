package com.sudipmhx.notifysync.data

import com.sudipmhx.notifysync.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/notification")
    suspend fun sendNotification(@Body body: NotificationPayload): Response<ResponseBody>

    @POST("api/sms")
    suspend fun sendSms(@Body body: SmsPayload): Response<ResponseBody>

    @POST("api/device/heartbeat")
    suspend fun heartbeat(): Response<ResponseBody>
}
