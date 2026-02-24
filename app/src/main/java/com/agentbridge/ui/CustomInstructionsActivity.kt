package com.agentbridge.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.R

class CustomInstructionsActivity : AppCompatActivity() {

    private lateinit var etCustomInstructions: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_instructions)

        val toolbar = findViewById<Toolbar>(R.id.toolbarCustomInstructions)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Custom Instructions"

        etCustomInstructions = findViewById(R.id.etInstructions)
        btnSave = findViewById(R.id.btnSaveInstructions)

        loadInstructions()

        btnSave.setOnClickListener {
            saveInstructions()
        }
    }

    private fun loadInstructions() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        val instructions = prefs.getString("custom_instructions", "") ?: ""
        etCustomInstructions.setText(instructions)
    }

    private fun saveInstructions() {
        val prefs = getSharedPreferences("agent_bridge_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("custom_instructions", etCustomInstructions.text.toString().trim())
            apply()
        }
        Toast.makeText(this, "Custom instructions saved", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
