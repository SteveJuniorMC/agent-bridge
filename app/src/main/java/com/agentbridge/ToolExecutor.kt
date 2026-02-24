package com.agentbridge

import android.content.Context
import android.util.Log
import com.agentbridge.db.ConversationDao
import com.agentbridge.tools.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val gson = Gson()
    private val dao = ConversationDao(context)

    fun execute(toolName: String, argumentsJson: String, taskId: Long? = null): JsonObject {
        val params: Map<String, Any> = try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(argumentsJson, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse arguments: $argumentsJson", e)
            emptyMap()
        }

        // Security check
        val securityResult = SecurityGuard.validateAction(toolName, params, context)
        if (securityResult is SecurityGuard.SecurityResult.Blocked) {
            Log.w(TAG, "Action blocked: ${securityResult.reason}")
            SecurityGuard.logAction(dao, toolName, argumentsJson, securityResult.reason, taskId, blocked = true)
            return JsonObject().apply {
                addProperty("success", false)
                addProperty("error", "BLOCKED: ${securityResult.reason}")
            }
        }

        Log.d(TAG, "Executing tool: $toolName with params: $params")

        val result = try {
            when (toolName) {
                // Screen
                "tap" -> ScreenTools.tap(params)
                "long_press" -> ScreenTools.longPress(params)
                "swipe" -> ScreenTools.swipe(params)
                "type_text" -> ScreenTools.typeText(params)
                "click_text" -> ScreenTools.clickText(params)
                "click_id" -> ScreenTools.clickId(params)
                "scroll" -> ScreenTools.scroll(params)
                "dump_ui" -> ScreenTools.dumpUi(params)
                "get_screen_text" -> ScreenTools.getScreenText(params)
                "find_element" -> ScreenTools.findElement(params)
                "back" -> ScreenTools.back(params)
                "home" -> ScreenTools.home(params)
                "open_app" -> IntentTools.openApp(context, params)
                "open_notifications" -> ScreenTools.openNotifications(params)

                // Messaging
                "send_sms" -> SmsTools.sendSms(context, params)
                "read_sms" -> SmsTools.readSms(context, params)
                "send_whatsapp" -> IntentTools.sendWhatsapp(context, params)

                // Phone
                "get_contacts" -> ContactsTools.getContacts(context, params)
                "get_location" -> LocationTools.getLocation(context, params)
                "get_time" -> SystemTools.getTime(params)
                "get_battery" -> SystemTools.getBattery(context, params)
                "set_clipboard" -> ClipboardTools.setClipboard(context, params)
                "get_clipboard" -> ClipboardTools.getClipboard(context, params)
                "speak" -> TtsTools.speak(context, params)

                // Memory
                "get_conversation" -> executeGetConversation(params)
                "save_note" -> executeSaveNote(params)
                "get_note" -> executeGetNote(params)

                // Task
                "task_done" -> executeTaskDone(params)

                else -> JsonObject().apply {
                    addProperty("success", false)
                    addProperty("error", "Unknown tool: $toolName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $toolName", e)
            JsonObject().apply {
                addProperty("success", false)
                addProperty("error", "Execution error: ${e.message}")
            }
        }

        // Audit log
        SecurityGuard.logAction(dao, toolName, argumentsJson, result.toString().take(500), taskId, blocked = false)

        return result
    }

    private fun executeGetConversation(params: Map<String, Any>): JsonObject {
        val contact = params["contact"]?.toString() ?: return error("Missing param: contact")
        val platform = params["platform"]?.toString()

        val messages = dao.getConversation(contact, platform)
        val result = JsonObject().apply { addProperty("success", true) }
        val msgArray = com.google.gson.JsonArray()
        for (msg in messages) {
            msgArray.add(JsonObject().apply {
                addProperty("direction", msg.direction)
                addProperty("content", msg.content)
                addProperty("timestamp", msg.timestamp)
            })
        }
        result.add("messages", msgArray)
        result.addProperty("count", messages.size)
        return result
    }

    private fun executeSaveNote(params: Map<String, Any>): JsonObject {
        val key = params["key"]?.toString() ?: return error("Missing param: key")
        val value = params["value"]?.toString() ?: return error("Missing param: value")
        dao.saveNote(key, value)
        return JsonObject().apply {
            addProperty("success", true)
            addProperty("message", "Note saved: $key")
        }
    }

    private fun executeGetNote(params: Map<String, Any>): JsonObject {
        val key = params["key"]?.toString() ?: return error("Missing param: key")
        val value = dao.getNote(key)
        return JsonObject().apply {
            addProperty("success", true)
            addProperty("key", key)
            addProperty("value", value)
            addProperty("found", value != null)
        }
    }

    private fun executeTaskDone(params: Map<String, Any>): JsonObject {
        val summary = params["summary"]?.toString() ?: "Task completed"
        return JsonObject().apply {
            addProperty("success", true)
            addProperty("done", true)
            addProperty("summary", summary)
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
