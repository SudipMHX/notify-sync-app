package com.sudipmhx.notifysync.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.lifecycle.lifecycleScope
import com.sudipmhx.notifysync.R
import com.sudipmhx.notifysync.data.DeviceRegistration
import com.sudipmhx.notifysync.data.local.QueueDatabase
import com.sudipmhx.notifysync.worker.SyncScheduler
import com.sudipmhx.notifysync.util.PrefsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var deviceIdText: TextView
    private lateinit var statusText: TextView
    private lateinit var connectionDetailText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var queueCountText: TextView
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)
        deviceIdText = findViewById(R.id.deviceIdText)
        statusText = findViewById(R.id.statusText)
        connectionDetailText = findViewById(R.id.connectionDetailText)
        lastSyncText = findViewById(R.id.lastSyncText)
        queueCountText = findViewById(R.id.queueCountText)
        refreshButton = findViewById(R.id.refreshButton)
        refreshButton.setOnClickListener { checkAndRefresh() }
        if (DeviceRegistration.isRegistered(prefs)) {
            SyncScheduler.enqueue(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val isRegistered = DeviceRegistration.isRegistered(prefs)
        statusText.setText(if (isRegistered) R.string.status_ready else R.string.status_setup_required)
        connectionDetailText.setText(
            when {
                !isRegistered -> R.string.status_setup_detail
                isOnline() -> R.string.status_online_detail
                else -> R.string.status_offline_detail
            }
        )

        deviceIdText.text = getString(
            R.string.device_id_format,
            prefs.getDeviceId() ?: getString(R.string.device_not_registered)
        )

        val lastSync = prefs.getLastSyncTime()
        lastSyncText.text = if (lastSync > 0L) {
            getString(R.string.last_sync_format, DateFormat.getDateTimeInstance().format(Date(lastSync)))
        } else {
            getString(R.string.last_sync_never)
        }

        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    QueueDatabase.getDatabase(applicationContext).queueDao().getUnsyncedCount()
                }
                queueCountText.text = when (count) {
                    0 -> getString(R.string.queue_empty)
                    1 -> getString(R.string.queue_count_singular, count)
                    else -> getString(R.string.queue_count_plural, count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                queueCountText.setText(R.string.queue_status_unavailable)
                android.util.Log.e(TAG, "Could not load pending sync count", e)
            }
        }
    }

    private fun checkAndRefresh() {
        refreshButton.isEnabled = false
        refreshButton.setText(R.string.status_checking)
        statusText.setText(R.string.status_checking)
        connectionDetailText.setText(R.string.status_checking_detail)

        lifecycleScope.launch {
            try {
                if (!isOnline()) {
                    statusText.setText(R.string.status_offline)
                    connectionDetailText.setText(R.string.status_offline_detail)
                    return@launch
                }

                if (!DeviceRegistration.isRegistered(prefs)) {
                    DeviceRegistration.register(this@MainActivity)
                }

                var syncState = SyncScheduler.syncNow(applicationContext)
                if (syncState == WorkInfo.State.FAILED && !DeviceRegistration.isRegistered(prefs)) {
                    DeviceRegistration.register(this@MainActivity)
                    syncState = SyncScheduler.syncNow(applicationContext)
                }
                refreshDashboard()
                if (syncState == WorkInfo.State.ENQUEUED) {
                    statusText.setText(R.string.status_sync_pending)
                    connectionDetailText.setText(R.string.status_sync_pending_detail)
                    return@launch
                }
                if (syncState != WorkInfo.State.SUCCEEDED) {
                    statusText.setText(R.string.status_check_failed)
                    connectionDetailText.setText(R.string.status_sync_failed_detail)
                    return@launch
                }

                connectionDetailText.setText(R.string.status_refresh_success_detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "Registration check failed with HTTP ${e.code()}", e)
                statusText.setText(R.string.status_check_failed)
                val serverMessage = readServerError(e)
                connectionDetailText.text = if (serverMessage == null) {
                    getString(R.string.status_server_error_format, e.code())
                } else {
                    getString(R.string.status_server_error_detail_format, e.code(), serverMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh device status", e)
                statusText.setText(R.string.status_check_failed)
                connectionDetailText.setText(R.string.status_check_failed_detail)
            } finally {
                refreshButton.isEnabled = true
                refreshButton.setText(R.string.refresh_status)
            }
        }
    }

    private fun readServerError(exception: HttpException): String? {
        return try {
            exception.response()?.errorBody()?.use { body ->
                body.string().trim().take(MAX_SERVER_ERROR_LENGTH).ifBlank { null }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read registration error response", e)
            null
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MAX_SERVER_ERROR_LENGTH = 300
    }
}
