package com.sameerasw.airsync.utils

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sameerasw.airsync.R
import com.sameerasw.airsync.service.AirSyncTileService

object QuickSettingsUtil {
    fun isQSTileAdded(context: Context): Boolean {
        return try {
            val tiles = Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles") ?: ""
            tiles.contains("AirSyncTileService") || tiles.contains("com.sameerasw.airsync/.service.AirSyncTileService")
        } catch (_: Exception) {
            false
        }
    }

    fun requestAddQuickSettingsTile(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            val componentName = ComponentName(context, AirSyncTileService::class.java)
            val iconRes = DeviceIconResolver.getLastDeviceIconRes(context)
            val icon = Icon.createWithResource(context, iconRes)

            statusBarManager?.requestAddTileService(
                componentName,
                context.getString(R.string.app_name),
                icon,
                ContextCompat.getMainExecutor(context)
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                        Toast.makeText(context, "Quick Settings tile added", Toast.LENGTH_SHORT).show()
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        Toast.makeText(context, "Tile is already in Quick Settings", Toast.LENGTH_SHORT).show()
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> {
                    }
                }
            }
        } else {
            // Fallback instruction for older Android versions
            Toast.makeText(
                context,
                "Open Quick Settings, tap the edit button, and drag AirSync into the active tiles.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

