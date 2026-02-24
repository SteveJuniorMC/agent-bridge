package com.agentbridge

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView

class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show(text: String, showProgress: Boolean = true) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }

        handler.post {
            try {
                if (overlayView == null) createOverlay()
                overlayView?.findViewById<TextView>(R.id.overlay_text)?.text = text
                overlayView?.findViewById<ProgressBar>(R.id.overlay_progress)?.visibility =
                    if (showProgress) View.VISIBLE else View.GONE
                overlayView?.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay: ${e.message}")
            }
        }
    }

    fun hide() {
        handler.post {
            overlayView?.visibility = View.GONE
        }
    }

    fun destroy() {
        handler.post {
            try {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy overlay: ${e.message}")
            }
        }
    }

    private fun createOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_status, null)
        windowManager.addView(overlayView, params)
    }
}
