package com.agentbridge.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.AgentForegroundService
import com.agentbridge.R
import com.agentbridge.db.ConversationDao
import com.agentbridge.db.TaskDao
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var switchAgent: SwitchMaterial
    private lateinit var tvAgentStatus: TextView
    private lateinit var viewStatusIndicator: View
    private lateinit var tvMessagesCount: TextView
    private lateinit var tvRepliesCount: TextView
    private lateinit var layoutRecentMessages: LinearLayout
    private lateinit var tvNoMessages: TextView

    private lateinit var conversationDao: ConversationDao
    private lateinit var taskDao: TaskDao
    private var suppressToggle = false
    private val expandedTasks = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if setup is complete; if not, redirect to SetupWizardActivity
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("setup_complete", false)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        conversationDao = ConversationDao(this)
        taskDao = TaskDao(this)

        // Bind views
        switchAgent = findViewById(R.id.switchAgent)
        tvAgentStatus = findViewById(R.id.tvAgentStatus)
        viewStatusIndicator = findViewById(R.id.viewStatusIndicator)
        tvMessagesCount = findViewById(R.id.tvMessagesCount)
        tvRepliesCount = findViewById(R.id.tvRepliesCount)
        layoutRecentMessages = findViewById(R.id.layoutRecentMessages)
        tvNoMessages = findViewById(R.id.tvNoMessages)

        // Agent toggle
        switchAgent.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            if (isChecked) {
                AgentForegroundService.start(this)
                tvAgentStatus.text = "Active — monitoring"
                viewStatusIndicator.setBackgroundColor(0xFF4CAF50.toInt())
            } else {
                AgentForegroundService.stop(this)
                tvAgentStatus.text = "Inactive"
                viewStatusIndicator.setBackgroundColor(0xFFF44336.toInt())
            }
        }

        // Navigation buttons
        findViewById<View>(R.id.btnConversations).setOnClickListener {
            startActivity(Intent(this, ConversationLogActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::conversationDao.isInitialized) return
        refreshStats()
        updateAgentStatus()
        loadRecentMessages()
    }

    private fun updateAgentStatus() {
        val running = AgentForegroundService.instance != null
        suppressToggle = true
        switchAgent.isChecked = running
        suppressToggle = false
        if (running) {
            tvAgentStatus.text = "Active — monitoring"
            viewStatusIndicator.setBackgroundColor(0xFF4CAF50.toInt())
        } else {
            tvAgentStatus.text = "Inactive"
            viewStatusIndicator.setBackgroundColor(0xFFF44336.toInt())
        }
    }

    private fun refreshStats() {
        val stats = conversationDao.getTodayStats()
        tvMessagesCount.text = stats.messagesHandled.toString()
        tvRepliesCount.text = stats.tasksCompleted.toString()
    }

    private fun loadRecentMessages() {
        layoutRecentMessages.removeAllViews()

        val tasks = taskDao.getAllTasks(15)
        if (tasks.isEmpty()) {
            layoutRecentMessages.addView(tvNoMessages)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        for (task in tasks) {
            val statusEmoji = when (task.status) {
                "completed" -> "[DONE]"
                "running" -> "[RUNNING]"
                "failed" -> "[FAILED]"
                "pending" -> "[PENDING]"
                else -> "[${task.status.uppercase()}]"
            }

            // Task card container
            val cardLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0x08000000)
                setPadding(12, 8, 12, 8)
            }

            // Task summary row
            val tv = TextView(this).apply {
                val time = dateFormat.format(Date(task.createdAt))
                text = "$statusEmoji  ${task.description}\n" +
                       "    $time | Steps: ${task.stepsTaken}"
                if (task.result != null) {
                    append("\n    Result: ${task.result.take(100)}")
                }
                textSize = 13f
                setTextColor(0xFF333333.toInt())
            }
            cardLayout.addView(tv)

            // Log expand indicator
            val hasLogs = task.stepsTaken > 0
            if (hasLogs) {
                val expandHint = TextView(this).apply {
                    text = if (expandedTasks.contains(task.id)) "^ Hide logs" else "v Show logs"
                    textSize = 11f
                    setTextColor(0xFF1565C0.toInt())
                    setPadding(0, 4, 0, 0)
                }
                cardLayout.addView(expandHint)
            }

            // Expanded log container
            val logContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 0)
                visibility = if (expandedTasks.contains(task.id)) View.VISIBLE else View.GONE
            }

            if (expandedTasks.contains(task.id)) {
                populateLogs(logContainer, task.id)
            }
            cardLayout.addView(logContainer)

            // Toggle logs on click
            cardLayout.setOnClickListener {
                if (!hasLogs) return@setOnClickListener
                if (expandedTasks.contains(task.id)) {
                    expandedTasks.remove(task.id)
                    logContainer.visibility = View.GONE
                    logContainer.removeAllViews()
                    if (cardLayout.childCount >= 2) {
                        (cardLayout.getChildAt(1) as? TextView)?.text = "v Show logs"
                    }
                } else {
                    expandedTasks.add(task.id)
                    logContainer.visibility = View.VISIBLE
                    populateLogs(logContainer, task.id)
                    if (cardLayout.childCount >= 2) {
                        (cardLayout.getChildAt(1) as? TextView)?.text = "^ Hide logs"
                    }
                }
            }

            // Divider
            val divider = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0x1A000000)
            }

            layoutRecentMessages.addView(cardLayout)
            layoutRecentMessages.addView(divider)
        }
    }

    private fun populateLogs(container: LinearLayout, taskId: Long) {
        container.removeAllViews()
        val logs = taskDao.getTaskLogs(taskId)

        if (logs.isEmpty()) {
            val noLogs = TextView(this).apply {
                text = "  No logs recorded"
                textSize = 11f
                setTextColor(0xFF999999.toInt())
            }
            container.addView(noLogs)
            return
        }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        for (log in logs) {
            val logColor = when (log.type) {
                "thinking" -> 0xFF1565C0.toInt()
                "tool_call" -> 0xFF2E7D32.toInt()
                "tool_result" -> 0xFF555555.toInt()
                "error" -> 0xFFD32F2F.toInt()
                "cancelled" -> 0xFFFF6F00.toInt()
                "api" -> 0xFF7B1FA2.toInt()
                "info" -> 0xFF455A64.toInt()
                "complete" -> 0xFF2E7D32.toInt()
                else -> 0xFF757575.toInt()
            }

            val typeLabel = when (log.type) {
                "thinking" -> "AI"
                "tool_call" -> "CALL"
                "tool_result" -> "RESULT"
                "error" -> "ERROR"
                "cancelled" -> "CANCEL"
                "api" -> "API"
                "info" -> "INFO"
                "complete" -> "DONE"
                else -> log.type.uppercase()
            }

            val time = timeFormat.format(Date(log.timestamp))
            val prefix = if (log.step > 0) "S${log.step}" else ""

            val logView = TextView(this).apply {
                text = "  $time $prefix [$typeLabel] ${log.content.take(200)}"
                textSize = 10f
                setTextColor(logColor)
                typeface = Typeface.MONOSPACE
                setPadding(0, 2, 0, 2)
            }

            if (log.content.length > 200) {
                var expanded = false
                logView.setOnClickListener {
                    expanded = !expanded
                    logView.text = if (expanded) {
                        "  $time $prefix [$typeLabel] ${log.content}"
                    } else {
                        "  $time $prefix [$typeLabel] ${log.content.take(200)}"
                    }
                }
            }

            container.addView(logView)
        }
    }
}
