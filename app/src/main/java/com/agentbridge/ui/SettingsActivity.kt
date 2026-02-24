package com.agentbridge.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.AgentForegroundService
import com.agentbridge.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var spinnerModel: Spinner
    private lateinit var etCustomInstructions: EditText
    private lateinit var switchAgent: SwitchMaterial
    private lateinit var btnSave: Button

    private val models = arrayOf(
        "google/gemini-2.0-flash-001",
        "anthropic/claude-3.5-sonnet",
        "openai/gpt-4o-mini",
        "meta-llama/llama-3.1-8b-instruct"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        etApiKey = findViewById(R.id.etApiKey)
        spinnerModel = findViewById(R.id.spinnerModel)
        etCustomInstructions = findViewById(R.id.etCustomInstructions)
        switchAgent = findViewById(R.id.switchAgent)
        btnSave = findViewById(R.id.btnSave)

        // Setup model spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = adapter

        loadSettings()

        // Agent toggle
        switchAgent.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AgentForegroundService.start(this)
            } else {
                AgentForegroundService.stop(this)
            }
        }

        // Save button
        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        switchAgent.isChecked = AgentForegroundService.instance != null
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))
        etCustomInstructions.setText(prefs.getString("custom_instructions", ""))

        val savedModel = prefs.getString("model", models[0])
        val modelIndex = models.indexOf(savedModel)
        if (modelIndex >= 0) {
            spinnerModel.setSelection(modelIndex)
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("api_key", etApiKey.text.toString().trim())
            putString("model", models[spinnerModel.selectedItemPosition])
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
