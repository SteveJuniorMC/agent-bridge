package com.agentbridge.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.AgentForegroundService
import com.agentbridge.R
import com.agentbridge.db.ConversationDao
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var switchAgent: SwitchMaterial
    private lateinit var tvAgentStatus: TextView
    private lateinit var viewStatusIndicator: View
    private lateinit var tvMessagesCount: TextView
    private lateinit var tvTasksCount: TextView
    private lateinit var layoutActivityFeed: LinearLayout
    private lateinit var tvNoActivity: TextView

    private lateinit var conversationDao: ConversationDao

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

        // Bind views
        switchAgent = findViewById(R.id.switchAgent)
        tvAgentStatus = findViewById(R.id.tvAgentStatus)
        viewStatusIndicator = findViewById(R.id.viewStatusIndicator)
        tvMessagesCount = findViewById(R.id.tvMessagesCount)
        tvTasksCount = findViewById(R.id.tvTasksCount)
        layoutActivityFeed = findViewById(R.id.layoutActivityFeed)
        tvNoActivity = findViewById(R.id.tvNoActivity)

        // Agent toggle
        switchAgent.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AgentForegroundService.start(this)
            } else {
                AgentForegroundService.stop(this)
            }
            updateAgentStatus()
        }

        // Navigation buttons
        findViewById<View>(R.id.btnNewTask).setOnClickListener {
            startActivity(Intent(this, TaskActivity::class.java))
        }
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
        loadActivityFeed()
    }

    private fun updateAgentStatus() {
        val running = AgentForegroundService.instance != null
        switchAgent.isChecked = running
        if (running) {
            tvAgentStatus.text = "Active — monitoring"
            viewStatusIndicator.setBackgroundColor(0xFF4CAF50.toInt()) // green
        } else {
            tvAgentStatus.text = "Inactive"
            viewStatusIndicator.setBackgroundColor(0xFFF44336.toInt()) // red
        }
    }

    private fun refreshStats() {
        val stats = conversationDao.getTodayStats()
        tvMessagesCount.text = stats.messagesHandled.toString()
        tvTasksCount.text = stats.tasksCompleted.toString()
    }

    private fun loadActivityFeed() {
        // Remove all views except the "no activity" placeholder
        layoutActivityFeed.removeAllViews()

        val entries = conversationDao.getRecentAuditLog(15)
        if (entries.isEmpty()) {
            layoutActivityFeed.addView(tvNoActivity)
            return
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        for (entry in entries) {
            val tv = TextView(this).apply {
                val time = dateFormat.format(Date(entry.timestamp))
                val blockedTag = if (entry.blocked) " [BLOCKED]" else ""
                text = "$time  ${entry.toolName}$blockedTag"
                if (entry.result != null) {
                    append("\n    ${entry.result.take(80)}")
                }
                textSize = 12f
                setTextColor(if (entry.blocked) 0xFFF44336.toInt() else 0xFF666666.toInt())
                setPadding(0, 4, 0, 4)
            }
            layoutActivityFeed.addView(tv)
        }
    }
}
