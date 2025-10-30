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
import androidx.health.connect.client.HealthConnectClient
import com.sameerasw.airsync.models.PermissionCategory
import com.sameerasw.airsync.models.PermissionGroup
import com.sameerasw.airsync.models.PermissionInfo
import com.sameerasw.airsync.service.InputAccessibilityService
import com.sameerasw.airsync.service.MediaNotificationListener
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

object PermissionUtil {

    const val ACCESSIBILITY_SERVICE = "Accessibility Service"
    const val NOTIFICATION_ACCESS = "Notification Access"
    const val BACKGROUND_APP_USAGE = "Background App Usage"
    const val HEALTH_CONNECT = "Health Connect"
    
    // Permission request codes
    const val REQUEST_CODE_RUNTIME_PERMISSIONS = 1001
    const val REQUEST_CODE_HEALTH_CONNECT = 1002
    const val REQUEST_CODE_LOCATION = 1003

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
    
    // ========== New Comprehensive Permission Management ==========
    
    /**
     * Get all permissions grouped by category with their status
     */
    fun getAllPermissionGroups(context: Context): List<PermissionGroup> {
        return listOf(
            getCorePermissions(context),
            getMessagingPermissions(context),
            getCallPermissions(context),
            getHealthPermissions(context),
            getLocationPermissions(context),
            getStoragePermissions(context),
            getSpecialPermissions(context)
        )
    }
    
    private fun getCorePermissions(context: Context): PermissionGroup {
        val permissions = mutableListOf<PermissionInfo>()
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionInfo(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    displayName = "Notifications",
                    description = "Show notifications from Mac",
                    category = PermissionCategory.CORE,
                    isGranted = isPostNotificationPermissionGranted(context),
                    isRequired = true
                )
            )
        }
        
        // Notification Listener
        permissions.add(
            PermissionInfo(
                permission = NOTIFICATION_ACCESS,
                displayName = "Notification Access",
                description = "Read and sync notifications",
                category = PermissionCategory.CORE,
                isGranted = isNotificationListenerEnabled(context),
                isRequired = true,
                requiresSpecialHandling = true
            )
        )
        
        // Accessibility Service
        permissions.add(
            PermissionInfo(
                permission = ACCESSIBILITY_SERVICE,
                displayName = "Accessibility Service",
                description = "Remote control your device",
                category = PermissionCategory.CORE,
                isGranted = isInputAccessibilityServiceEnabled(context),
                isRequired = true,
                requiresSpecialHandling = true
            )
        )
        
        // Battery Optimization
        permissions.add(
            PermissionInfo(
                permission = BACKGROUND_APP_USAGE,
                displayName = "Battery Optimization",
                description = "Keep connection alive in background",
                category = PermissionCategory.CORE,
                isGranted = isBatteryOptimizationDisabled(context),
                isRequired = false,
                requiresSpecialHandling = true
            )
        )
        
        return PermissionGroup(
            category = PermissionCategory.CORE,
            title = "Core Permissions",
            description = "Essential for AirSync to work",
            permissions = permissions
        )
    }
    
    private fun getMessagingPermissions(context: Context): PermissionGroup {
        val permissions = listOf(
            PermissionInfo(
                permission = Manifest.permission.READ_SMS,
                displayName = "Read SMS",
                description = "View your text messages",
                category = PermissionCategory.MESSAGING,
                isGranted = checkPermission(context, Manifest.permission.READ_SMS),
                isRequired = false
            ),
            PermissionInfo(
                permission = Manifest.permission.SEND_SMS,
                displayName = "Send SMS",
                description = "Send text messages from Mac",
                category = PermissionCategory.MESSAGING,
                isGranted = checkPermission(context, Manifest.permission.SEND_SMS),
                isRequired = false
            ),
            PermissionInfo(
                permission = Manifest.permission.RECEIVE_SMS,
                displayName = "Receive SMS",
                description = "Get notified of new messages",
                category = PermissionCategory.MESSAGING,
                isGranted = checkPermission(context, Manifest.permission.RECEIVE_SMS),
                isRequired = false
            )
        )
        
        return PermissionGroup(
            category = PermissionCategory.MESSAGING,
            title = "Messaging",
            description = "Send and receive text messages",
            permissions = permissions
        )
    }
    
    private fun getCallPermissions(context: Context): PermissionGroup {
        val permissions = mutableListOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALL_LOG,
                displayName = "Read Call Log",
                description = "View your call history",
                category = PermissionCategory.CALLS,
                isGranted = checkPermission(context, Manifest.permission.READ_CALL_LOG),
                isRequired = false
            ),
            PermissionInfo(
                permission = Manifest.permission.READ_PHONE_STATE,
                displayName = "Phone State",
                description = "Detect incoming/outgoing calls",
                category = PermissionCategory.CALLS,
                isGranted = checkPermission(context, Manifest.permission.READ_PHONE_STATE),
                isRequired = false
            ),
            PermissionInfo(
                permission = Manifest.permission.READ_CONTACTS,
                displayName = "Read Contacts",
                description = "Show contact names in calls and messages",
                category = PermissionCategory.CALLS,
                isGranted = checkPermission(context, Manifest.permission.READ_CONTACTS),
                isRequired = false
            )
        )
        
        // ANSWER_PHONE_CALLS is only available on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(
                PermissionInfo(
                    permission = Manifest.permission.ANSWER_PHONE_CALLS,
                    displayName = "Answer Calls",
                    description = "Answer calls from Mac (limited support)",
                    category = PermissionCategory.CALLS,
                    isGranted = checkPermission(context, Manifest.permission.ANSWER_PHONE_CALLS),
                    isRequired = false
                )
            )
        }
        
        return PermissionGroup(
            category = PermissionCategory.CALLS,
            title = "Calls & Contacts",
            description = "Manage calls and view contacts",
            permissions = permissions
        )
    }
    
    private fun getHealthPermissions(context: Context): PermissionGroup {
        val permissions = mutableListOf<PermissionInfo>()
        
        // Check if Health Connect is available
        val healthConnectAvailable = HealthConnectUtil.isAvailable(context)
        
        if (healthConnectAvailable) {
            // Check Health Connect permissions
            val hasHealthPermissions = runBlocking {
                try {
                    HealthConnectUtil.hasPermissions(context)
                } catch (e: Exception) {
                    false
                }
            }
            
            permissions.add(
                PermissionInfo(
                    permission = HEALTH_CONNECT,
                    displayName = "Health Connect",
                    description = "Access health and fitness data (steps, heart rate, sleep, etc.)",
                    category = PermissionCategory.HEALTH,
                    isGranted = hasHealthPermissions,
                    isRequired = false,
                    requiresSpecialHandling = true
                )
            )
        }
        
        // Activity Recognition (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(
                PermissionInfo(
                    permission = Manifest.permission.ACTIVITY_RECOGNITION,
                    displayName = "Physical Activity",
                    description = "Track your physical activities and movements",
                    category = PermissionCategory.HEALTH,
                    isGranted = checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION),
                    isRequired = false
                )
            )
        }
        
        return PermissionGroup(
            category = PermissionCategory.HEALTH,
            title = "Health & Fitness",
            description = "Sync health and activity data",
            permissions = permissions
        )
    }
    
    private fun getLocationPermissions(context: Context): PermissionGroup {
        val permissions = mutableListOf(
            PermissionInfo(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                displayName = "Precise Location",
                description = "Track location for activity and fitness data",
                category = PermissionCategory.LOCATION,
                isGranted = checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
                isRequired = false
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                displayName = "Approximate Location",
                description = "General location for activity tracking",
                category = PermissionCategory.LOCATION,
                isGranted = checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
                isRequired = false
            )
        )
        
        // Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(
                PermissionInfo(
                    permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    displayName = "Background Location",
                    description = "Track location in background for continuous activity monitoring",
                    category = PermissionCategory.LOCATION,
                    isGranted = checkPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    isRequired = false
                )
            )
        }
        
        return PermissionGroup(
            category = PermissionCategory.LOCATION,
            title = "Location",
            description = "For activity and fitness tracking",
            permissions = permissions
        )
    }
    
    private fun getStoragePermissions(context: Context): PermissionGroup {
        val permissions = mutableListOf<PermissionInfo>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                PermissionInfo(
                    permission = Manifest.permission.READ_MEDIA_IMAGES,
                    displayName = "Photos",
                    description = "Share photos with Mac",
                    category = PermissionCategory.STORAGE,
                    isGranted = hasReadMediaImagesPermission(context),
                    isRequired = false
                )
            )
        }
        
        return PermissionGroup(
            category = PermissionCategory.STORAGE,
            title = "Storage",
            description = "Access photos and files",
            permissions = permissions
        )
    }
    
    private fun getSpecialPermissions(context: Context): PermissionGroup {
        // This is handled in Core permissions
        return PermissionGroup(
            category = PermissionCategory.SPECIAL,
            title = "Special Permissions",
            description = "System-level permissions",
            permissions = emptyList()
        )
    }
    
    private fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get list of runtime permissions that need to be requested
     */
    fun getRuntimePermissionsToRequest(context: Context): List<String> {
        val permissions = mutableListOf<String>()
        
        // Messaging permissions
        if (!checkPermission(context, Manifest.permission.READ_SMS)) {
            permissions.add(Manifest.permission.READ_SMS)
        }
        if (!checkPermission(context, Manifest.permission.SEND_SMS)) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (!checkPermission(context, Manifest.permission.RECEIVE_SMS)) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }
        
        // Call permissions
        if (!checkPermission(context, Manifest.permission.READ_CALL_LOG)) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }
        if (!checkPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (!checkPermission(context, Manifest.permission.READ_CONTACTS)) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        
        // Answer phone calls (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!checkPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)) {
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
        }
        
        // Activity recognition (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        
        // Location permissions
        if (!checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        // Post notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPostNotificationPermissionGranted(context)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!hasReadMediaImagesPermission(context)) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        
        return permissions
    }
    
    /**
     * Open Health Connect permission screen
     * Note: This should be called from an Activity context with proper permission launcher
     */
    fun openHealthConnectPermissions(context: Context) {
        try {
            // Try to open Health Connect app directly
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                // Health Connect not installed, open Play Store
                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(playStoreIntent)
            }
        } catch (e: Exception) {
            // Last resort: open app settings
            openAppSettings(context)
        }
    }
    
    /**
     * Get count of missing permissions
     */
    fun getMissingPermissionCount(context: Context): Int {
        return getAllPermissionGroups(context)
            .flatMap { it.permissions }
            .count { !it.isGranted }
    }
    
    /**
     * Get count of missing required permissions
     */
    fun getMissingRequiredPermissionCount(context: Context): Int {
        return getAllPermissionGroups(context)
            .flatMap { it.permissions }
            .count { !it.isGranted && it.isRequired }
    }
}