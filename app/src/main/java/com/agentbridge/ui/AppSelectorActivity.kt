package com.agentbridge.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.R
import com.agentbridge.db.TaskDao

class AppSelectorActivity : AppCompatActivity() {

    private lateinit var layoutAppList: LinearLayout
    private lateinit var taskDao: TaskDao

    private data class AppEntry(
        val packageName: String,
        val displayName: String
    )

    private val defaultApps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"),
        AppEntry("com.google.android.apps.messaging", "Messages"),
        AppEntry("org.telegram.messenger", "Telegram"),
        AppEntry("com.instagram.android", "Instagram"),
        AppEntry("com.facebook.orca", "Messenger"),
        AppEntry("com.reddit.frontpage", "Reddit")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selector)

        val toolbar = findViewById<Toolbar>(R.id.toolbarAppSelector)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Monitored Apps"

        taskDao = TaskDao(this)
        layoutAppList = findViewById(R.id.layoutAppList)

        loadApps()
    }

    private fun loadApps() {
        layoutAppList.removeAllViews()

        // Load current state from DB
        val monitored = taskDao.getMonitoredApps()
        val monitoredMap = monitored.associate { it.packageName to it.enabled }

        // Add a description header
        val tvDescription = TextView(this).apply {
            text = "Select which messaging apps the agent should monitor for incoming messages."
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 24)
        }
        layoutAppList.addView(tvDescription)

        for (app in defaultApps) {
            val isEnabled = monitoredMap[app.packageName] ?: false

            val cb = CheckBox(this).apply {
                text = "${app.displayName}\n${app.packageName}"
                isChecked = isEnabled
                textSize = 15f
                setPadding(0, 12, 0, 12)
                setOnCheckedChangeListener { _, isChecked ->
                    taskDao.setMonitoredApp(app.packageName, app.displayName, isChecked)
                }
            }
            layoutAppList.addView(cb)

            // Divider
            val divider = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0x1A000000)
            }
            layoutAppList.addView(divider)
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
