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

    companion object {
        val KNOWN_MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",      // Signal
            "com.google.android.apps.messaging", // Google Messages
            "com.facebook.orca",                // Messenger
            "com.instagram.android",
            "com.viber.voip",
            "jp.naver.line.android",            // Line
            "com.discord",
            "com.slack",
            "com.snapchat.android",
            "com.skype.raider",
            "com.kakao.talk",
            "com.tencent.mm",                   // WeChat
            "com.google.android.apps.dynamite", // Google Chat
            "com.microsoft.teams",
        )
    }

    private lateinit var layoutAppList: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var taskDao: TaskDao

    private data class AppEntry(
        val packageName: String,
        val displayName: String
    )

    private var supportedApps = listOf<AppEntry>()
    private var otherApps = listOf<AppEntry>()

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

        val allApps = getInstalledApps()
        supportedApps = allApps.filter { it.packageName in KNOWN_MESSAGING_PACKAGES }
        otherApps = allApps.filter { it.packageName !in KNOWN_MESSAGING_PACKAGES }

        etSearch.addTextChangedListener { text ->
            displayApps(text?.toString()?.trim() ?: "")
        }

        displayApps("")
    }

    private fun getInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName in KNOWN_MESSAGING_PACKAGES }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun displayApps(query: String) {
        layoutAppList.removeAllViews()

        val monitored = taskDao.getMonitoredApps()
        val monitoredMap = monitored.associate { it.packageName to it.enabled }

        val filteredSupported = filterApps(supportedApps, query)
        val filteredOther = filterApps(otherApps, query)

        // Supported Apps section
        if (filteredSupported.isNotEmpty()) {
            addSectionHeader("Supported Apps")
            addDescription("These messaging apps support notification replies.")
            for (app in filteredSupported.sortedByDescending { monitoredMap[it.packageName] == true }) {
                addAppCheckbox(app, monitoredMap[app.packageName] ?: false)
            }
        }

        // Other Apps section
        if (filteredOther.isNotEmpty()) {
            addSectionHeader("Other Apps")
            addDescription("These apps may not support notification replies and might not work reliably.")
            for (app in filteredOther.sortedByDescending { monitoredMap[it.packageName] == true }) {
                addAppCheckbox(app, monitoredMap[app.packageName] ?: false)
            }
        }

        if (filteredSupported.isEmpty() && filteredOther.isEmpty()) {
            val noResults = TextView(this).apply {
                text = "No apps found"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                setPadding(0, 24, 0, 24)
            }
            layoutAppList.addView(noResults)
        }
    }

    private fun filterApps(apps: List<AppEntry>, query: String): List<AppEntry> {
        if (query.isEmpty()) return apps
        return apps.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun addSectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 4)
        }
        layoutAppList.addView(header)
    }

    private fun addDescription(text: String) {
        val desc = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 8)
        }
        layoutAppList.addView(desc)
    }

    private fun addAppCheckbox(app: AppEntry, isEnabled: Boolean) {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
