package com.agentbridge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpCommandServer(
    private val service: AgentAccessibilityService,
    private val port: Int = 8765
) : Thread("TcpCommandServer") {

    private val TAG = "TcpCommandServer"
    private val gson = Gson()
    @Volatile
    private var running = true
    private var serverSocket: ServerSocket? = null

    override fun run() {
        try {
            serverSocket = ServerSocket(port)
            Log.i(TAG, "TCP server listening on port $port")

            while (running) {
                val client: Socket
                try {
                    client = serverSocket!!.accept()
                } catch (e: Exception) {
                    if (!running) break
                    Log.e(TAG, "Accept failed", e)
                    continue
                }
                handleClient(client)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server error", e)
        } finally {
            serverSocket?.close()
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            val line = reader.readLine() ?: return
            Log.d(TAG, "Received: $line")

            val response = processJsonCommand(line)
            writer.println(response)
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Client handling error", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun processJsonCommand(json: String): String {
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(json, type)
            val cmd = map["cmd"]?.toString() ?: throw IllegalArgumentException("Missing 'cmd' field")
            val result = CommandProcessor.process(service, cmd, map)
            gson.toJson(result)
        } catch (e: Exception) {
            val error = JsonObject()
            error.addProperty("success", false)
            error.addProperty("error", "Parse error: ${e.message}")
            gson.toJson(error)
        }
    }

    fun shutdown() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}
