package com.agentbridge.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject

object LocationTools {

    fun getLocation(context: Context, params: Map<String, Any>): JsonObject {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return error("Location permission not granted")
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                JsonObject().apply {
                    addProperty("success", true)
                    addProperty("latitude", location.latitude)
                    addProperty("longitude", location.longitude)
                    addProperty("accuracy", location.accuracy)
                    addProperty("timestamp", location.time)
                }
            } else {
                error("Location not available")
            }
        } catch (e: Exception) {
            error("Failed to get location: ${e.message}")
        }
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
