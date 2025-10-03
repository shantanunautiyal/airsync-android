package com.sameerasw.airsync.utils

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime

// Data class to hold the fetched health data
data class HealthData(
    val steps: Long?,
    val heartRate: Long?,
    val sleep: String?
) {
    fun toJson(): String {
        val dataMap = mapOf(
            "steps" to steps,
            "heartRate" to heartRate,
            "sleep" to sleep
        )
        val responseMap = mapOf(
            "type" to "healthData",
            "data" to dataMap
        )
        return Gson().toJson(responseMap)
    }
}

object HealthConnectManager {

    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun getHealthPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun checkPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            return granted.containsAll(PERMISSIONS)
        }
        return false
    }

    suspend fun readHealthData(context: Context): HealthData = withContext(Dispatchers.IO) {
        if (!checkPermissions(context)) {
            // Cannot proceed if permissions are not granted
            return@withContext HealthData(null, null, null)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val todayStart = ZonedDateTime.now().toLocalDate().atStartOfDay(ZonedDateTime.now().zone).toInstant()
        val now = Instant.now()

        // 1. Fetch Today's Steps
        val steps = try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(todayStart, now)
                )
            )
            response.records.sumOf { it.count }
        } catch (e: Exception) {
            null
        }

        // 2. Fetch Latest Heart Rate
        val heartRate = try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(todayStart, now),
                    ascendingOrder = false
                )
            )
            response.records.firstOrNull()?.samples?.firstOrNull()?.beatsPerMinute?.toLong()
        } catch (e: Exception) {
            null
        }

        // 3. Fetch Last Night's Sleep
        val sleepDuration = try {
            // Look for sleep sessions ending in the last 24 hours
            val lastDay = Instant.now().minusSeconds(86400)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(lastDay, now)
                )
            )
            // Get the most recent sleep session
            val lastSession = response.records.maxByOrNull { it.endTime }
            lastSession?.let {
                val durationSeconds = (it.endTime.epochSecond - it.startTime.epochSecond)
                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60
                "${hours}h ${minutes}m"
            }
        } catch (e: Exception) {
            null
        }

        return@withContext HealthData(steps, heartRate, sleepDuration)
    }
}