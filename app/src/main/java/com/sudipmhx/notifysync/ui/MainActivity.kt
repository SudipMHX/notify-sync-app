package com.sudipmhx.notifysync.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sudipmhx.notifysync.R
import com.sudipmhx.notifysync.util.PrefsManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = PrefsManager(this)
        val deviceIdText: TextView = findViewById(R.id.deviceIdText)
        val statusText: TextView = findViewById(R.id.statusText)
        val lastSyncText: TextView = findViewById(R.id.lastSyncText)

        deviceIdText.text = "Device ID: ${prefs.getDeviceId() ?: "Not Registered"}"
        statusText.text = if (prefs.getApiKey() != null) "Status: Connected ✅" else "Status: Not Connected ❌"
        
        val lastSync = prefs.getLastSyncTime()
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            lastSyncText.text = "Last Sync: ${sdf.format(Date(lastSync))}"
        } else {
            lastSyncText.text = "Last Sync: Never"
        }
    }
}
