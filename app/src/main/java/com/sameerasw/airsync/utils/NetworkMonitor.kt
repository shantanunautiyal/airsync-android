package com.sameerasw.airsync.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NetworkInfo(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val ipAddress: String?
)

object NetworkMonitor {
    private const val TAG = "NetworkMonitor"

    /**
     * Monitor network changes and emit new network information
     */
    fun observeNetworkChanges(context: Context): Flow<NetworkInfo> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun getCurrentNetworkInfo(): NetworkInfo {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            val isConnected = networkCapabilities != null &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val ipAddress = if (isWifi) DeviceInfoUtil.getWifiIpAddress(context) else null

            return NetworkInfo(isConnected, isWifi, ipAddress)
        }

        // Send initial state
        trySend(getCurrentNetworkInfo())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use NetworkCallback for newer versions
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    trySend(getCurrentNetworkInfo())
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                    trySend(getCurrentNetworkInfo())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network capabilities changed: $network")
                    trySend(getCurrentNetworkInfo())
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        } else {
            // Fallback to BroadcastReceiver for older versions
            val networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.d(TAG, "Network broadcast received: ${intent.action}")
                    trySend(getCurrentNetworkInfo())
                }
            }

            val intentFilter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }

            context.registerReceiver(networkReceiver, intentFilter)

            awaitClose {
                context.unregisterReceiver(networkReceiver)
            }
        }
    }.distinctUntilChanged()

    /**
     * Check if we're currently connected to Wi-Fi
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /**
     * Get current Wi-Fi IP address if connected
     */
    fun getCurrentWifiIp(context: Context): String? {
        return if (isWifiConnected(context)) {
            DeviceInfoUtil.getWifiIpAddress(context)
        } else {
            null
        }
    }
}
