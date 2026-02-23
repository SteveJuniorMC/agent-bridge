package com.agentbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

object GestureHelper {

    private const val TAG = "GestureHelper"

    fun tap(service: AccessibilityService, x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "tap($x, $y) completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "tap($x, $y) cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    fun longPress(service: AccessibilityService, x: Float, y: Float, duration: Long = 1000L, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "longPress($x, $y, $duration) completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "longPress($x, $y, $duration) cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    fun swipe(service: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300L, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "swipe($x1,$y1 -> $x2,$y2) completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "swipe($x1,$y1 -> $x2,$y2) cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    fun pinch(service: AccessibilityService, centerX: Float, centerY: Float, startDist: Float, endDist: Float, duration: Long = 500L, callback: ((Boolean) -> Unit)? = null) {
        val path1 = Path().apply {
            moveTo(centerX - startDist / 2, centerY)
            lineTo(centerX - endDist / 2, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startDist / 2, centerY)
            lineTo(centerX + endDist / 2, centerY)
        }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0L, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "pinch completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "pinch cancelled")
                callback?.invoke(false)
            }
        }, null)
    }
}
