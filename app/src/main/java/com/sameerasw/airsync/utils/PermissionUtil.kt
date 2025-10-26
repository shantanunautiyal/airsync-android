package com.sameerasw.airsync.utils

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.sameerasw.airsync.service.InputAccessibilityService
import com.sameerasw.airsync.service.MediaNotificationListener
import androidx.core.net.toUri

object PermissionUtil {

    const val ACCESSIBILITY_SERVICE = "Accessibility Service"
    const val NOTIFICATION_ACCESS = "Notification Access"
    const val BACKGROUND_APP_USAGE = "Background App Usage"

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(componentName.flattenToString())
    }

    fun isInputAccessibilityServiceEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, InputAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(componentName.flattenToString()) == true
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isBatteryOptimizationPermissionRequired(): Boolean {
        return true // for min API level
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSpecificBatterySettings(context)
        }
    }

    fun openAppSpecificBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openGeneralBatteryOptimizationSettings(context)
        }
    }

    fun openGeneralBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isPostNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun hasReadMediaImagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getAllMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!isNotificationListenerEnabled(context)) {
            missing.add(NOTIFICATION_ACCESS)
        }

        if (!isInputAccessibilityServiceEnabled(context)) {
            missing.add(ACCESSIBILITY_SERVICE)
        }

        if (isNotificationPermissionRequired() && !isPostNotificationPermissionGranted(context)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (isBatteryOptimizationPermissionRequired() && !isBatteryOptimizationDisabled(context)) {
            missing.add(BACKGROUND_APP_USAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasReadMediaImagesPermission(context)) {
            missing.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        return missing
    }

    fun getCriticalMissingPermissions(context: Context): List<String> {
        val critical = mutableListOf<String>()
        if (!isNotificationListenerEnabled(context)) {
            critical.add(NOTIFICATION_ACCESS)
        }
        if (!isInputAccessibilityServiceEnabled(context)) {
            critical.add(ACCESSIBILITY_SERVICE)
        }
        return critical
    }

    fun getOptionalMissingPermissions(context: Context): List<String> {
        val optional = mutableListOf<String>()
        if (isNotificationPermissionRequired() && !isPostNotificationPermissionGranted(context)) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isBatteryOptimizationPermissionRequired() && !isBatteryOptimizationDisabled(context)) {
            optional.add(BACKGROUND_APP_USAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasReadMediaImagesPermission(context)) {
            optional.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        return optional
    }
}