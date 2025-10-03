package com.sameerasw.airsync.utils

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object WallpaperHandler {
    private const val TAG = "WallpaperHandler"

    suspend fun sendWallpaper(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                if (!PermissionUtil.hasReadStoragePermission(context)) {
                    Log.w(TAG, "Missing storage permission to read wallpaper.")
                    sendWallpaperResponse(null, "Storage permission not granted.")
                    return@withContext
                }

                val wallpaperManager = WallpaperManager.getInstance(context)
                val wallpaperDrawable = wallpaperManager.drawable
                if (wallpaperDrawable is BitmapDrawable) {
                    val bitmap = wallpaperDrawable.bitmap
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64Wallpaper = Base64.encodeToString(byteArray, Base64.DEFAULT)

                    sendWallpaperResponse(base64Wallpaper, "Wallpaper sent successfully.")
                    Log.d(TAG, "Wallpaper sent successfully.")
                } else {
                    Log.e(TAG, "Wallpaper is not a bitmap drawable.")
                    sendWallpaperResponse(null, "Could not retrieve wallpaper as bitmap.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending wallpaper: ${e.message}", e)
                sendWallpaperResponse(null, "Error sending wallpaper: ${e.message}")
            }
        }
    }

    private fun sendWallpaperResponse(base64Wallpaper: String?, message: String) {
        val response = JSONObject().apply {
            put("type", "wallpaperResponse")
            val data = JSONObject().apply {
                put("success", base64Wallpaper != null)
                put("message", message)
                base64Wallpaper?.let { put("wallpaper", it) }
            }
            put("data", data)
        }
        WebSocketUtil.sendMessage(JsonUtil.toSingleLine(response.toString()))
    }
}
