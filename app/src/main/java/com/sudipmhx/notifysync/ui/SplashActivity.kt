package com.sudipmhx.notifysync.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sudipmhx.notifysync.R
import com.sudipmhx.notifysync.data.DeviceRegistration
import com.sudipmhx.notifysync.util.PrefsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val prefs = PrefsManager(this)

        if (DeviceRegistration.isRegistered(prefs)) {
            goNext()
            return
        }

        lifecycleScope.launch {
            try {
                DeviceRegistration.register(this@SplashActivity)
                goNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Device registration failed", e)
                goNext()
            }
        }
    }

    private fun goNext() {
        startActivity(Intent(this, PermissionActivity::class.java))
        finish()
    }

    companion object {
        private const val TAG = "SplashActivity"
    }
}
