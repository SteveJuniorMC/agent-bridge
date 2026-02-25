package com.agentbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object ToolRegistry {

    private val accessibilityTools = setOf(
        "tap", "long_press", "swipe", "type_text", "click_text", "click_id",
        "scroll", "dump_ui", "get_screen_text", "find_element", "back", "home",
        "open_app", "list_installed_apps", "open_notifications", "send_whatsapp"
    )

    private val smsTools = setOf("send_sms", "read_sms")

    private val contactsTools = setOf("get_contacts")

    fun getToolDefinitions(context: Context): List<OpenRouterClient.ToolDefinition> {
        val hasAccessibility = AgentAccessibilityService.instance != null
        val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

        return allTools().filter { tool ->
            val name = tool.function.name
            when {
                name in accessibilityTools -> hasAccessibility
                name in smsTools -> hasSms
                name in contactsTools -> hasContacts
                else -> true
            }
        }
    }

    private fun allTools(): List<OpenRouterClient.ToolDefinition> {
        return listOf(
            // Screen Control
            tool("tap", "Tap at screen coordinates") {
                requiredNumber("x", "X coordinate")
                requiredNumber("y", "Y coordinate")
            },
            tool("long_press", "Long press at screen coordinates") {
                requiredNumber("x", "X coordinate")
                requiredNumber("y", "Y coordinate")
            },
            tool("swipe", "Swipe gesture from one point to another") {
                requiredNumber("x1", "Start X")
                requiredNumber("y1", "Start Y")
                requiredNumber("x2", "End X")
                requiredNumber("y2", "End Y")
            },
            tool("type_text", "Type text into the currently focused input field") {
                requiredString("text", "Text to type")
            },
            tool("click_text", "Find and click an element by its visible text") {
                requiredString("text", "Visible text to find and click")
            },
            tool("click_id", "Find and click an element by its resource ID") {
                requiredString("id", "Resource ID to find and click")
            },
            tool("scroll", "Scroll the screen up or down") {
                requiredString("direction", "Scroll direction: 'up' or 'down'")
            },
            tool("dump_ui", "Get the full UI tree as JSON. Use to understand what's on screen.") {},
            tool("get_screen_text", "Get all visible text from the current screen") {},
            tool("find_element", "Find an element and return its bounds/properties") {
                optionalString("text", "Find by visible text")
                optionalString("id", "Find by resource ID")
            },
            tool("back", "Press the back button") {},
            tool("home", "Press the home button") {},
            tool("open_app", "Open an app by package name or URL. ALWAYS prefer this over trying to find and tap app icons on the home screen. Common packages: com.whatsapp, com.google.android.apps.messaging, org.telegram.messenger, com.instagram.android, com.facebook.orca, com.reddit.frontpage, com.snapchat.android, com.twitter.android, com.spotify.music, com.google.android.youtube, com.google.android.gm (Gmail), com.google.android.calendar, com.google.android.apps.maps, com.android.chrome, com.google.android.dialer. If you don't know the package name, use list_installed_apps to find it.") {
                optionalString("package", "App package name (e.g. com.whatsapp)")
                optionalString("url", "URL to open")
            },
            tool("list_installed_apps", "List all installed apps with their package names. Use this to find the correct package name before calling open_app.") {
                optionalString("query", "Filter apps by name (optional)")
            },
            tool("open_notifications", "Pull down the notification shade") {},

            // Messaging
            tool("reply_notification", "Reply to the notification that triggered this task. This is the FASTEST and most RELIABLE way to reply to incoming messages on WhatsApp, Telegram, Instagram, Messenger, and other messaging apps. It sends the reply directly through the notification without opening any app. ALWAYS try this first before using send_whatsapp or manual screen interaction. If this fails (returns can_reply=false), fall back to send_whatsapp or opening the app manually.") {
                requiredString("text", "The reply message text")
            },
            tool("send_sms", "Send an SMS text message") {
                requiredString("to", "Phone number to send to")
                requiredString("message", "Message text")
            },
            tool("read_sms", "Read SMS messages from inbox") {
                optionalString("from", "Filter by sender phone number")
                optionalNumber("limit", "Max messages to return (default 10)")
            },
            tool("send_whatsapp", "Open WhatsApp chat with a prefilled message. Accepts a contact name OR phone number. NOTE: This PREFILLS the message but does NOT send it — after calling this, you must use click_text to tap the Send button. Prefer reply_notification for replying to incoming messages.") {
                requiredString("to", "Contact name or phone number (with country code)")
                requiredString("message", "Message text")
            },

            // Phone
            tool("get_contacts", "Search phone contacts") {
                optionalString("query", "Search query for contact name")
            },
            tool("get_location", "Get current GPS coordinates") {},
            tool("get_time", "Get current date, time, and day of week") {},
            tool("get_battery", "Get battery level and charging status") {},
            tool("set_clipboard", "Copy text to clipboard") {
                requiredString("text", "Text to copy")
            },
            tool("get_clipboard", "Read current clipboard content") {},
            tool("speak", "Speak text aloud using text-to-speech") {
                requiredString("text", "Text to speak")
            },

            // Memory
            tool("get_conversation", "Retrieve conversation history with a contact") {
                requiredString("contact", "Contact name")
                optionalString("platform", "Platform filter (whatsapp, sms, telegram, etc.)")
            },
            tool("link_contacts", "Link two contacts that are the same person across different platforms. Use this when someone identifies themselves across platforms (e.g. 'hi it's kyle from reddit'). Check the known contacts list in your system prompt to find the matching username.") {
                requiredString("name1", "Contact name on first platform (as it appears in notifications)")
                requiredString("platform1", "First platform (e.g. reddit, whatsapp, telegram)")
                requiredString("name2", "Contact name on second platform")
                requiredString("platform2", "Second platform")
            },
            tool("save_note", "Save arbitrary data for later retrieval") {
                requiredString("key", "Note key/name")
                requiredString("value", "Note content")
            },
            tool("get_note", "Retrieve a previously saved note") {
                requiredString("key", "Note key/name")
            },

            // Task
            tool("task_done", "Mark the current task as complete") {
                requiredString("summary", "Brief summary of what was accomplished")
            },
        )
    }

    // Builder helpers
    private fun tool(
        name: String,
        description: String,
        block: ParamBuilder.() -> Unit
    ): OpenRouterClient.ToolDefinition {
        val builder = ParamBuilder()
        builder.block()

        val parameters = JsonObject().apply {
            addProperty("type", "object")
            add("properties", builder.properties)
            if (builder.required.isNotEmpty()) {
                add("required", JsonArray().apply { builder.required.forEach { add(it) } })
            }
        }

        return OpenRouterClient.ToolDefinition(
            function = OpenRouterClient.FunctionDef(
                name = name,
                description = description,
                parameters = parameters
            )
        )
    }

    class ParamBuilder {
        val properties = JsonObject()
        val required = mutableListOf<String>()

        fun requiredString(name: String, description: String) {
            properties.add(name, JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", description)
            })
            required.add(name)
        }

        fun optionalString(name: String, description: String) {
            properties.add(name, JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", description)
            })
        }

        fun requiredNumber(name: String, description: String) {
            properties.add(name, JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", description)
            })
            required.add(name)
        }

        fun optionalNumber(name: String, description: String) {
            properties.add(name, JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", description)
            })
        }
    }
}
