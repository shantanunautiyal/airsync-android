package com.sameerasw.airsync.utils

import androidx.annotation.DrawableRes
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.ConnectedDevice

/**
 * Maps a connected Mac device to a large preview image under the main UI.
 * Uses the same classification rules we use for icons:
 * - iMac -> imac.png
 * - Mac mini -> ic_device_macmini.png
 * - Mac Studio -> ic_device_macstudio.png
 * - Mac Pro -> ic_device_macpro.png
 * - MacBook (Air/Pro) -> ic_device_macbook.png
 * - Fallback -> ic_device_macbook.png
 */
object DevicePreviewResolver {

    @DrawableRes
    fun getPreviewRes(device: ConnectedDevice?): Int {
        if (device == null) return R.drawable.ic_device_macbook
        val hay = buildString {
            if (!device.name.isNullOrBlank()) append(device.name).append(' ')
            device.model?.let { if (it.isNotBlank()) append(it).append(' ') }
            device.deviceType?.let { if (it.isNotBlank()) append(it) }
        }.trim().lowercase()

        return when {
            hay.contains("imac") -> R.drawable.ic_device_imac
            hay.contains("mac mini") || hay.contains("macmini") -> R.drawable.ic_device_macmini
            hay.contains("mac studio") -> R.drawable.ic_device_macstudio
            // Ensure "mac pro" (desktop) before generic "pro" in MacBook Pro names
            hay.contains("mac pro") -> R.drawable.ic_device_macpro
            hay.contains("macbook") -> R.drawable.ic_device_macbook
            else -> R.drawable.ic_device_macbook
        }
    }
}
