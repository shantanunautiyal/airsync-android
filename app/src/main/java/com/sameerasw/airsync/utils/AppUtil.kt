package com.sameerasw.airsync.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

object AppUtil {
    private const val TAG = "AppUtil"

    /**
     * Get all installed apps
     */
    suspend fun getInstalledApps(context: Context): List<NotificationApp> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager

            // Intent to find apps with launcher entry points
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val apps = pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo

                    // Skip our own app
                    if (appInfo.packageName.contains("airsync")) return@mapNotNull null

                    try {
                        NotificationApp(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            isEnabled = true,
                            icon = pm.getApplicationIcon(appInfo),
                            isSystemApp = isSystemApp(appInfo),
                            lastUpdated = System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading app ${appInfo.packageName}: ${e.message}")
                        null
                    }
                }
                .sortedBy { it.appName.lowercase() }

            apps
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Merge installed apps with saved preferences, keeping user settings and adding new apps
     */
    fun mergeWithSavedApps(
        installedApps: List<NotificationApp>,
        savedApps: List<NotificationApp>
    ): List<NotificationApp> {
        val savedAppsMap = savedApps.associateBy { it.packageName }

        return installedApps.map { installedApp ->
            val savedApp = savedAppsMap[installedApp.packageName]
            savedApp?.// Use saved preferences but update app info
            copy(
                appName = installedApp.appName,
                icon = installedApp.icon,
                isSystemApp = installedApp.isSystemApp
            )
                ?: // New app, use defaults (enabled)
                installedApp
        }.sortedBy { it.appName.lowercase() }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

}
