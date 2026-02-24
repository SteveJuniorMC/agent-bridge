package com.agentbridge

import android.content.Context
import android.util.Log
import com.agentbridge.db.ConversationDao
import com.agentbridge.db.TaskDao
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class AgentLoop(private val context: Context) {

    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_STEPS = 30
        private const val MIN_STEP_DELAY_MS = 2000L
    }

    data class AgentTask(
        val id: Long,
        val type: TaskType,
        val description: String,
        val contact: String? = null,
        val platform: String? = null,
        val packageName: String? = null,
        val message: String? = null,
        val notificationKey: String? = null
    )

    enum class TaskType { NOTIFICATION, MANUAL }

    interface StatusListener {
        fun onStatusChanged(status: String, showProgress: Boolean = true)
        fun onTaskStarted(task: AgentTask)
        fun onTaskCompleted(task: AgentTask, summary: String)
        fun onTaskFailed(task: AgentTask, error: String)
        fun onQueueChanged(queueSize: Int)
    }

    private val taskQueue = ConcurrentLinkedQueue<AgentTask>()
    private val isRunning = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private var statusListener: StatusListener? = null
    private var loopThread: Thread? = null

    private val prefs = context.getSharedPreferences("agent_bridge_prefs", Context.MODE_PRIVATE)
    private val taskDao = TaskDao(context)
    private val conversationDao = ConversationDao(context)
    private val toolExecutor = ToolExecutor(context)
    private val promptBuilder = SystemPromptBuilder(context)

    fun setStatusListener(listener: StatusListener) {
        this.statusListener = listener
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Agent loop started")
        statusListener?.onStatusChanged("Idle — monitoring notifications", showProgress = false)

        loopThread = Thread {
            while (isRunning.get()) {
                val task = taskQueue.poll()
                if (task != null) {
                    processTask(task)
                    statusListener?.onQueueChanged(taskQueue.size)
                } else {
                    Thread.sleep(500)
                }
            }
        }.apply {
            name = "AgentLoop"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        loopThread?.interrupt()
        loopThread = null
        Log.i(TAG, "Agent loop stopped")
    }

    fun enqueueNotification(contact: String, message: String, platform: String, notificationKey: String?) {
        // Save incoming message to DB
        conversationDao.saveMessage(contact, platformName(platform), "incoming", message)
        conversationDao.saveContact(contact, platform = platformName(platform))

        // Check for prompt injection
        val warnings = SecurityGuard.scanIncomingMessage(message)
        if (warnings.isNotEmpty()) {
            Log.w(TAG, "Prompt injection warnings for message from $contact: $warnings")
        }

        val dbTaskId = taskDao.createTask("Reply to $contact on ${platformName(platform)}: ${message.take(100)}")

        val task = AgentTask(
            id = dbTaskId,
            type = TaskType.NOTIFICATION,
            description = "New message from $contact: ${message.take(200)}",
            contact = contact,
            platform = platformName(platform),
            packageName = platform,
            message = message,
            notificationKey = notificationKey
        )

        taskQueue.add(task)
        statusListener?.onQueueChanged(taskQueue.size)
        Log.i(TAG, "Notification task enqueued: ${task.description}")
    }

    fun enqueueManualTask(description: String): Long {
        val dbTaskId = taskDao.createTask(description)

        val task = AgentTask(
            id = dbTaskId,
            type = TaskType.MANUAL,
            description = description
        )

        // Manual tasks go to front
        val current = taskQueue.toList()
        taskQueue.clear()
        taskQueue.add(task)
        taskQueue.addAll(current)

        statusListener?.onQueueChanged(taskQueue.size)
        Log.i(TAG, "Manual task enqueued: $description")
        return dbTaskId
    }

    val queueSize: Int get() = taskQueue.size
    val isActive: Boolean get() = isRunning.get()

    private fun processTask(task: AgentTask) {
        isProcessing.set(true)
        taskDao.updateTaskStatus(task.id, "running")
        statusListener?.onTaskStarted(task)

        // Pause listener to prevent notification updates from re-triggering
        if (task.type == TaskType.NOTIFICATION) {
            NotificationListener.instance?.paused = true
        }

        // Set notification context on tool executor for reply_notification
        toolExecutor.currentNotificationKey = task.notificationKey
        toolExecutor.currentContact = task.contact
        toolExecutor.currentPlatform = task.platform
        toolExecutor.currentPackageName = task.packageName

        val apiKey = prefs.getString("api_key", null)
        val model = prefs.getString("model", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"

        if (apiKey.isNullOrBlank()) {
            failTask(task, "No API key configured")
            return
        }

        val client = OpenRouterClient(apiKey, model)
        val systemPrompt = promptBuilder.build(
            triggerDescription = task.description,
            contactName = task.contact,
            platform = task.platform
        )

        val messages = mutableListOf<OpenRouterClient.ChatMessage>()
        messages.add(OpenRouterClient.ChatMessage(role = "system", content = systemPrompt))

        // Add trigger context
        val triggerContent = when (task.type) {
            TaskType.NOTIFICATION -> "You received a new message from ${task.contact} on ${task.platform}: \"${task.message}\"\n\nTo reply, use the reply_notification tool — it sends your reply directly through the notification without opening any app. If it fails (can_reply=false), fall back to send_whatsapp or open the app manually."
            TaskType.MANUAL -> "The user asked you to: ${task.description}\n\nDecide what to do next."
        }
        messages.add(OpenRouterClient.ChatMessage(role = "user", content = triggerContent))

        val tools = ToolRegistry.getToolDefinitions()

        var steps = 0
        var done = false

        while (steps < MAX_STEPS && !done && isRunning.get()) {
            steps++
            taskDao.incrementSteps(task.id)

            val queueInfo = if (taskQueue.size > 0) " (${taskQueue.size} queued)" else ""
            statusListener?.onStatusChanged("Step $steps — Thinking...$queueInfo")

            try {
                val response = client.chat(messages, tools)

                // Handle content response
                if (response.content != null) {
                    Log.d(TAG, "AI content: ${response.content.take(200)}")
                }

                // Handle tool calls
                if (response.toolCalls != null && response.toolCalls.isNotEmpty()) {
                    // Add assistant message with tool calls
                    messages.add(OpenRouterClient.ChatMessage(
                        role = "assistant",
                        content = response.content,
                        toolCalls = response.toolCalls
                    ))

                    for (toolCall in response.toolCalls) {
                        val toolName = toolCall.function.name
                        val toolArgs = toolCall.function.arguments

                        Log.d(TAG, "Executing tool: $toolName($toolArgs)")
                        statusListener?.onStatusChanged("Executing: $toolName$queueInfo")

                        val result = toolExecutor.execute(toolName, toolArgs, task.id)
                        Log.d(TAG, "Tool result: ${result.toString().take(200)}")

                        // Add tool result message
                        messages.add(OpenRouterClient.ChatMessage(
                            role = "tool",
                            content = result.toString(),
                            toolCallId = toolCall.id
                        ))

                        // Check if task_done was called
                        if (toolName == "task_done") {
                            done = true
                            val summary = result.get("summary")?.asString ?: "Task completed"
                            completeTask(task, summary)
                        }

                        // Save outgoing message if we typed/sent something
                        // (reply_notification saves its own messages via ToolExecutor)
                        if (toolName == "type_text" && task.contact != null) {
                            val typedText = try {
                                com.google.gson.Gson().fromJson(toolArgs, Map::class.java)["text"]?.toString()
                            } catch (e: Exception) { null }
                            if (typedText != null) {
                                conversationDao.saveMessage(
                                    task.contact, task.platform ?: "unknown", "outgoing", typedText, task.id
                                )
                            }
                        }
                    }
                } else if (response.finishReason == "stop" && response.toolCalls == null) {
                    // AI finished without calling a tool — treat as done
                    done = true
                    val summary = response.content ?: "Task completed"
                    completeTask(task, summary)
                } else {
                    // Add assistant message and continue
                    messages.add(OpenRouterClient.ChatMessage(
                        role = "assistant",
                        content = response.content
                    ))
                    messages.add(OpenRouterClient.ChatMessage(
                        role = "user",
                        content = "Continue. What's your next action?"
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in agent loop step $steps", e)
                failTask(task, "Error: ${e.message}")
                done = true
            }

            // Rate limiting
            if (!done) {
                Thread.sleep(MIN_STEP_DELAY_MS)
            }
        }

        if (!done && steps >= MAX_STEPS) {
            failTask(task, "Max steps ($MAX_STEPS) reached")
        }

        // Dismiss the notification and unpause listener
        if (task.type == TaskType.NOTIFICATION && task.notificationKey != null) {
            NotificationListener.instance?.dismissNotification(task.notificationKey)
        }
        NotificationListener.instance?.paused = false

        client.shutdown()
        isProcessing.set(false)

        if (taskQueue.isEmpty()) {
            statusListener?.onStatusChanged("Idle — monitoring notifications", showProgress = false)
        }
    }

    private fun completeTask(task: AgentTask, summary: String) {
        taskDao.updateTaskStatus(task.id, "completed", summary)
        statusListener?.onTaskCompleted(task, summary)
        Log.i(TAG, "Task completed: $summary")
    }

    private fun failTask(task: AgentTask, error: String) {
        taskDao.updateTaskStatus(task.id, "failed", error)
        statusListener?.onTaskFailed(task, error)
        Log.e(TAG, "Task failed: $error")
    }

    private fun platformName(packageName: String): String {
        return when {
            packageName.contains("whatsapp") -> "whatsapp"
            packageName.contains("messaging") || packageName.contains("sms") -> "sms"
            packageName.contains("telegram") -> "telegram"
            packageName.contains("facebook") || packageName.contains("orca") -> "messenger"
            packageName.contains("instagram") -> "instagram"
            packageName.contains("snapchat") -> "snapchat"
            packageName.contains("reddit") -> "reddit"
            packageName.contains("linkedin") -> "linkedin"
            else -> packageName
        }
    }
}
