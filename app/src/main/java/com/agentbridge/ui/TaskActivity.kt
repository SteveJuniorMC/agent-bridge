package com.agentbridge.ui

import android.os.Bundle
import android.view.MenuItem
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

        val service = AgentForegroundService.instance
        if (service != null) {
            service.enqueueTask(description)
            etTaskDescription.text.clear()
            Toast.makeText(this, "Task enqueued", Toast.LENGTH_SHORT).show()
            loadTasks()
        } else {
            Toast.makeText(this, "Agent is not running. Start it from the main screen.", Toast.LENGTH_LONG).show()
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

            val tv = TextView(this).apply {
                val time = dateFormat.format(Date(task.createdAt))
                text = "$statusEmoji  ${task.description}\n" +
                       "    $time | Steps: ${task.stepsTaken}"
                if (task.result != null) {
                    append("\n    Result: ${task.result.take(100)}")
                }
                textSize = 13f
                setTextColor(0xFF333333.toInt())
                setPadding(0, 8, 0, 8)

                // Status colored left border via background
                setBackgroundColor(0x08000000)
                compoundDrawablePadding = 8
            }

            // Add a small divider
            val divider = android.view.View(this@TaskActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0x1A000000)
            }

            layoutTaskList.addView(tv)
            layoutTaskList.addView(divider)
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
