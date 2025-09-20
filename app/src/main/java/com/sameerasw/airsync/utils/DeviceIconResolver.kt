package com.sameerasw.airsync.utils

import android.content.Context
import androidx.annotation.DrawableRes
import com.sameerasw.airsync.R
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.ConnectedDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Resolves which icon to use for a given connected Mac device.
 * Rules (for now):
 * - MacBook Air/Pro -> use existing laptop icon
 * - iMac -> desktop/monitor icon
 * - Mac mini -> small box icon
 * - Mac Studio -> medium tower icon
 * - Mac Pro -> tall tower icon
 * - Fallback -> laptop icon
 *
 * We only check the top category name (MacBook Air, MacBook Pro, iMac, Mac mini, Mac Studio, Mac Pro)
 * and we match case-insensitively against deviceType/model/name.
 */
object DeviceIconResolver {

    @DrawableRes
    fun getIconRes(device: ConnectedDevice?): Int {
        if (device == null) return R.drawable.ic_laptop_24

        val name = device.name
        val model = device.model
        val type = device.deviceType

        return getIconResForName(name, model, type)
    }

    @DrawableRes
    fun getIconResForName(name: String?, model: String?, deviceType: String?): Int {
        val hay = buildString {
            if (!name.isNullOrBlank()) append(name).append(' ')
            if (!model.isNullOrBlank()) append(model).append(' ')
            if (!deviceType.isNullOrBlank()) append(deviceType)
        }.trim().lowercase()

        // Check top categories by substring
        return when {
            hay.contains("imac") -> R.drawable.ic_desktop_24
            hay.contains("mac mini") || hay.contains("macmini") -> R.drawable.ic_mac_mini_24
            hay.contains("mac studio") -> R.drawable.ic_mac_studio_24
            // Ensure "mac pro" (desktop) is checked before generic "pro" in MacBook Pro names
            hay.contains("mac pro") -> R.drawable.ic_mac_pro_24
            // MacBook covers both Air and Pro; we keep existing laptop icon for both
            hay.contains("macbook") -> R.drawable.ic_laptop_24
            else -> R.drawable.ic_laptop_24
        }
    }

    /** Convenience for places without direct device: read last device once. */
    @DrawableRes
    fun getLastDeviceIconRes(context: Context): Int = runBlocking {
        return@runBlocking try {
            val ds = DataStoreManager(context)
            val last = ds.getLastConnectedDevice().first()
            getIconRes(last)
        } catch (_: Exception) {
            R.drawable.ic_laptop_24
        }
    }
}
