package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sameerasw.airsync.domain.model.CallEvent
import com.sameerasw.airsync.domain.model.CallLogBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * CallSyncClient handles WebSocket and HTTP communication for call events.
 * - Real-time call events are sent via WebSocket when connected
 * - Batch call-log sync is sent via HTTP POST
 * - Implements exponential backoff for reconnection with local queuing
 */
class CallSyncClient(
    private val context: Context,
    private val serverBaseUrl: String,
    private val authToken: String
) : WebSocketListener() {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private val eventQueue = mutableListOf<CallEvent>()
    private var reconnectAttempts = 0

    /**
     * Connect to WebSocket for real-time call events
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (isConnecting || webSocket != null) return@withContext

        isConnecting = true
        try {
            val wsUrl = serverBaseUrl.replace("http", "ws")
            val request = Request.Builder()
                .url("$wsUrl/ws/calls")
                .header("Authorization", "Bearer $authToken")
                .header("User-Agent", "AirSync-Android/2.1")
                .build()

            Log.d(TAG, "Connecting to WebSocket: $wsUrl/ws/calls")
            webSocket = httpClient.newWebSocket(request, this@CallSyncClient)
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connection error", e)
            scheduleReconnect()
        } finally {
            isConnecting = false
        }
    }

    /**
     * Disconnect WebSocket
     */
    fun disconnect() {
        try {
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket", e)
        }
    }

    /**
     * Send a single real-time call event via WebSocket
     */
    suspend fun sendCallEvent(event: CallEvent): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (webSocket == null) {
                // Queue for later delivery
                eventQueue.add(event)
                return@withContext false
            }

            val json = gson.toJson(
                mapOf(
                    "type" to "call_event",
                    "data" to event
                )
            )

            webSocket?.send(json)
            Log.d(TAG, "Sent call event: ${event.direction} ${event.state}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call event", e)
            eventQueue.add(event) // Queue for retry
            false
        }
    }

    /**
     * Send batch of call-log events via HTTP POST
     */
    suspend fun sendCallLogBatch(
        events: List<CallEvent>,
        lastSyncTimestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext true

        return@withContext try {
            val batch = CallLogBatch(
                deviceId = "", // Will be populated by caller
                events = events,
                lastSyncTimestamp = lastSyncTimestamp
            )

            val json = gson.toJson(batch)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$serverBaseUrl/api/calls/sync")
                .post(requestBody)
                .header("Authorization", "Bearer $authToken")
                .header("User-Agent", "AirSync-Android/2.1")
                .build()

            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful

            Log.d(TAG, "Call log batch sync: ${response.code} (${events.size} events)")

            if (success) {
                reconnectAttempts = 0
            }

            response.close()
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call log batch", e)
            false
        }
    }

    // WebSocketListener callbacks

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket opened")
        reconnectAttempts = 0

        // Send any queued events
        val queued = eventQueue.toList()
        eventQueue.clear()
        queued.forEach { event ->
            try {
                val json = gson.toJson(
                    mapOf(
                        "type" to "call_event",
                        "data" to event
                    )
                )
                webSocket.send(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending queued event", e)
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "WebSocket message received: $text")
        // Handle messages from server (acknowledgments, commands, etc.)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closing: $code $reason")
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closed: $code $reason")
        this.webSocket = null
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket failure", t)
        this.webSocket = null
        scheduleReconnect()
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        // TODO: Implement with coroutine delay
        // For now, just log
        reconnectAttempts++
        val backoffMs = (2000L * Math.pow(1.5, reconnectAttempts.toDouble())).toLong()
        Log.d(TAG, "Scheduling reconnect in ${backoffMs}ms (attempt $reconnectAttempts)")
    }

    companion object {
        private const val TAG = "CallSyncClient"
        private var instance: CallSyncClient? = null

        fun getInstance(context: Context, serverUrl: String, token: String): CallSyncClient {
            return instance ?: CallSyncClient(context, serverUrl, token).also {
                instance = it
            }
        }
    }
}

