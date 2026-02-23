package com.agentbridge

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvTcpStatus: TextView
    private lateinit var btnOpenSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvTcpStatus = findViewById(R.id.tvTcpStatus)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = AgentAccessibilityService.instance != null
        if (enabled) {
            tvServiceStatus.text = getString(R.string.service_enabled)
            tvServiceStatus.setBackgroundColor(0xFFC8E6C9.toInt()) // light green
            tvTcpStatus.text = "TCP Server: RUNNING on port 8765"
        } else {
            tvServiceStatus.text = getString(R.string.service_disabled)
            tvServiceStatus.setBackgroundColor(0xFFFFCDD2.toInt()) // light red
            tvTcpStatus.text = "TCP Server: NOT RUNNING (enable service first)"
        }
    }
}
