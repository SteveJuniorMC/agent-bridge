package com.agentbridge.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemTools {

    fun getTime(params: Map<String, Any>): JsonObject {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

        return JsonObject().apply {
            addProperty("success", true)
            addProperty("datetime", sdf.format(Date(now)))
            addProperty("timestamp", now)
            addProperty("day_of_week", dayFormat.format(Date(now)))
        }
    }

    fun getBattery(context: Context, params: Map<String, Any>): JsonObject {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }

            val plugged = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }

            JsonObject().apply {
                addProperty("success", true)
                addProperty("level", percentage)
                addProperty("status", status)
                addProperty("plugged", plugged)
            }
        } catch (e: Exception) {
            error("Failed to get battery info: ${e.message}")
        }
    }

    fun getDeviceInfo(params: Map<String, Any>): JsonObject {
        return JsonObject().apply {
            addProperty("success", true)
            addProperty("manufacturer", Build.MANUFACTURER)
            addProperty("model", Build.MODEL)
            addProperty("android_version", Build.VERSION.RELEASE)
            addProperty("sdk_version", Build.VERSION.SDK_INT)
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
