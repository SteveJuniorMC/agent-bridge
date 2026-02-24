package com.agentbridge.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.JsonObject

object IntentTools {

    fun openApp(context: Context, params: Map<String, Any>): JsonObject {
        val packageName = params["package"]?.toString()
        val url = params["url"]?.toString()

        return try {
            val intent = when {
                packageName != null -> {
                    context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: return error("App not found: $packageName")
                }
                url != null -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                }
                else -> return error("Must provide 'package' or 'url' parameter")
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            JsonObject().apply {
                addProperty("success", true)
                addProperty("message", "App opened: ${packageName ?: url}")
            }
        } catch (e: Exception) {
            error("Failed to open app: ${e.message}")
        }
    }

    fun sendWhatsapp(context: Context, params: Map<String, Any>): JsonObject {
        val to = params["to"]?.toString() ?: return error("Missing param: to")
        val message = params["message"]?.toString() ?: return error("Missing param: message")

        return try {
            // Clean phone number (remove spaces, dashes, etc.)
            val cleanNumber = to.replace(Regex("[^0-9+]"), "")
            val uri = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            JsonObject().apply {
                addProperty("success", true)
                addProperty("message", "WhatsApp opened for $cleanNumber with prefilled message")
            }
        } catch (e: Exception) {
            error("Failed to open WhatsApp: ${e.message}")
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
