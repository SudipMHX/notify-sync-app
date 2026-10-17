package com.sudipmhx.notifysync.data

import android.content.Context
import com.sudipmhx.notifysync.BuildConfig
import com.sudipmhx.notifysync.util.PrefsManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val BASE_URL = BuildConfig.SERVER_URL

    @Volatile
    private var apiService: ApiService? = null

    fun getService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService(context.applicationContext).also {
                apiService = it
            }
        }
    }

    private fun buildService(context: Context): ApiService {
        val prefs = PrefsManager(context)

        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
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

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
