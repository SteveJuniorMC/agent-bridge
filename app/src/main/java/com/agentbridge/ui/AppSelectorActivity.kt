package com.agentbridge.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import com.agentbridge.R
import com.agentbridge.db.TaskDao

class AppSelectorActivity : AppCompatActivity() {

    private lateinit var layoutAppList: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var taskDao: TaskDao

    private data class AppEntry(
        val packageName: String,
        val displayName: String
    )

    private var allApps = listOf<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selector)

        val toolbar = findViewById<Toolbar>(R.id.toolbarAppSelector)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Monitored Apps"

        taskDao = TaskDao(this)
        layoutAppList = findViewById(R.id.layoutAppList)
        etSearch = findViewById(R.id.etSearchApps)

        allApps = getInstalledApps()

        etSearch.addTextChangedListener { text ->
            displayApps(text?.toString()?.trim() ?: "")
        }

        displayApps("")
    }

    private fun getInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun displayApps(query: String) {
        layoutAppList.removeAllViews()

        val monitored = taskDao.getMonitoredApps()
        val monitoredMap = monitored.associate { it.packageName to it.enabled }

        val filtered = if (query.isEmpty()) allApps
            else allApps.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }

        // Show monitored apps first
        val sorted = filtered.sortedByDescending { monitoredMap[it.packageName] == true }

        val tvDescription = TextView(this).apply {
            text = "Select which apps the agent should monitor for incoming messages."
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 16)
        }
        layoutAppList.addView(tvDescription)

        for (app in sorted) {
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

            val divider = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 4; bottomMargin = 4 }
                setBackgroundColor(0x1A000000)
            }
            layoutAppList.addView(divider)
        }

        if (sorted.isEmpty()) {
            val noResults = TextView(this).apply {
                text = "No apps found"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                setPadding(0, 24, 0, 24)
            }
            layoutAppList.addView(noResults)
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
