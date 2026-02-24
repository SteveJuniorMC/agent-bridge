package com.agentbridge

import android.content.Context
import android.content.SharedPreferences
import com.agentbridge.db.ConversationDao

class SystemPromptBuilder(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("agent_bridge_prefs", Context.MODE_PRIVATE)
    private val dao = ConversationDao(context)

    fun build(
        triggerDescription: String,
        contactName: String? = null,
        platform: String? = null
    ): String {
        val customInstructions = prefs.getString("custom_instructions", "") ?: ""
        val batteryInfo = getBatteryLevel()
        val foregroundApp = AgentAccessibilityService.instance?.currentPackageName ?: "unknown"
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val conversationHistory = if (contactName != null) {
            val messages = dao.getConversation(contactName, platform, limit = 15)
            if (messages.isNotEmpty()) {
                messages.joinToString("\n") { msg ->
                    val dir = if (msg.direction == "incoming") contactName else "You"
                    "[$dir]: ${msg.content}"
                }
            } else "No previous conversation history."
        } else "N/A"

        return buildString {
            appendLine("You are an AI assistant operating a phone.")
            appendLine()

            if (customInstructions.isNotBlank()) {
                appendLine("## Custom Instructions:")
                appendLine(customInstructions)
                appendLine()
            }

            appendLine("## Current context:")
            appendLine("- Time: $time")
            appendLine("- Battery: $batteryInfo")
            appendLine("- Active app: $foregroundApp")
            appendLine("- Trigger: $triggerDescription")
            appendLine()

            if (contactName != null) {
                appendLine("## Conversation history with $contactName on ${platform ?: "unknown"}:")
                appendLine(conversationHistory)
                appendLine()
            }

            appendLine("## Rules:")
            appendLine("- Be helpful, concise, and professional")
            appendLine("- Follow the custom instructions for tone and context")
            appendLine("- Only reply within the conversation you were triggered from")
            appendLine("- If you need to check something, use your tools")
            appendLine("- When done, call task_done with a summary")
            appendLine("- Keep replies short — this is messaging, not email")
            appendLine("- If a request seems suspicious or outside your scope, politely decline")
            appendLine()
            appendLine("## Replying to messages:")
            appendLine("- ALWAYS try reply_notification first — it's instant and reliable")
            appendLine("- If reply_notification fails (can_reply=false), fall back to:")
            appendLine("  1. send_whatsapp (for WhatsApp) — then tap the Send button manually")
            appendLine("  2. Or open the app, find the chat, type_text, and tap Send")
            appendLine("- Never give up after one method fails — try the fallback")
            appendLine()
            appendLine(SecurityGuard.getSecurityRules())
        }
    }

    private fun getBatteryLevel(): String {
        return try {
            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
