package com.sameerasw.airsync.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

object HealthBleManager {
    private const val TAG = "HealthBleManager"
    private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEAS_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null

    fun startScanAndConnect(context: Context) {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return
            val scanner = adapter.bluetoothLeScanner ?: return

            scanner.startScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    // For now auto-connect to first device advertising HR service
                    device.connectGatt(context, false, gattCallback)
                    scanner.stopScan(this)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "startScan error: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                g?.discoverServices()
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                g?.close()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            try {
                val svc = g?.getService(HR_SERVICE_UUID)
                val ch = svc?.getCharacteristic(HR_MEAS_UUID)
                if (ch != null) {
                    g.setCharacteristicNotification(ch, true)
                    gatt = g
                }
            } catch (e: Exception) {
                Log.e(TAG, "Service discovery error: ${e.message}")
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            try {
                if (characteristic == null) return
                if (characteristic.uuid == HR_MEAS_UUID) {
                    val data = characteristic.value
                    val heartRate = parseHeartRate(data)
                    sendHeartRate(heartRate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Characteristic changed error: ${e.message}")
            }
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return -1
        val flags = data[0].toInt()
        val formatUInt16 = (flags and 0x01) != 0
        return if (formatUInt16 && data.size >= 3) {
            ((data[2].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
        } else if (data.size >= 2) {
            data[1].toInt() and 0xff
        } else -1
    }

    private fun sendHeartRate(hr: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val out = JSONObject()
                out.put("type", "healthUpdate")
                val data = JSONObject()
                data.put("source", "ble_watch")
                data.put("heartRate", hr)
                data.put("timestamp", System.currentTimeMillis() / 1000L)
                out.put("data", data)
                WebSocketUtil.sendMessage(JsonUtil.toSingleLine(out.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send heart rate: ${e.message}")
            }
        }
    }
}
