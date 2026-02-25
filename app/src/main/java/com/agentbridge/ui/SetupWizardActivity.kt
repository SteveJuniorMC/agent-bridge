package com.agentbridge.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.agentbridge.AgentForegroundService
import com.agentbridge.NotificationListener
import com.agentbridge.AgentAccessibilityService
import com.agentbridge.OpenRouterClient
import com.agentbridge.R
import com.agentbridge.db.TaskDao

class SetupWizardActivity : AppCompatActivity() {

    // Steps
    private lateinit var layoutStep1: View
    private lateinit var layoutStep2: View
    private lateinit var layoutStep3: View
    private lateinit var layoutStep4: View

    // Step 1 - API
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var tvConnectionStatus: TextView

    // Step 2 - Permissions
    private lateinit var cbAccessibility: CheckBox
    private lateinit var cbNotificationListener: CheckBox
    private lateinit var cbOverlay: CheckBox
    private lateinit var cbNotifications: CheckBox
    private lateinit var cbContacts: CheckBox
    private lateinit var cbSms: CheckBox
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnGrantNotificationListener: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantNotifications: Button
    private lateinit var btnGrantContacts: Button
    private lateinit var btnGrantSms: Button

    // Step 3 - App monitoring
    private lateinit var layoutAppToggles: LinearLayout

    // Step 4 - Custom instructions
    private lateinit var etCustomInstructions: EditText

    // Navigation
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button
    private lateinit var btnStartAgent: Button
    private lateinit var tvStepIndicator: TextView

    private var currentStep = 0
    private var connectionTested = false

    private data class AppEntry(
        val packageName: String,
        val displayName: String,
        var enabled: Boolean = true
    )

    private val monitoredApps = mutableListOf<AppEntry>()

    private lateinit var taskDao: TaskDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        taskDao = TaskDao(this)

        initViews()
        setupStep1()
        setupStep2()
        setupStep3()
        setupStep4()
        setupNavigation()
        showStep(0)
    }

    private fun initViews() {
        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)
        layoutStep4 = findViewById(R.id.layoutStep4)

        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        cbAccessibility = findViewById(R.id.cbAccessibility)
        cbNotificationListener = findViewById(R.id.cbNotificationListener)
        cbOverlay = findViewById(R.id.cbOverlay)
        cbNotifications = findViewById(R.id.cbNotifications)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnGrantNotificationListener = findViewById(R.id.btnGrantNotificationListener)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantNotifications = findViewById(R.id.btnGrantNotifications)
        cbContacts = findViewById(R.id.cbContacts)
        cbSms = findViewById(R.id.cbSms)
        btnGrantContacts = findViewById(R.id.btnGrantContacts)
        btnGrantSms = findViewById(R.id.btnGrantSms)

        layoutAppToggles = findViewById(R.id.layoutAppToggles)

        etCustomInstructions = findViewById(R.id.etCustomInstructions)

        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)
        btnStartAgent = findViewById(R.id.btnStartAgent)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)
    }

    // ---- Step 1: API Configuration ----

    private fun setupStep1() {
        btnTestConnection.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val model = etModel.text.toString().trim()
            if (model.isEmpty()) {
                Toast.makeText(this, "Please enter a model name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvConnectionStatus.text = "Testing connection..."
            tvConnectionStatus.setTextColor(0xFF999999.toInt())
            btnTestConnection.isEnabled = false

            Thread {
                val client = OpenRouterClient(apiKey, model)
                val success = client.testConnection()
                client.shutdown()
                runOnUiThread {
                    btnTestConnection.isEnabled = true
                    if (success) {
                        tvConnectionStatus.text = "Connection successful!"
                        tvConnectionStatus.setTextColor(0xFF4CAF50.toInt())
                        connectionTested = true
                    } else {
                        tvConnectionStatus.text = "Connection failed. Check your API key."
                        tvConnectionStatus.setTextColor(0xFFF44336.toInt())
                        connectionTested = false
                    }
                    updateNavigation()
                }
            }.start()
        }
    }

    // ---- Step 2: Permissions ----

    private fun setupStep2() {
        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnGrantNotificationListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        btnGrantNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        btnGrantContacts.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1002)
        }
        btnGrantSms.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS), 1003)
        }
    }

    private fun checkPermissions() {
        // Accessibility service
        val accessibilityEnabled = AgentAccessibilityService.instance != null
        cbAccessibility.isChecked = accessibilityEnabled
        btnGrantAccessibility.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE

        // Notification listener
        val listenerEnabled = NotificationListener.instance != null ||
            isNotificationListenerEnabled()
        cbNotificationListener.isChecked = listenerEnabled
        btnGrantNotificationListener.visibility = if (listenerEnabled) View.GONE else View.VISIBLE

        // Overlay permission
        val overlayEnabled = Settings.canDrawOverlays(this)
        cbOverlay.isChecked = overlayEnabled
        btnGrantOverlay.visibility = if (overlayEnabled) View.GONE else View.VISIBLE

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            cbNotifications.isChecked = notifGranted
            btnGrantNotifications.visibility = if (notifGranted) View.GONE else View.VISIBLE
        } else {
            cbNotifications.isChecked = true
            btnGrantNotifications.visibility = View.GONE
        }

        // Contacts permission
        val contactsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        cbContacts.isChecked = contactsGranted
        btnGrantContacts.visibility = if (contactsGranted) View.GONE else View.VISIBLE

        // SMS permission
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        cbSms.isChecked = smsGranted
        btnGrantSms.visibility = if (smsGranted) View.GONE else View.VISIBLE

        updateNavigation()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, com.agentbridge.NotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun allPermissionsGranted(): Boolean {
        return cbAccessibility.isChecked &&
                cbNotificationListener.isChecked &&
                cbOverlay.isChecked &&
                cbNotifications.isChecked &&
                cbContacts.isChecked &&
                cbSms.isChecked
    }

    // ---- Step 3: App Monitoring ----

    private fun setupStep3() {
        layoutAppToggles.removeAllViews()

        // Get installed non-system apps
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.displayName.lowercase() }

        monitoredApps.clear()
        monitoredApps.addAll(installed)

        // Load existing monitored app state
        val existing = taskDao.getMonitoredApps()
        val existingMap = existing.associate { it.packageName to it.enabled }

        for (app in monitoredApps) {
            app.enabled = existingMap[app.packageName] ?: false

            val cb = CheckBox(this).apply {
                text = "${app.displayName}\n${app.packageName}"
                isChecked = app.enabled
                textSize = 14f
                setPadding(0, 8, 0, 8)
                setOnCheckedChangeListener { _, isChecked ->
                    app.enabled = isChecked
                }
            }
            layoutAppToggles.addView(cb)
        }
    }

    // ---- Step 4: Custom Instructions ----

    private fun setupStep4() {
        etCustomInstructions.setHint(
            "Example: Reply in a friendly, concise tone. Always use English. " +
            "Never share personal information. Sign off messages with my name: Alex."
        )
    }

    // ---- Navigation ----

    private fun setupNavigation() {
        btnBack.setOnClickListener {
            if (currentStep > 0) showStep(currentStep - 1)
        }
        btnNext.setOnClickListener {
            if (currentStep < 3) showStep(currentStep + 1)
        }
        btnStartAgent.setOnClickListener {
            saveAndStart()
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        layoutStep1.visibility = if (step == 0) View.VISIBLE else View.GONE
        layoutStep2.visibility = if (step == 1) View.VISIBLE else View.GONE
        layoutStep3.visibility = if (step == 2) View.VISIBLE else View.GONE
        layoutStep4.visibility = if (step == 3) View.VISIBLE else View.GONE

        tvStepIndicator.text = "Step ${step + 1} of 4"
        btnBack.visibility = if (step > 0) View.VISIBLE else View.INVISIBLE

        if (step == 1) checkPermissions()

        updateNavigation()
    }

    private fun updateNavigation() {
        val isLastStep = currentStep == 3
        btnNext.visibility = if (isLastStep) View.GONE else View.VISIBLE
        btnStartAgent.visibility = if (isLastStep) View.VISIBLE else View.GONE

        val apiKeySet = etApiKey.text.toString().trim().isNotEmpty()
        val modelSet = etModel.text.toString().trim().isNotEmpty()
        val canStart = apiKeySet && modelSet && allPermissionsGranted()
        btnStartAgent.isEnabled = canStart
    }

    private fun saveAndStart() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("api_key", etApiKey.text.toString().trim())
            putString("model", etModel.text.toString().trim())
            putString("custom_instructions", etCustomInstructions.text.toString().trim())
            putBoolean("setup_complete", true)
            apply()
        }

        // Save monitored apps
        for (app in monitoredApps) {
            taskDao.setMonitoredApp(app.packageName, app.displayName, app.enabled)
        }

        // Start the foreground service
        AgentForegroundService.start(this)

        // Navigate to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == 1) {
            checkPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode in listOf(1001, 1002, 1003)) {
            checkPermissions()
        }
    }
}
