package com.agentbridge

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"

        @Volatile
        var instance: NotificationListener? = null
            private set
    }

    // Store active notifications keyed by notification key
    private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        activeNotifications.clear()
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName

        // Don't process our own notifications
        if (pkg == packageName) return

        // Always store notifications that have a reply action (for reply_notification tool)
        if (hasReplyAction(sbn)) {
            activeNotifications[sbn.key] = sbn
        }

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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        activeNotifications.remove(sbn.key)
    }

    /**
     * Reply to a notification using its RemoteInput action.
     * Returns a result describing what happened.
     */
    fun replyToNotification(notificationKey: String, text: String): ReplyResult {
        val sbn = activeNotifications[notificationKey]
            ?: return ReplyResult(false, "Notification not found or already dismissed")

        val actions = sbn.notification.actions
            ?: return ReplyResult(false, "Notification has no actions")

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs.isNullOrEmpty()) continue

            // Found a reply action
            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (remoteInput in remoteInputs) {
                    bundle.putCharSequence(remoteInput.resultKey, text)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(applicationContext, 0, intent)

                Log.i(TAG, "Reply sent via notification to key=$notificationKey: ${text.take(100)}")

                // Remove from active since the reply has been sent
                activeNotifications.remove(notificationKey)

                ReplyResult(true, "Reply sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification reply", e)
                ReplyResult(false, "Failed to send reply: ${e.message}")
            }
        }

        return ReplyResult(false, "No reply action found in notification")
    }

    /**
     * Find and reply to any notification from a given contact/package combo.
     * Useful when we know the contact but the exact key changed.
     */
    fun replyToContact(contact: String, packageName: String, text: String): ReplyResult {
        for ((key, sbn) in activeNotifications) {
            if (sbn.packageName != packageName) continue
            val title = sbn.notification.extras?.getString(Notification.EXTRA_TITLE) ?: continue
            if (title == contact) {
                return replyToNotification(key, text)
            }
        }
        return ReplyResult(false, "No active notification found from $contact on $packageName")
    }

    /**
     * Check if a notification key has a reply action available.
     */
    fun canReply(notificationKey: String): Boolean {
        val sbn = activeNotifications[notificationKey] ?: return false
        return hasReplyAction(sbn)
    }

    private fun hasReplyAction(sbn: StatusBarNotification): Boolean {
        val actions = sbn.notification.actions ?: return false
        return actions.any { it.remoteInputs?.isNotEmpty() == true }
    }

    data class ReplyResult(val success: Boolean, val message: String)
}
