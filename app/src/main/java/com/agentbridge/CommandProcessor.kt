package com.agentbridge

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject

object CommandProcessor {

    private const val TAG = "CommandProcessor"

    fun process(service: AgentAccessibilityService, cmd: String, params: Map<String, Any?>): JsonObject {
        Log.d(TAG, "Processing command: $cmd with params: $params")

        return when (cmd) {
            // Gestures
            "tap" -> {
                val x = toFloat(params["x"]) ?: return errorResult("Missing param: x")
                val y = toFloat(params["y"]) ?: return errorResult("Missing param: y")
                GestureHelper.tap(service, x, y)
                successResult("tap dispatched at ($x, $y)")
            }
            "long_press" -> {
                val x = toFloat(params["x"]) ?: return errorResult("Missing param: x")
                val y = toFloat(params["y"]) ?: return errorResult("Missing param: y")
                val duration = toLong(params["duration"]) ?: 1000L
                GestureHelper.longPress(service, x, y, duration)
                successResult("long_press dispatched at ($x, $y) for ${duration}ms")
            }
            "swipe" -> {
                val x1 = toFloat(params["x1"]) ?: return errorResult("Missing param: x1")
                val y1 = toFloat(params["y1"]) ?: return errorResult("Missing param: y1")
                val x2 = toFloat(params["x2"]) ?: return errorResult("Missing param: x2")
                val y2 = toFloat(params["y2"]) ?: return errorResult("Missing param: y2")
                val duration = toLong(params["duration"]) ?: 300L
                GestureHelper.swipe(service, x1, y1, x2, y2, duration)
                successResult("swipe dispatched ($x1,$y1)->($x2,$y2)")
            }
            "pinch" -> {
                val cx = toFloat(params["centerX"]) ?: return errorResult("Missing param: centerX")
                val cy = toFloat(params["centerY"]) ?: return errorResult("Missing param: centerY")
                val startDist = toFloat(params["startDist"]) ?: return errorResult("Missing param: startDist")
                val endDist = toFloat(params["endDist"]) ?: return errorResult("Missing param: endDist")
                GestureHelper.pinch(service, cx, cy, startDist, endDist)
                successResult("pinch dispatched")
            }

            // Element interaction
            "click_text" -> {
                val text = params["text"]?.toString() ?: return errorResult("Missing param: text")
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.clickByText(root, text)
                root?.recycle()
                result
            }
            "click_id" -> {
                val id = params["id"]?.toString() ?: return errorResult("Missing param: id")
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.clickById(root, id)
                root?.recycle()
                result
            }
            "type" -> {
                val text = params["text"]?.toString() ?: return errorResult("Missing param: text")
                val root = service.rootInActiveWindow
                if (root == null) return errorResult("No root window")
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused == null) {
                    root.recycle()
                    return errorResult("No focused input field")
                }
                val bundle = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
                val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                focused.recycle()
                root.recycle()
                val result = JsonObject()
                result.addProperty("success", success)
                result
            }
            "scroll" -> {
                val forward = params["forward"]?.toString()?.toBooleanStrictOrNull() ?: true
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.scrollFirstScrollable(root, forward)
                root?.recycle()
                result
            }

            // Navigation
            "back" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                successResult("back performed")
            }
            "home" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                successResult("home performed")
            }
            "recents" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                successResult("recents performed")
            }
            "notifications" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                successResult("notifications performed")
            }
            "quick_settings" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                successResult("quick_settings performed")
            }
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    successResult("lock_screen performed")
                } else {
                    errorResult("lock_screen requires API 28+")
                }
            }
            "screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    successResult("screenshot performed")
                } else {
                    errorResult("screenshot requires API 28+")
                }
            }

            // Screen reading
            "dump_ui" -> {
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.dumpUiTree(root)
                root?.recycle()
                result
            }
            "get_text" -> {
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.getAllText(root)
                root?.recycle()
                result
            }
            "get_foreground" -> {
                val result = JsonObject()
                result.addProperty("packageName", service.currentPackageName ?: "unknown")
                result.addProperty("className", service.currentClassName ?: "unknown")
                result
            }
            "find_text" -> {
                val query = params["text"]?.toString() ?: return errorResult("Missing param: text")
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.findByText(root, query)
                root?.recycle()
                result
            }
            "find_id" -> {
                val id = params["id"]?.toString() ?: return errorResult("Missing param: id")
                val root = service.rootInActiveWindow
                val result = UiTreeHelper.findById(root, id)
                root?.recycle()
                result
            }

            else -> errorResult("Unknown command: $cmd")
        }
    }

    private fun successResult(message: String): JsonObject {
        val result = JsonObject()
        result.addProperty("success", true)
        result.addProperty("message", message)
        return result
    }

    private fun errorResult(message: String): JsonObject {
        val result = JsonObject()
        result.addProperty("success", false)
        result.addProperty("error", message)
        return result
    }

    private fun toFloat(value: Any?): Float? {
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }

    private fun toLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}
