package com.sudipmhx.notifysync.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sudipmhx.notifysync.data.ApiClient
import com.sudipmhx.notifysync.data.model.RegisterRequest
import com.sudipmhx.notifysync.util.PrefsManager
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout needed for splash, or use a simple one
        val prefs = PrefsManager(this)

        if (prefs.getApiKey() != null) {
            goNext()
            return
        }

        val deviceName = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE

        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(this@SplashActivity).register(
                    RegisterRequest(deviceName, androidVersion)
                )
                prefs.saveDeviceId(res.deviceId)
                prefs.saveApiKey(res.apiKey)
                goNext()
            } catch (e: Exception) {
                e.printStackTrace()
                // If registration fails (e.g. no internet or server down), 
                // we still want the user to be able to open the app.
                goNext()
            }
        }
    }

    private fun goNext() {
        startActivity(Intent(this, PermissionActivity::class.java))
        finish()
    }
}
