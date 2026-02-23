package com.agentbridge

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object UiTreeHelper {

    fun dumpUiTree(root: AccessibilityNodeInfo?): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        result.add("tree", nodeToJson(root))
        return result
    }

    private fun nodeToJson(node: AccessibilityNodeInfo): JsonObject {
        val obj = JsonObject()
        obj.addProperty("className", node.className?.toString() ?: "")
        obj.addProperty("text", node.text?.toString() ?: "")
        obj.addProperty("contentDescription", node.contentDescription?.toString() ?: "")
        obj.addProperty("viewId", node.viewIdResourceName ?: "")

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val boundsObj = JsonObject()
        boundsObj.addProperty("left", bounds.left)
        boundsObj.addProperty("top", bounds.top)
        boundsObj.addProperty("right", bounds.right)
        boundsObj.addProperty("bottom", bounds.bottom)
        obj.add("bounds", boundsObj)

        obj.addProperty("isClickable", node.isClickable)
        obj.addProperty("isScrollable", node.isScrollable)
        obj.addProperty("isEnabled", node.isEnabled)
        obj.addProperty("isChecked", node.isChecked)
        obj.addProperty("isFocused", node.isFocused)
        obj.addProperty("isVisibleToUser", node.isVisibleToUser)

        val childCount = node.childCount
        if (childCount > 0) {
            val children = JsonArray()
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                children.add(nodeToJson(child))
                child.recycle()
            }
            obj.add("children", children)
        }

        return obj
    }

    fun getAllText(root: AccessibilityNodeInfo?): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val texts = JsonArray()
        collectText(root, texts)
        result.add("texts", texts)
        return result
    }

    private fun collectText(node: AccessibilityNodeInfo, texts: JsonArray) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank()) {
            val obj = JsonObject()
            if (!text.isNullOrBlank()) obj.addProperty("text", text)
            if (!desc.isNullOrBlank()) obj.addProperty("contentDescription", desc)

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val boundsObj = JsonObject()
            boundsObj.addProperty("left", bounds.left)
            boundsObj.addProperty("top", bounds.top)
            boundsObj.addProperty("right", bounds.right)
            boundsObj.addProperty("bottom", bounds.bottom)
            obj.add("bounds", boundsObj)

            texts.add(obj)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, texts)
            child.recycle()
        }
    }

    fun findByText(root: AccessibilityNodeInfo?, query: String): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val nodes = root.findAccessibilityNodeInfosByText(query)
        val matches = JsonArray()
        for (node in nodes) {
            matches.add(nodeToJson(node))
            node.recycle()
        }
        result.add("matches", matches)
        result.addProperty("count", matches.size())
        return result
    }

    fun findById(root: AccessibilityNodeInfo?, viewId: String): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val matches = JsonArray()
        for (node in nodes) {
            matches.add(nodeToJson(node))
            node.recycle()
        }
        result.add("matches", matches)
        result.addProperty("count", matches.size())
        return result
    }

    fun clickByText(root: AccessibilityNodeInfo?, text: String): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) {
            result.addProperty("success", false)
            result.addProperty("error", "No element found with text: $text")
            return result
        }
        val node = nodes[0]
        val clicked = clickNodeOrParent(node)
        for (n in nodes) n.recycle()
        result.addProperty("success", clicked)
        return result
    }

    fun clickById(root: AccessibilityNodeInfo?, viewId: String): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) {
            result.addProperty("success", false)
            result.addProperty("error", "No element found with id: $viewId")
            return result
        }
        val node = nodes[0]
        val clicked = clickNodeOrParent(node)
        for (n in nodes) n.recycle()
        result.addProperty("success", clicked)
        return result
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val grandparent = parent.parent
            parent.recycle()
            parent = grandparent
        }
        return false
    }

    fun scrollFirstScrollable(root: AccessibilityNodeInfo?, forward: Boolean): JsonObject {
        val result = JsonObject()
        if (root == null) {
            result.addProperty("error", "No root window available")
            return result
        }
        val scrollable = findFirstScrollable(root)
        if (scrollable == null) {
            result.addProperty("success", false)
            result.addProperty("error", "No scrollable element found")
            return result
        }
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrolled = scrollable.performAction(action)
        scrollable.recycle()
        result.addProperty("success", scrolled)
        return result
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }
}
