package com.sudipmhx.notifysync.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sudipmhx.notifysync.R

class PermissionActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        when {
            !hasSmsPermission() -> {
                statusText.text = "NotifySync needs SMS permission to capture incoming messages."
                actionButton.text = "Grant SMS Permission"
                actionButton.setOnClickListener { requestSmsPermission() }
            }
            !hasNotificationAccess() -> {
                statusText.text = "NotifySync needs Notification Access to capture incoming notifications."
                actionButton.text = "Grant Notification Access"
                actionButton.setOnClickListener { requestNotificationAccess() }
            }
            !isIgnoringBatteryOptimizations() -> {
                statusText.text = "NotifySync needs to run in the background to sync data reliably."
                actionButton.text = "Ignore Battery Optimizations"
                actionButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
            }
            else -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)
    }

    private fun hasNotificationAccess(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun requestNotificationAccess() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
