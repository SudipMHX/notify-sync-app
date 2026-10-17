package com.sudipmhx.notifysync.data

import com.sudipmhx.notifysync.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/notification")
    suspend fun sendNotification(@Body body: NotificationPayload): Response<Unit>

    @POST("api/sms")
    suspend fun sendSms(@Body body: SmsPayload): Response<Unit>
}
