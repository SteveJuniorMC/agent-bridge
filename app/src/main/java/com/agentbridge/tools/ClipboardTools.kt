package com.agentbridge.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.gson.JsonObject

object ClipboardTools {

    fun setClipboard(context: Context, params: Map<String, Any>): JsonObject {
        val text = params["text"]?.toString() ?: return error("Missing param: text")

        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("agent_bridge", text)
            clipboard.setPrimaryClip(clip)
            JsonObject().apply {
                addProperty("success", true)
                addProperty("message", "Clipboard set")
            }
        } catch (e: Exception) {
            error("Failed to set clipboard: ${e.message}")
        }
    }

    fun getClipboard(context: Context, params: Map<String, Any>): JsonObject {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

            JsonObject().apply {
                addProperty("success", true)
                addProperty("text", text ?: "")
                addProperty("hasContent", text != null)
            }
        } catch (e: Exception) {
            error("Failed to read clipboard: ${e.message}")
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
