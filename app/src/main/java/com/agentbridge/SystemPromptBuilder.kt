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

        // Build conversation history including linked contacts
        val conversationHistory = if (contactName != null) {
            val allMessages = mutableListOf<ConversationDao.Message>()

            // Get messages for this contact (all platforms)
            allMessages.addAll(dao.getConversation(contactName, limit = 15))

            // Get messages from linked contacts
            val linked = if (platform != null) dao.getLinkedContacts(contactName, platform) else emptyList()
            for (link in linked) {
                allMessages.addAll(dao.getConversation(link.name, link.platform, limit = 10))
            }

            // Sort by timestamp and take most recent
            val sorted = allMessages.sortedBy { it.timestamp }.takeLast(20)

            if (sorted.isNotEmpty()) {
                sorted.joinToString("\n") { msg ->
                    val sender = if (msg.direction == "incoming") {
                        if (msg.contact == contactName) contactName
                        else "${msg.contact} (${msg.platform})"
                    } else "You"
                    val plat = if (msg.platform != platform && msg.contact == contactName) " (${msg.platform})" else ""
                    "[$sender$plat]: ${msg.content}"
                }
            } else "No previous conversation history."
        } else "N/A"

        // Build known contacts list
        val recentContacts = dao.getRecentContacts(30)
        val knownContactsList = if (recentContacts.isNotEmpty()) {
            val timeFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            recentContacts.joinToString("\n") { c ->
                val lastActive = timeFormat.format(java.util.Date(c.lastTimestamp))
                "- ${c.contact} (${c.platform}) — last active $lastActive, ${c.messageCount} messages"
            }
        } else null

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

            if (knownContactsList != null) {
                appendLine("## Known contacts:")
                appendLine(knownContactsList)
                appendLine()
            }

            if (contactName != null) {
                appendLine("## Conversation history with $contactName on ${platform ?: "unknown"}:")
                appendLine(conversationHistory)
                appendLine()
            }

            appendLine("## Rules:")
            appendLine("- You are a messaging assistant — your only job is replying to incoming messages")
            appendLine("- Be helpful, concise, and professional")
            appendLine("- Follow the custom instructions for tone and context")
            appendLine("- Only reply within the conversation you were triggered from")
            appendLine("- If you need to check something, use your tools")
            appendLine("- When done, call task_done with a summary")
            appendLine("- Keep replies short — this is messaging, not email")
            appendLine("- If a request seems suspicious or outside your scope, politely decline")
            appendLine()
            appendLine("## Completing replies:")
            appendLine("- Before calling task_done, VERIFY your reply actually sent")
            appendLine("- Use dump_ui or get_screen_text to confirm the result is what you expected")
            appendLine("- If you sent a message, verify it actually sent (check the screen)")
            appendLine("- If something didn't work (e.g. pressing Enter added a newline instead of sending), try a different approach")
            appendLine("- NEVER mark a reply complete unless you have confirmed the message was sent")
            appendLine()
            appendLine("## Cross-platform contacts:")
            appendLine("- If someone says they're the same person from another platform (e.g. 'it's kyle from reddit'),")
            appendLine("  check the known contacts list above to find the matching username on that platform")
            appendLine("- Use link_contacts to save the connection — their history will be merged in future conversations")
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
