package com.agentbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import java.io.File

class CommandReceiver : BroadcastReceiver() {

    private val TAG = "CommandReceiver"
    private val gson = Gson()

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")
        if (cmd.isNullOrBlank()) {
            Log.w(TAG, "No 'cmd' extra in broadcast")
            return
        }

        val service = AgentAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "AccessibilityService not running")
            writeResult("""{"success":false,"error":"Accessibility service not running"}""")
            return
        }

        val params = mutableMapOf<String, Any?>()
        params["cmd"] = cmd
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                if (key != "cmd") {
                    params[key] = extras.get(key)
                }
            }
        }

        Log.d(TAG, "Received broadcast: cmd=$cmd, params=$params")
        val result = CommandProcessor.process(service, cmd, params)
        val json = gson.toJson(result)
        Log.d(TAG, "Result: $json")
        writeResult(json)
    }

    private fun writeResult(json: String) {
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "agent-bridge"
            )
            dir.mkdirs()
            val file = File(dir, "last_result.json")
            file.writeText(json)
            Log.d(TAG, "Result written to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write result file", e)
        }
    }
}
