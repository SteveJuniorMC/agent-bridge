package com.agentbridge.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object ContactsTools {

    fun getContacts(context: Context, params: Map<String, Any>): JsonObject {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return error("Contacts permission not granted")
        }

        val query = params["query"]?.toString()
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        return try {
            val contacts = JsonArray()
            val selection = if (query != null)
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?" else null
            val selectionArgs = if (query != null) arrayOf("%$query%") else null

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection, selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val contact = JsonObject().apply {
                        addProperty("name", cursor.getString(0))
                        addProperty("phone", cursor.getString(1))
                    }
                    contacts.add(contact)
                    count++
                }
            }

            JsonObject().apply {
                addProperty("success", true)
                add("contacts", contacts)
                addProperty("count", contacts.size())
            }
        } catch (e: Exception) {
            error("Failed to read contacts: ${e.message}")
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
