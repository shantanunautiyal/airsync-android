package com.sameerasw.airsync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface
import java.util.*

object DeviceInfoUtil {

    fun getDeviceName(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
                ?: Build.MODEL
                ?: "Android Device"
        } catch (_: Exception) {
            Build.MODEL ?: "Android Device"
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && hostAddress.contains(".") && !hostAddress.contains(":")) {
                            // IPv4 address
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {

        }
        return null
    }

    @Suppress("DEPRECATION")
    fun getWifiIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            if (ipAddress != 0) {
                String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            } else {
                getLocalIpAddress()
            }
        } catch (_: Exception) {
            getLocalIpAddress()
        }
    }
}
