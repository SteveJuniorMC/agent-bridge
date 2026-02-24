package com.agentbridge.tools

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.agentbridge.AgentAccessibilityService
import com.agentbridge.CommandProcessor
import com.agentbridge.GestureHelper
import com.agentbridge.UiTreeHelper
import com.google.gson.JsonObject

object ScreenTools {

    fun tap(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "tap", params)
    }

    fun longPress(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "long_press", params)
    }

    fun swipe(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "swipe", params)
    }

    fun typeText(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "type", params)
    }

    fun clickText(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "click_text", params)
    }

    fun clickId(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "click_id", params)
    }

    fun scroll(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        val direction = params["direction"]?.toString() ?: "down"
        val forward = direction != "up"
        return CommandProcessor.process(service, "scroll", mapOf("forward" to forward))
    }

    fun dumpUi(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "dump_ui", params)
    }

    fun getScreenText(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "get_text", params)
    }

    fun findElement(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        val text = params["text"]?.toString()
        val id = params["id"]?.toString()
        return when {
            text != null -> CommandProcessor.process(service, "find_text", mapOf("text" to text))
            id != null -> CommandProcessor.process(service, "find_id", mapOf("id" to id))
            else -> error("Must provide 'text' or 'id' parameter")
        }
    }

    fun back(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "back", params)
    }

    fun home(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "home", params)
    }

    fun openNotifications(params: Map<String, Any>): JsonObject {
        val service = AgentAccessibilityService.instance ?: return error("Accessibility service not running")
        return CommandProcessor.process(service, "notifications", params)
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
