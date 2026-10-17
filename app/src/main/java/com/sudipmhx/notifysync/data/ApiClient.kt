package com.sudipmhx.notifysync.data

import android.content.Context
import com.sudipmhx.notifysync.util.PrefsManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://notify-sync-dashboard.onrender.com/"

    private var apiService: ApiService? = null

    fun getService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            val prefs = PrefsManager(context)
            
            val authInterceptor = Interceptor { chain ->
                val request = chain.request()
                // Check if it's NOT the register endpoint
                if (!request.url.encodedPath.endsWith("/api/register")) {
                    val apiKey = prefs.getApiKey()
                    if (apiKey != null) {
                        val newRequest = request.newBuilder()
                            .addHeader("x-api-key", apiKey)
                            .build()
                        return@Interceptor chain.proceed(newRequest)
                    }
                }
                chain.proceed(request)
            }

            val retryInterceptor = Interceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: SocketTimeoutException) {
                    // Retry once on timeout (Render cold start)
                    chain.proceed(request)
                }
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(retryInterceptor)
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            val service = retrofit.create(ApiService::class.java)
            apiService = service
            service
        }
    }
}
