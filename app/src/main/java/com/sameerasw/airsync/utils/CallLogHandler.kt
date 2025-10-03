package com.sameerasw.airsync.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object CallLogHandler {
    private const val TAG = "CallLogHandler"

    @SuppressLint("MissingPermission")
    fun fetchCallLogs(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = context.contentResolver
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
                )

                val calls = JSONArray()
                cursor?.use {
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                    val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                    while (it.moveToNext() && calls.length() < 200) { // Limit to 200 most recent calls
                        val call = JSONObject().apply {
                            put("number", it.getString(numberIndex))
                            put("type", getCallType(it.getInt(typeIndex)))
                            put("date", it.getLong(dateIndex))
                            put("duration", it.getLong(durationIndex))
                            put("name", it.getString(nameIndex) ?: "")
                        }
                        calls.put(call)
                    }
                }

                val response = JSONObject().apply {
                    put("type", "callLogsResponse")
                    put("data", calls)
                }
                WebSocketUtil.sendMessage(JsonUtil.toSingleLine(response.toString()))
                Log.d(TAG, "Successfully sent ${calls.length()} call logs.")

            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: Missing READ_CALL_LOG permission.")
                sendPermissionError()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching call logs: ${e.message}")
            }
        }
    }

    private fun getCallType(typeCode: Int): String {
        return when (typeCode) {
            CallLog.Calls.INCOMING_TYPE -> "incoming"
            CallLog.Calls.OUTGOING_TYPE -> "outgoing"
            CallLog.Calls.MISSED_TYPE -> "missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
            CallLog.Calls.REJECTED_TYPE -> "rejected"
            CallLog.Calls.BLOCKED_TYPE -> "blocked"
            else -> "unknown"
        }
    }

    private fun sendPermissionError() {
        val errorResponse = JSONObject().apply {
            put("type", "callLogsResponse")
            put("error", "Permission denied. Please grant READ_CALL_LOG permission.")
        }
        WebSocketUtil.sendMessage(JsonUtil.toSingleLine(errorResponse.toString()))
    }
}
