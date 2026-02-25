package com.agentbridge.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.AgentForegroundService
import com.agentbridge.R
import com.agentbridge.db.TaskDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskActivity : AppCompatActivity() {

    private lateinit var etTaskDescription: EditText
    private lateinit var btnSubmitTask: Button
    private lateinit var layoutTaskList: LinearLayout
    private lateinit var taskDao: TaskDao
    private val expandedTasks = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        val toolbar = findViewById<Toolbar>(R.id.toolbarTask)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Tasks"

        taskDao = TaskDao(this)

        etTaskDescription = findViewById(R.id.etTaskDescription)
        btnSubmitTask = findViewById(R.id.btnSubmitTask)
        layoutTaskList = findViewById(R.id.layoutTaskList)

        btnSubmitTask.setOnClickListener {
            submitTask()
        }

        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun submitTask() {
        val description = etTaskDescription.text.toString().trim()
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a task description", Toast.LENGTH_SHORT).show()
            return
        }

        // Start service if not running
        if (AgentForegroundService.instance == null) {
            AgentForegroundService.start(this)
            // Wait briefly for service to initialize
            Thread {
                Thread.sleep(1000)
                runOnUiThread { enqueueAndClear(description) }
            }.start()
        } else {
            enqueueAndClear(description)
        }
    }

    private fun enqueueAndClear(description: String) {
        val service = AgentForegroundService.instance
        if (service != null) {
            service.enqueueTask(description)
            etTaskDescription.text.clear()
            Toast.makeText(this, "Task enqueued", Toast.LENGTH_SHORT).show()
            loadTasks()
        } else {
            Toast.makeText(this, "Failed to start agent service", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadTasks() {
        layoutTaskList.removeAllViews()

        val tasks = taskDao.getAllTasks()
        if (tasks.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No tasks yet"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                setPadding(0, 32, 0, 32)
            }
            layoutTaskList.addView(emptyView)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        for (task in tasks) {
            val statusColor = when (task.status) {
                "completed" -> 0xFF4CAF50.toInt()
                "running" -> 0xFF2196F3.toInt()
                "failed" -> 0xFFF44336.toInt()
                "pending" -> 0xFFFF9800.toInt()
                else -> 0xFF999999.toInt()
            }

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
                compoundDrawablePadding = 8
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
                    // Update hint
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
            val divider = View(this@TaskActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0x1A000000)
            }

            layoutTaskList.addView(cardLayout)
            layoutTaskList.addView(divider)
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
                "thinking" -> 0xFF1565C0.toInt()  // blue
                "tool_call" -> 0xFF2E7D32.toInt()  // green
                "tool_result" -> 0xFF555555.toInt() // dark gray
                "error" -> 0xFFD32F2F.toInt()       // red
                "cancelled" -> 0xFFFF6F00.toInt()   // orange
                "api" -> 0xFF7B1FA2.toInt()         // purple
                "info" -> 0xFF455A64.toInt()         // blue-gray
                "complete" -> 0xFF2E7D32.toInt()    // green
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

            // Make log entries expandable on click if content is long
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
