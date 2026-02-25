package com.agentbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.agentbridge.db.TaskDao
import com.agentbridge.ui.MainActivity

class AgentForegroundService : Service(), AgentLoop.StatusListener {

    companion object {
        private const val TAG = "AgentForegroundService"
        private const val CHANNEL_ID = "agent_bridge_service"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: AgentForegroundService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var agentLoop: AgentLoop
    private lateinit var overlayManager: OverlayManager
    private lateinit var taskDao: TaskDao

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        taskDao = TaskDao(this)
        agentLoop = AgentLoop(this)
        agentLoop.setStatusListener(this)
        overlayManager = OverlayManager(this)
        overlayManager.onStopClicked = {
            Log.i(TAG, "Stop button pressed")
            agentLoop.cancelCurrentTask()
        }
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Agent Bridge is monitoring...")
        startForeground(NOTIFICATION_ID, notification)
        agentLoop.start()
        overlayManager.show("Idle — monitoring notifications", showProgress = false)
        Log.i(TAG, "Foreground service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        agentLoop.stop()
        overlayManager.destroy()
        instance = null
        Log.i(TAG, "Foreground service destroyed")
    }

    // Public API
    fun handleIncomingMessage(contact: String, message: String, platform: String, notificationKey: String?) {
        agentLoop.enqueueNotification(contact, message, platform, notificationKey)
    }

    fun enqueueTask(description: String): Long {
        return agentLoop.enqueueManualTask(description)
    }

    fun isAppMonitored(packageName: String): Boolean {
        return taskDao.isAppMonitored(packageName)
    }

    val isAgentActive: Boolean get() = agentLoop.isActive
    val queueSize: Int get() = agentLoop.queueSize

    // StatusListener callbacks
    override fun onStatusChanged(status: String, showProgress: Boolean) {
        overlayManager.show(status, showProgress, showStop = showProgress)
        updateNotification(status)
    }

    override fun onTaskStarted(task: AgentLoop.AgentTask) {
        val status = when (task.type) {
            AgentLoop.TaskType.NOTIFICATION -> "Replying to ${task.contact} on ${task.platform}..."
            AgentLoop.TaskType.MANUAL -> "Working: ${task.description.take(40)}..."
        }
        overlayManager.show(status, showStop = true)
        updateNotification(status)
    }

    override fun onTaskCompleted(task: AgentLoop.AgentTask, summary: String) {
        val status = "Done — ${summary.take(50)}"
        overlayManager.show(status, showProgress = false)
        updateNotification(status)
    }

    override fun onTaskFailed(task: AgentLoop.AgentTask, error: String) {
        overlayManager.show("Failed: ${error.take(50)}", showProgress = false)
        updateNotification("Task failed")
    }

    override fun onQueueChanged(queueSize: Int) {
        // Queue info is embedded in status messages
    }

    // Notification management
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent Bridge Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the AI agent running in the background"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Agent Bridge")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
