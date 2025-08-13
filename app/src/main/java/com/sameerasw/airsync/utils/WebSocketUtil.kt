package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WebSocketUtil {
    private const val TAG = "WebSocketUtil"
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var currentIpAddress: String? = null
    private var currentPort: Int? = null
    private var currentSymmetricKey: javax.crypto.SecretKey? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)

    // Callback for connection status changes
    private var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    private var onMessageReceived: ((String) -> Unit)? = null

    // Global connection status listeners for UI updates
    private val connectionStatusListeners = mutableSetOf<(Boolean) -> Unit>()

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Keep connection alive
            .pingInterval(30, TimeUnit.SECONDS) // Send ping every 30 seconds
            .build()
    }

    fun connect(
        context: Context,
        ipAddress: String,
        port: Int,
        symmetricKey: String?,
        onConnectionStatus: ((Boolean) -> Unit)? = null,
        onMessage: ((String) -> Unit)? = null
    ) {
        if (isConnecting.get() || isConnected.get()) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        // Validate local network IP
        if (!isLocalNetwork(ipAddress)) {
            Log.e(TAG, "Invalid IP address: $ipAddress. Only local network addresses are allowed.")
            onConnectionStatus?.invoke(false)
            return
        }

        isConnecting.set(true)
        currentIpAddress = ipAddress
        currentPort = port
        currentSymmetricKey = symmetricKey?.let { CryptoUtil.decodeKey(it) }
        onConnectionStatusChanged = onConnectionStatus
        onMessageReceived = onMessage

        try {
            if (client == null) {
                client = createClient()
            }

            // Always use ws:// for local network
            val url = "ws://$ipAddress:$port/socket"

            Log.d(TAG, "Connecting to $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected to $url")
                    isConnected.set(true)
                    isConnecting.set(false)

                    // Perform initial sync sequence
                    SyncManager.performInitialSync(context)

                    // Start periodic sync for battery and status updates
                    SyncManager.startPeriodicSync(context)

                    // Update connection status
                    onConnectionStatusChanged?.invoke(true)
                    updatePersistentNotification(context, true)

                    // Notify all registered listeners about the connection status
                    notifyConnectionStatusListeners(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received: $text")

                    val decryptedMessage = currentSymmetricKey?.let { key ->
                        CryptoUtil.decryptMessage(text, key)
                    } ?: text

                    // Handle incoming commands
                    WebSocketMessageHandler.handleIncomingMessage(context, decryptedMessage)

                    // Update last sync time on successful response
                    updateLastSyncTime(context)

                    // Notify listeners
                    onMessageReceived?.invoke(decryptedMessage)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code / $reason")
                    isConnected.set(false)
                    onConnectionStatusChanged?.invoke(false)
                    updatePersistentNotification(context, false)

                    // Notify listeners about the connection status
                    notifyConnectionStatusListeners(false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket connection failed: ${t.message}")
                    isConnected.set(false)
                    isConnecting.set(false)

                    // Update connection status
                    onConnectionStatusChanged?.invoke(false)
                    updatePersistentNotification(context, false)

                    // Notify listeners about the connection status
                    notifyConnectionStatusListeners(false)
                }
            }

            webSocket = client!!.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebSocket: ${e.message}")
            isConnecting.set(false)
            onConnectionStatusChanged?.invoke(false)
        }
    }

    private fun isLocalNetwork(ipAddress: String): Boolean {
        return ipAddress.startsWith("192.168.") ||
                ipAddress.startsWith("10.") ||
                ipAddress.startsWith("172.16.") ||
                ipAddress.startsWith("172.17.") ||
                ipAddress.startsWith("172.18.") ||
                ipAddress.startsWith("172.19.") ||
                ipAddress.startsWith("172.2") ||
                ipAddress.startsWith("172.30.") ||
                ipAddress.startsWith("172.31.") ||
                ipAddress == "127.0.0.1" ||
                ipAddress == "localhost"
    }

    fun sendMessage(message: String): Boolean {
        return if (isConnected.get() && webSocket != null) {
            Log.d(TAG, "Sending message: $message")
            val messageToSend = currentSymmetricKey?.let { key ->
                CryptoUtil.encryptMessage(message, key)
            } ?: message

            webSocket!!.send(messageToSend)
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send message")
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        isConnected.set(false)
        isConnecting.set(false)

        // Stop periodic sync when disconnecting
        SyncManager.stopPeriodicSync()

        webSocket?.close(1000, "Manual disconnection")
        webSocket = null
        onConnectionStatusChanged?.invoke(false)

        // Notify listeners about the disconnection
        notifyConnectionStatusListeners(false)
    }

    fun cleanup() {
        disconnect()

        // Reset sync manager state
        SyncManager.reset()

        client?.dispatcher?.executorService?.shutdown()
        client = null
        currentIpAddress = null
        currentPort = null
        currentSymmetricKey = null
        onConnectionStatusChanged = null
        onMessageReceived = null
    }

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    private fun updatePersistentNotification(context: Context, isConnected: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStoreManager = com.sameerasw.airsync.data.local.DataStoreManager(context)
                val isSyncEnabled = dataStoreManager.getNotificationSyncEnabled().first()

                if (!isSyncEnabled) {
                    NotificationUtil.hideConnectionStatusNotification(context)
                    return@launch
                }

                val connectedDevice = dataStoreManager.getLastConnectedDevice().first()
                val lastSyncTime = dataStoreManager.getLastSyncTime().first()

                NotificationUtil.showConnectionStatusNotification(
                    context = context,
                    connectedDevice = connectedDevice,
                    lastSyncTime = lastSyncTime,
                    isConnected = isConnected
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating persistent notification: ${e.message}")
            }
        }
    }

    private fun updateLastSyncTime(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStoreManager = com.sameerasw.airsync.data.local.DataStoreManager(context)
                dataStoreManager.updateLastSyncTime(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error updating last sync time: ${e.message}")
            }
        }
    }

    // Register a global connection status listener
    fun registerConnectionStatusListener(listener: (Boolean) -> Unit) {
        connectionStatusListeners.add(listener)
    }

    // Unregister a global connection status listener
    fun unregisterConnectionStatusListener(listener: (Boolean) -> Unit) {
        connectionStatusListeners.remove(listener)
    }

    // Notify listeners about the connection status
    private fun notifyConnectionStatusListeners(isConnected: Boolean) {
        connectionStatusListeners.forEach { listener ->
            listener(isConnected)
        }
    }
}
