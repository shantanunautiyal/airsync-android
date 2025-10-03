package com.sameerasw.airsync.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object SmsHandler {
    private const val TAG = "SmsHandler"

    fun fetchConversations(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse("content://sms/")
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, "date DESC")
                val threads = mutableMapOf<String, JSONArray>()

                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val threadId = c.getString(c.getColumnIndexOrThrow("thread_id"))
                        val address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                        val body = c.getString(c.getColumnIndexOrThrow("body")) ?: ""
                        val date = c.getLong(c.getColumnIndexOrThrow("date")) / 1000L
                        val id = c.getString(c.getColumnIndexOrThrow("_id"))
                        val type = c.getInt(c.getColumnIndexOrThrow("type"))

                        val msg = JSONObject()
                        msg.put("id", id)
                        msg.put("from", if (type == 2) null else address)
                        msg.put("to", if (type == 2) address else null)
                        msg.put("body", body)
                        msg.put("timestamp", date)

                        val arr = threads.getOrPut(threadId) { JSONArray() }
                        arr.put(msg)
                    }
                }

                val convs = JSONArray()
                for ((threadId, msgs) in threads) {
                    val firstMsg = msgs.optJSONObject(0)
                    val address = firstMsg?.optString("to") ?: firstMsg?.optString("from") ?: ""
                    val contact = resolveContactName(context, address)
                    val conv = JSONObject()
                    conv.put("id", threadId)
                    conv.put("address", address)
                    conv.put("contact", contact)
                    conv.put("messages", msgs)
                    convs.put(conv)
                }

                val out = JSONObject()
                out.put("type", "smsConversations")
                out.put("data", convs)
                WebSocketUtil.sendMessage(JsonUtil.toSingleLine(out.toString()))

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching SMS conversations: ${e.message}")
            }
        }
    }

    fun fetchMessagesForConversation(context: Context, conversationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sel = "thread_id = ?"
                val cursor = context.contentResolver.query(Uri.parse("content://sms/"), null, sel, arrayOf(conversationId), "date ASC")
                val msgs = JSONArray()
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(c.getColumnIndexOrThrow("_id"))
                        val address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                        val body = c.getString(c.getColumnIndexOrThrow("body")) ?: ""
                        val date = c.getLong(c.getColumnIndexOrThrow("date")) / 1000L
                        val type = c.getInt(c.getColumnIndexOrThrow("type"))

                        val msg = JSONObject()
                        msg.put("id", id)
                        msg.put("from", if (type == 2) null else address)
                        msg.put("to", if (type == 2) address else null)
                        msg.put("body", body)
                        msg.put("timestamp", date)
                        msgs.put(msg)
                    }
                }

                val out = JSONObject()
                out.put("type", "smsMessages")
                val data = JSONObject()
                data.put("conversationId", conversationId)
                data.put("messages", msgs)
                out.put("data", data)
                WebSocketUtil.sendMessage(JsonUtil.toSingleLine(out.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching messages for convo $conversationId: ${e.message}")
            }
        }
    }

    private fun resolveContactName(context: Context, number: String?): String {
        try {
            if (number.isNullOrBlank()) return ""
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0) ?: ""
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }

    fun sendSms(context: Context, recipient: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sms = SmsManager.getDefault()
                sms.sendTextMessage(recipient, null, message, null, null)

                // Build a message object and send smsMessageSent
                val msg = org.json.JSONObject()
                msg.put("id", java.util.UUID.randomUUID().toString())
                msg.put("from", null)
                msg.put("to", recipient)
                msg.put("body", message)
                msg.put("timestamp", System.currentTimeMillis() / 1000L)

                val out = org.json.JSONObject()
                out.put("type", "smsMessageSent")
                val data = org.json.JSONObject()
                data.put("conversationId", "")
                data.put("message", msg)
                out.put("data", data)

                WebSocketUtil.sendMessage(JsonUtil.toSingleLine(out.toString()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $recipient: ${e.message}")
            }
        }
    }
}
