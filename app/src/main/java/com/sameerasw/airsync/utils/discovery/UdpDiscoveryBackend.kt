package com.sameerasw.airsync.utils.discovery

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.DiscoveredDevice
import com.sameerasw.airsync.utils.DiscoveryMode
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.WakeupHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscoveryBackend : DiscoveryBackend {
    companion object {
        private const val TAG = "UdpDiscoveryBackend"
        private const val BROADCAST_PORT = 8889
        private const val PRUNE_INTERVAL_MS = 10000L
        private const val DEVICE_TIMEOUT_MS = 25000L
    }

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var socket: DatagramSocket? = null
    private var listeningJob: Job? = null
    private var broadcastJob: Job? = null
    private var pruningJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var currentMode = DiscoveryMode.ACTIVE

    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    private fun acquireMulticastLock(context: Context) {
        try {
            if (multicastLock == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                multicastLock = wm.createMulticastLock("AirSync:UdpDiscoveryLock")
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
                Log.d(TAG, "MulticastLock acquired")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "MulticastLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release MulticastLock: ${e.message}")
        }
    }

    override fun start(context: Context) {
        if (!PermissionUtil.isLocalNetworkPermissionGranted(context)) {
            Log.d(TAG, "Skipping UDP Discovery Backend start: local network permission not granted")
            return
        }
        if (isRunning) {
            updateBroadcastingState(context)
            return
        }

        isRunning = true
        Log.d(TAG, "Starting UDP Discovery Backend (Mode: $currentMode)")

        acquireMulticastLock(context)
        startListening(context)
        updateBroadcastingState(context)
        startPruning()
    }

    override fun stop(context: Context) {
        Log.d(TAG, "Stopping UDP Discovery Backend")
        if (isRunning && currentMode == DiscoveryMode.ACTIVE) {
            broadcastGoodbye(context)
        }
        isRunning = false
        listeningJob?.cancel()
        broadcastJob?.cancel()
        pruningJob?.cancel()

        releaseMulticastLock()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        _discoveredDevices.value = emptyList()
    }

    override fun setMode(mode: DiscoveryMode) {
        if (currentMode == mode) return
        Log.d(TAG, "Changing discovery mode to: $mode")
        currentMode = mode
    }

    // A customized setMode with context to update broadcasting state
    fun setModeWithContext(context: Context, mode: DiscoveryMode) {
        if (currentMode == mode) return
        Log.d(TAG, "Changing discovery mode to: $mode")
        currentMode = mode
        if (isRunning) {
            updateBroadcastingState(context)
        }
    }

    private fun updateBroadcastingState(context: Context) {
        broadcastJob?.cancel()

        if (!PermissionUtil.isLocalNetworkPermissionGranted(context)) {
            Log.d(TAG, "Skipping broadcasting state update: local network permission not granted")
            return
        }

        if (currentMode == DiscoveryMode.ACTIVE) {
            acquireMulticastLock(context)
            startBroadcasting(context)
        } else {
            Log.d(TAG, "Switched to PASSIVE discovery (listening only)")
            acquireMulticastLock(context)
        }
    }

    fun refreshSocket() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Refreshing UDP discovery socket due to network change")
                socket?.close()
                socket = null
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing socket: ${e.message}")
            }
        }
    }

    private fun startListening(context: Context) {
        val appContext = context.applicationContext
        listeningJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            while (isRunning) {
                try {
                    if (socket == null || socket!!.isClosed) {
                        Log.d(TAG, "Creating new DatagramSocket on port $BROADCAST_PORT")
                        socket = DatagramSocket(BROADCAST_PORT).apply {
                            broadcast = true
                            reuseAddress = true
                            soTimeout = 0
                        }
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val jsonString = String(packet.data, 0, packet.length)
                    handleIncomingTraffic(appContext, jsonString, packet.address.hostAddress)
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error receiving packet: ${e.message}, recreating socket...")
                        try {
                            socket?.close()
                        } catch (_: Exception) {
                        }
                        socket = null
                        delay(2000)
                    }
                }
            }
        }
    }

    private fun handleIncomingTraffic(context: Context, message: String, sourceIp: String?) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")

            when (type) {
                "presence" -> {
                    val deviceType = json.optString("deviceType")
                    if (deviceType == "mac") {
                        handlePresenceMessage(context, json, sourceIp)

                        if (currentMode == DiscoveryMode.PASSIVE) {
                            CoroutineScope(Dispatchers.IO).launch {
                                broadcastPresence(context)
                            }
                        }
                    }
                }

                "bye" -> {
                    val deviceType = json.optString("deviceType")
                    if (deviceType == "mac") {
                        val id = json.optString("id")
                        val currentList = _discoveredDevices.value.filter { it.id != id }
                        _discoveredDevices.value = currentList
                    }
                }

                "wakeUpRequest" -> {
                    val data = if (json.has("data")) json.getJSONObject("data") else json
                    val macIp = data.optString("macIP", data.optString("macIp", ""))
                    val macPort = data.optInt("macPort", 6996)
                    val macName = data.optString("macName", "Mac")

                    CoroutineScope(Dispatchers.IO).launch {
                        WakeupHandler.processWakeupRequest(context, macIp, macPort, macName)
                    }
                }
            }
        } catch (e: Exception) {
            // Silence parsing exceptions
        }
    }

    private fun handlePresenceMessage(context: Context, json: JSONObject, sourceIp: String?) {
        try {
            val id = json.optString("id")
            val name = json.optString("name")
            val port = json.optInt("port", 6996)
            val deviceType = json.optString("deviceType")

            val incomingIps = mutableSetOf<String>()
            val ipsArray = json.optJSONArray("ips")
            if (ipsArray != null) {
                for (i in 0 until ipsArray.length()) {
                    incomingIps.add(ipsArray.getString(i))
                }
            } else {
                val singleIp = json.optString("ip")
                if (singleIp.isNotEmpty()) incomingIps.add(singleIp)
                else if (sourceIp != null) incomingIps.add(sourceIp)
            }

            val ds = DataStoreManager.getInstance(context)
            val expandNetworkingEnabled = runBlocking { ds.getExpandNetworkingEnabled().first() }

            val validIps = incomingIps.filter { ip ->
                if (ip.startsWith("100.")) {
                    if (expandNetworkingEnabled) return@filter true
                    val myIps = getAllIpAddresses()
                    myIps.any { it.startsWith("100.") }
                } else true
            }.toSet()

            if (validIps.isEmpty()) return

            val device = DiscoveredDevice(
                id = id,
                name = name,
                ips = validIps,
                port = port,
                type = deviceType,
                lastSeen = System.currentTimeMillis(),
                discoverySource = DiscoverySource.UDP
            )

            updateDeviceList(device, validIps)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing discovery message: ${e.message}")
        }
    }

    private fun updateDeviceList(device: DiscoveredDevice, newIps: Set<String>) {
        val currentList = _discoveredDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == device.id }

        if (index != -1) {
            val existing = currentList[index]
            val updatedIps = existing.ips.toMutableSet()
            updatedIps.addAll(newIps)
            currentList[index] = existing.copy(
                ips = updatedIps,
                lastSeen = System.currentTimeMillis(),
                name = device.name,
                discoverySource = DiscoverySource.UDP
            )
        } else {
            currentList.add(device)
        }
        _discoveredDevices.value = currentList
    }

    private fun startBroadcasting(context: Context) {
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting Active Broadcast Loop")
            while (isRunning && currentMode == DiscoveryMode.ACTIVE) {
                broadcastPresence(context)
                delay(10000)
            }
        }
    }

    override fun broadcastPresence(context: Context) {
        val allIps = getAllIpAddresses()
        if (allIps.isEmpty()) {
            return
        }

        val ds = DataStoreManager.getInstance(context)
        val customName = try {
            runBlocking { ds.getDeviceName().first() }
        } catch (e: Exception) {
            ""
        }

        val deviceName = if (customName.isNotBlank()) customName else android.os.Build.MODEL

        val knownTargetIps = try {
            val connections = runBlocking {
                ds.getAllNetworkDeviceConnections().first()
            }
            connections.flatMap { connection ->
                connection.networkConnections.values
            }.toSet()
        } catch (e: Exception) {
            emptySet<String>()
        }

        val expandNetworkingEnabled = try {
            runBlocking { ds.getExpandNetworkingEnabled().first() }
        } catch (e: Exception) {
            true
        }

        val filteredLocalIps = if (expandNetworkingEnabled) allIps else allIps.filter { !it.startsWith("100.") }
        if (filteredLocalIps.isEmpty()) return

        val deviceId = DeviceInfoUtil.getDeviceId(context)

        val json = JSONObject()
        json.put("type", "presence")
        json.put("deviceType", "android")
        json.put("id", deviceId)
        json.put("name", deviceName)
        json.put("ips", FilteredIpArray(filteredLocalIps))
        val payload = json.toString()
        val data = payload.toByteArray()

        for (bindIp in filteredLocalIps) {
            try {
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName("255.255.255.255"),
                    BROADCAST_PORT
                )
                DatagramSocket(0, InetAddress.getByName(bindIp)).use { sender ->
                    sender.broadcast = true
                    sender.send(packet)
                }
            } catch (e: Exception) {
                // Ignore failure
            }
        }

        if (knownTargetIps.isNotEmpty()) {
            for (targetIp in knownTargetIps) {
                if (allIps.contains(targetIp)) continue
                if (!expandNetworkingEnabled && targetIp.startsWith("100.")) continue
                sendUnicast(targetIp, payload)
            }
        }
    }

    private fun broadcastGoodbye(context: Context) {
        val allIps = getAllIpAddresses()
        if (allIps.isEmpty()) return

        val deviceId = DeviceInfoUtil.getDeviceId(context)

        val json = JSONObject()
        json.put("type", "bye")
        json.put("deviceType", "android")
        json.put("id", deviceId)
        val payload = json.toString()
        val data = payload.toByteArray()

        CoroutineScope(Dispatchers.IO).launch {
            repeat(3) {
                for (bindIp in allIps) {
                    try {
                        val packet = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("255.255.255.255"),
                            BROADCAST_PORT
                        )
                        DatagramSocket(0, InetAddress.getByName(bindIp)).use { sender ->
                            sender.broadcast = true
                            sender.send(packet)
                        }
                    } catch (e: Exception) {
                        // Ignore failure
                    }
                }
                delay(100)
            }
        }
    }

    private fun sendUnicast(targetIp: String, message: String) {
        try {
            val data = message.toByteArray()
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName(targetIp),
                BROADCAST_PORT
            )

            DatagramSocket().use { sender ->
                sender.send(packet)
            }
        } catch (e: Exception) {
            // Ignore failure
        }
    }

    private fun FilteredIpArray(ips: List<String>): org.json.JSONArray {
        val array = org.json.JSONArray()
        ips.forEach { array.put(it) }
        return array
    }

    fun getAllIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val name = networkInterface.name.lowercase()
                if (name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") || name.contains("ppp")) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is java.net.Inet4Address) {
                        ips.add(address.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces: ${e.message}")
        }
        return ips
    }

    private fun startPruning() {
        pruningJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                delay(PRUNE_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val active = _discoveredDevices.value.filter { now - it.lastSeen < DEVICE_TIMEOUT_MS }
                if (active.size != _discoveredDevices.value.size) {
                    _discoveredDevices.value = active
                }
            }
        }
    }
}
