package com.agentbridge.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object SmsTools {

    fun sendSms(context: Context, params: Map<String, Any>): JsonObject {
        val to = params["to"]?.toString() ?: return error("Missing param: to")
        val message = params["message"]?.toString() ?: return error("Missing param: message")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return error("SMS permission not granted")
        }

        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, message, null, null)
            }
            JsonObject().apply {
                addProperty("success", true)
                addProperty("message", "SMS sent to $to")
            }
        } catch (e: Exception) {
            error("Failed to send SMS: ${e.message}")
        }
    }

    fun readSms(context: Context, params: Map<String, Any>): JsonObject {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return error("SMS read permission not granted")
        }

        val from = params["from"]?.toString()
        val limit = (params["limit"] as? Number)?.toInt() ?: 10

        return try {
            val messages = JsonArray()
            val uri = Uri.parse("content://sms/inbox")
            val selection = if (from != null) "address LIKE ?" else null
            val selectionArgs = if (from != null) arrayOf("%$from%") else null

            context.contentResolver.query(
                uri, arrayOf("address", "body", "date", "read"),
                selection, selectionArgs, "date DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val msg = JsonObject().apply {
                        addProperty("from", cursor.getString(0))
                        addProperty("body", cursor.getString(1))
                        addProperty("date", cursor.getLong(2))
                        addProperty("read", cursor.getInt(3) == 1)
                    }
                    messages.add(msg)
                    count++
                }
            }

            JsonObject().apply {
                addProperty("success", true)
                add("messages", messages)
                addProperty("count", messages.size())
            }
        } catch (e: Exception) {
            error("Failed to read SMS: ${e.message}")
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
