package com.agentbridge.tools

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.gson.JsonObject
import java.util.Locale

object TtsTools {

    private const val TAG = "TtsTools"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                tts?.language = Locale.getDefault()
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }

    fun speak(context: Context, params: Map<String, Any>): JsonObject {
        val text = params["text"]?.toString() ?: return error("Missing param: text")

        if (tts == null) init(context)

        if (!isInitialized) {
            return error("TTS not initialized yet")
        }

        return try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent_bridge_tts")
            JsonObject().apply {
                addProperty("success", true)
                addProperty("message", "Speaking: ${text.take(50)}")
            }
        } catch (e: Exception) {
            error("TTS failed: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun error(msg: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("error", msg)
        }
    }
}
