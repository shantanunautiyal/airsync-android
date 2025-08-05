package com.sameerasw.airsync.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.gson.Gson
import com.sameerasw.airsync.domain.model.GitHubRelease
import com.sameerasw.airsync.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_API_URL = "https://api.github.com/repos/sameerasw/airsync-android/releases/latest"
    private const val APK_FILE_NAME = "app-release.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check for updates from GitHub releases
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates...")

            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AirSync-Android/${getCurrentVersionName(context)}")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub API request failed: ${response.code}")
                return@withContext null
            }

            val json = response.body?.string() ?: run {
                Log.e(TAG, "Empty response body")
                return@withContext null
            }

            val release = Gson().fromJson(json, GitHubRelease::class.java)
            Log.d(TAG, "Latest release: ${release.tagName}")

            // Skip draft releases
            if (release.isDraft) {
                Log.d(TAG, "Skipping draft release")
                return@withContext null
            }

            // Parse versions
            val currentVersion = getCurrentVersionName(context)
            val remoteVersion = release.tagName.removePrefix("v")

            // Check if update is needed
            if (!isUpdateNeeded(currentVersion, remoteVersion)) {
                Log.d(TAG, "No update needed. Current: $currentVersion, Remote: $remoteVersion")
                return@withContext null
            }

            // Find APK asset
            val apkAsset = release.assets.find { asset ->
                asset.name.endsWith(".apk") && (
                    asset.name.contains("app-release") ||
                    asset.name.contains("airsync")
                )
            }

            if (apkAsset == null) {
                Log.e(TAG, "No APK asset found in release")
                return@withContext null
            }

            Log.d(TAG, "Update available: $currentVersion -> $remoteVersion")

            return@withContext UpdateInfo(
                release = release,
                asset = apkAsset,
                currentVersion = currentVersion,
                newVersion = remoteVersion,
                isBetaUpdate = release.isPrerelease || remoteVersion.contains("BETA", ignoreCase = true),
                downloadSize = formatFileSize(apkAsset.size)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            null
        }
    }

    /**
     * Get current app version name from PackageManager
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "2.0.4"
        } catch (e: Exception) {
            Log.w(TAG, "Could not get version name: ${e.message}")
            "2.0.4"
        }
    }

    /**
     * Compare version strings to determine if update is needed
     */
    private fun isUpdateNeeded(currentVersion: String, remoteVersion: String): Boolean {
        try {
            val current = parseVersionCode(currentVersion)
            val remote = parseVersionCode(remoteVersion)
            return remote > current
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $currentVersion vs $remoteVersion", e)
            return false
        }
    }

    /**
     * Parse version string like beta
     */
    private fun parseVersionCode(version: String): Int {
        val cleanVersion = version.removePrefix("v")
            .split("-")[0] // Remove BETA, ALPHA etc suffixes

        val parts = cleanVersion.split(".").mapNotNull { it.toIntOrNull() }

        return when (parts.size) {
            3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
            2 -> parts[0] * 10000 + parts[1] * 100
            1 -> parts[0] * 10000
            else -> 0
        }
    }

    /**
     * Download APK file using DownloadManager
     */
    fun downloadUpdate(context: Context, updateInfo: UpdateInfo): Long {
        try {
            // Create download directory
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
            downloadDir.mkdirs()

            val apkFile = File(downloadDir, APK_FILE_NAME)

            // Delete existing file
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = DownloadManager.Request(updateInfo.asset.downloadUrl.toUri())
                .setTitle("AirSync Update")
                .setDescription("Downloading AirSync ${updateInfo.newVersion} (${updateInfo.downloadSize})")
                .setDestinationUri(Uri.fromFile(apkFile))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Log.d(TAG, "Started download with ID: $downloadId")
            return downloadId

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            throw e
        }
    }

    /**
     * Install downloaded APK
     */
    fun installUpdate(context: Context) {
        try {
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
            val apkFile = File(downloadDir, APK_FILE_NAME)

            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found for installation")
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
            throw e
        }
    }

    /**
     * Check if app can install packages from unknown sources
     */
    fun canInstallPackages(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Open settings to allow installation from unknown sources
     */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Format file size in human readable format
     */
    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
    }

    /**
     * Get download progress
     */
    fun getDownloadProgress(context: Context, downloadId: Long): Int {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                if (bytesTotal > 0) {
                    ((bytesDownloaded * 100L) / bytesTotal).toInt()
                } else {
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download progress", e)
            0
        }
    }

    /**
     * Check if download is complete
     */
    fun isDownloadComplete(context: Context, downloadId: Long): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                status == DownloadManager.STATUS_SUCCESSFUL
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking download status", e)
            false
        }
    }
}
