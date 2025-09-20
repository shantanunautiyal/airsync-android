package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WebSocketUtil {
    private const val TAG = "WebSocketUtil"
    private const val HANDSHAKE_TIMEOUT_MS = 7_000L
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var currentIpAddress: String? = null
    private var currentPort: Int? = null
    private var currentSymmetricKey: javax.crypto.SecretKey? = null
    private var isConnected = AtomicBoolean(false)
    private var isConnecting = AtomicBoolean(false)
    // Transport state: true after OkHttp onOpen, false after closing/failure/disconnect
    private var isSocketOpen = AtomicBoolean(false)
    private var handshakeCompleted = AtomicBoolean(false)
    private var handshakeTimeoutJob: Job? = null

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
        onMessage: ((String) -> Unit)? = null,
        // Distinguish between manual user triggered connections and auto reconnect attempts
        manualAttempt: Boolean = true,
        // Called if we don't receive an initial message from Mac within timeout (likely auth failure)
        onHandshakeTimeout: (() -> Unit)? = null
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
        handshakeCompleted.set(false)

        // Notify listeners that a manual connection attempt has begun so they can cancel auto-reconnect loops
        if (manualAttempt) {
            manualConnectListeners.forEach { listener ->
                try { listener() } catch (e: Exception) { Log.w(TAG, "ManualConnectListener error: ${e.message}") }
            }
        }
        currentIpAddress = ipAddress
        currentPort = port
        currentSymmetricKey = symmetricKey?.let { CryptoUtil.decodeKey(it) }
        onConnectionStatusChanged = onConnectionStatus
        onMessageReceived = onMessage

        // Reflect "Connecting..." immediately in the persistent notification
        updatePersistentNotification(context, isConnected = false, isConnecting = true)

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
                    // Transport is open now
                    isSocketOpen.set(true)
                    // Defer marking as connected until we get macInfo (handshake)
                    isConnected.set(false)
                    isConnecting.set(true)

                    // Trigger initial sync so Mac responds
                    try { SyncManager.performInitialSync(context) } catch (_: Exception) {}
                    updatePersistentNotification(context, isConnected = false, isConnecting = true)

                    // Start handshake timeout
                    handshakeTimeoutJob?.cancel()
                    handshakeTimeoutJob = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            delay(HANDSHAKE_TIMEOUT_MS)
                            if (!handshakeCompleted.get()) {
                                Log.w(TAG, "Handshake timed out; treating as authentication failure")
                                isConnected.set(false)
                                isConnecting.set(false)
                                try { webSocket.close(4001, "Handshake timeout") } catch (_: Exception) {}
                                // Treat as manual disconnect if this was a manual attempt
                                if (manualAttempt) {
                                    try {
                                        val ds = com.sameerasw.airsync.data.local.DataStoreManager(context)
                                        ds.setUserManuallyDisconnected(true)
                                    } catch (_: Exception) {}
                                }
                                onConnectionStatusChanged?.invoke(false)
                                updatePersistentNotification(context, isConnected = false, isConnecting = false)
                                notifyConnectionStatusListeners(false)
                                onHandshakeTimeout?.invoke()
                            }
                        } catch (_: Exception) {}
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received: $text")

                    val decryptedMessage = currentSymmetricKey?.let { key ->
                        CryptoUtil.decryptMessage(text, key)
                    } ?: text

                    // On first macInfo message, complete handshake and now report connected
                    if (!handshakeCompleted.get()) {
                        val handshakeOk = try {
                            val json = org.json.JSONObject(decryptedMessage)
                            json.optString("type") == "macInfo"
                        } catch (_: Exception) { false }
                        if (handshakeOk) {
                            handshakeCompleted.set(true)
                            isConnected.set(true)
                            isConnecting.set(false)
                            handshakeTimeoutJob?.cancel()
                            try { SyncManager.startPeriodicSync(context) } catch (_: Exception) {}
                            onConnectionStatusChanged?.invoke(true)
                            updatePersistentNotification(context, isConnected = true, isConnecting = false)
                            notifyConnectionStatusListeners(true)
                        }
                    }

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
                    isSocketOpen.set(false)
                    isConnecting.set(false)
                    handshakeCompleted.set(false)
                    handshakeTimeoutJob?.cancel()
                    onConnectionStatusChanged?.invoke(false)
                    updatePersistentNotification(context, isConnected = false, isConnecting = false)
                    // Clear continue browsing notifs on disconnect
                    try { NotificationUtil.clearContinueBrowsingNotifications(context) } catch (_: Exception) {}

                    // Notify listeners about the connection status
                    notifyConnectionStatusListeners(false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket connection failed: ${t.message}")
                    isConnected.set(false)
                    isConnecting.set(false)
                    isSocketOpen.set(false)
                    handshakeCompleted.set(false)
                    handshakeTimeoutJob?.cancel()

                    // Update connection status
                    onConnectionStatusChanged?.invoke(false)
                    updatePersistentNotification(context, isConnected = false, isConnecting = false)
                    // Clear continue browsing notifs on failure
                    try { NotificationUtil.clearContinueBrowsingNotifications(context) } catch (_: Exception) {}

                    // Notify listeners about the connection status
                    notifyConnectionStatusListeners(false)
                }
            }

            webSocket = client!!.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebSocket: ${e.message}")
            isConnecting.set(false)
            handshakeCompleted.set(false)
            handshakeTimeoutJob?.cancel()
            onConnectionStatusChanged?.invoke(false)
            updatePersistentNotification(context, isConnected = false, isConnecting = false)
            try { NotificationUtil.clearContinueBrowsingNotifications(context) } catch (_: Exception) {}
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
        // Allow sending as soon as the socket is open (even before handshake completes)
        return if (isSocketOpen.get() && webSocket != null) {
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

    fun disconnect(context: Context? = null) {
        Log.d(TAG, "Disconnecting WebSocket")
        isConnected.set(false)
        isConnecting.set(false)
        isSocketOpen.set(false)
        handshakeCompleted.set(false)
        handshakeTimeoutJob?.cancel()

        // Stop periodic sync when disconnecting
        SyncManager.stopPeriodicSync()

        webSocket?.close(1000, "Manual disconnection")
        webSocket = null
        onConnectionStatusChanged?.invoke(false)

        // Clear continue browsing notifications if possible
        context?.let {
            try { NotificationUtil.clearContinueBrowsingNotifications(it) } catch (_: Exception) {}
        }

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
        handshakeCompleted.set(false)
        handshakeTimeoutJob?.cancel()
    }

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    private fun updatePersistentNotification(context: Context, isConnected: Boolean, isConnecting: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ds = com.sameerasw.airsync.data.local.DataStoreManager(context)

                val lastDevice = ds.getLastConnectedDevice().first()
                val deviceName = lastDevice?.name

                val ourIp = com.sameerasw.airsync.utils.DeviceInfoUtil.getWifiIpAddress(context)
                val all = ds.getAllNetworkDeviceConnections().first()
                val hasReconnectTarget = if (ourIp != null && lastDevice != null) {
                    all.firstOrNull { it.deviceName == lastDevice.name && it.getClientIpForNetwork(ourIp) != null } != null
                } else false

                val autoEnabled = ds.getAutoReconnectEnabled().first()
                val manual = ds.getUserManuallyDisconnected().first()

                val shouldShow = !isConnected && autoEnabled && !manual && hasReconnectTarget
                if (shouldShow) {
                    NotificationUtil.showConnectionStatusNotification(
                        context = context,
                        deviceName = deviceName,
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        isAutoReconnecting = true,
                        hasReconnectTarget = hasReconnectTarget
                    )
                } else {
                    NotificationUtil.hideConnectionStatusNotification(context)
                }
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
