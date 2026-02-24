package com.agentbridge.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.AgentForegroundService
import com.agentbridge.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etCustomInstructions: EditText
    private lateinit var switchAgent: SwitchMaterial
    private lateinit var btnSave: Button
    private var suppressToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        etCustomInstructions = findViewById(R.id.etCustomInstructions)
        switchAgent = findViewById(R.id.switchAgent)
        btnSave = findViewById(R.id.btnSave)

        loadSettings()

        // Agent toggle
        switchAgent.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            if (isChecked) {
                AgentForegroundService.start(this)
            } else {
                AgentForegroundService.stop(this)
            }
        }

        // Monitored apps
        findViewById<Button>(R.id.btnMonitoredApps).setOnClickListener {
            startActivity(Intent(this, AppSelectorActivity::class.java))
        }

        // Save button
        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        suppressToggle = true
        switchAgent.isChecked = AgentForegroundService.instance != null
        suppressToggle = false
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model", ""))
        etCustomInstructions.setText(prefs.getString("custom_instructions", ""))
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("api_key", etApiKey.text.toString().trim())
            putString("model", etModel.text.toString().trim())
            putString("custom_instructions", etCustomInstructions.text.toString().trim())
            apply()
        }
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
