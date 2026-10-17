package com.sudipmhx.notifysync.data

import android.content.Context
import com.google.gson.Gson
import com.sudipmhx.notifysync.BuildConfig
import com.sudipmhx.notifysync.util.PrefsManager
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume

class DeviceSocketClient(context: Context) {
    private val socket: Socket
    private val gson = Gson()

    init {
        val apiKey = PrefsManager(context.applicationContext).getApiKey()
            ?: throw IllegalStateException("Device API key is not available")
        val options = IO.Options.builder()
            .setAuth(mapOf("apiKey" to apiKey))
            .setReconnection(false)
            .setTimeout(CONNECT_TIMEOUT_MS)
            .setForceNew(true)
            .build()
        socket = IO.socket(URI.create(BuildConfig.SERVER_URL), options)
    }

    suspend fun connect(): Boolean =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS + 1_000) {
            suspendCancellableCoroutine { continuation ->
                socket.once(Socket.EVENT_CONNECT) {
                    if (continuation.isActive) continuation.resume(true)
                }
                socket.once(Socket.EVENT_CONNECT_ERROR) {
                    if (continuation.isActive) continuation.resume(false)
                }
                continuation.invokeOnCancellation { close() }
                socket.connect()
            }
        } ?: false

    suspend fun send(event: String, payload: Any): Boolean {
        return try {
            withTimeoutOrNull(ACK_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val json = JSONObject(gson.toJson(payload))
                    socket.emit(
                        event,
                        json,
                        Ack { arguments ->
                            val response = arguments.firstOrNull() as? JSONObject
                            if (continuation.isActive) {
                                continuation.resume(response?.optBoolean("ok", false) == true)
                            }
                        }
                    )
                    continuation.invokeOnCancellation { close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } ?: false
    }

    fun close() {
        socket.disconnect()
        socket.close()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val ACK_TIMEOUT_MS = 15_000L
    }
}
