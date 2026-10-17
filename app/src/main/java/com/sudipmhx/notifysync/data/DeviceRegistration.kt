package com.sudipmhx.notifysync.data

import android.content.Context
import android.os.Build
import com.sudipmhx.notifysync.data.model.RegisterRequest
import com.sudipmhx.notifysync.util.PrefsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DeviceRegistration {
    private val registrationMutex = Mutex()

    fun isRegistered(prefs: PrefsManager): Boolean =
        !prefs.getDeviceId().isNullOrBlank() && !prefs.getApiKey().isNullOrBlank()

    suspend fun register(context: Context) {
        val appContext = context.applicationContext
        registrationMutex.withLock {
            val prefs = PrefsManager(appContext)
            if (isRegistered(prefs)) return@withLock

            val response = ApiClient.getService(appContext).register(
                RegisterRequest(
                    deviceName = Build.MODEL.orEmpty().ifBlank { Build.DEVICE },
                    androidVersion = Build.VERSION.RELEASE.orEmpty()
                )
            )

            require(response.deviceId.isNotBlank() && response.apiKey.isNotBlank()) {
                "Registration response did not include a device ID and API key"
            }

            check(prefs.saveRegistration(response.deviceId, response.apiKey)) {
                "Could not save device registration"
            }
        }
    }
}
