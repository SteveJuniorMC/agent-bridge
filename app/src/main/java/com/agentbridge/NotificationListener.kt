package com.agentbridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"

        @Volatile
        var instance: NotificationListener? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName

        // Don't process our own notifications
        if (pkg == packageName) return

        // Check if this app is monitored
        val foregroundService = AgentForegroundService.instance
        if (foregroundService == null || !foregroundService.isAppMonitored(pkg)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Skip group summaries
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Skip ongoing notifications (media players, etc.)
        if (sbn.isOngoing) return

        // Skip empty or very short messages
        if (text.isBlank() || text.length < 2) return

        Log.i(TAG, "New message from $title on $pkg: ${text.take(100)}")

        foregroundService.handleIncomingMessage(
            contact = title,
            message = text,
            platform = pkg,
            notificationKey = sbn.key
        )
    }
}
