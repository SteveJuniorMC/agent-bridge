package com.agentbridge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentA11yService"

        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    var currentPackageName: String? = null
        private set
    var currentClassName: String? = null
        private set

    private var tcpServer: TcpCommandServer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")

        tcpServer = TcpCommandServer(this).also {
            it.isDaemon = true
            it.start()
        }
        Log.i(TAG, "TCP server started on port 8765")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { currentPackageName = it }
            event.className?.toString()?.let { currentClassName = it }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpServer?.shutdown()
        tcpServer = null
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        tcpServer?.shutdown()
        tcpServer = null
        Log.i(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }
}
