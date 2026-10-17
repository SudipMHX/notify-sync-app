package com.sudipmhx.notifysync.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notify_sync_prefs", Context.MODE_PRIVATE)

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString("device_id", deviceId).apply()
    }

    fun getDeviceId(): String? {
        return prefs.getString("device_id", null)
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("api_key", apiKey).apply()
    }

    fun saveRegistration(deviceId: String, apiKey: String): Boolean =
        prefs.edit()
            .putString("device_id", deviceId)
            .putString("api_key", apiKey)
            .commit()

    fun getApiKey(): String? {
        return prefs.getString("api_key", null)
    }

    fun saveLastSyncTime(timestamp: Long) {
        prefs.edit().putLong("last_sync_time", timestamp).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong("last_sync_time", 0L)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearRegistration(): Boolean =
        prefs.edit()
            .remove("device_id")
            .remove("api_key")
            .commit()
}
