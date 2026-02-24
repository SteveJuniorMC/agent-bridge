package com.agentbridge

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenRouterClient(private val apiKey: String, private val model: String) {

    companion object {
        private const val TAG = "OpenRouterClient"
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): ChatResponse {
        val body = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            if (!tools.isNullOrEmpty()) {
                add("tools", gson.toJsonTree(tools))
            }
        }

        val requestBody = gson.toJson(body).toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/agent-bridge")
            .addHeader("X-Title", "Agent Bridge")
            .post(requestBody)
            .build()

        Log.d(TAG, "Sending request to OpenRouter, model=$model, messages=${messages.size}")

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        if (!response.isSuccessful) {
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw Exception("OpenRouter API error ${response.code}: $responseBody")
        }

        Log.d(TAG, "Response received: ${responseBody.take(200)}")

        return parseResponse(responseBody)
    }

    private fun parseResponse(body: String): ChatResponse {
        val json = gson.fromJson(body, JsonObject::class.java)
        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw Exception("No choices in response")
        }

        val choice = choices[0].asJsonObject
        val message = choice.getAsJsonObject("message")
        val role = message.get("role")?.asString ?: "assistant"
        val content = if (message.has("content") && !message.get("content").isJsonNull)
            message.get("content").asString else null
        val finishReason = if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull)
            choice.get("finish_reason").asString else null

        val toolCalls = mutableListOf<ToolCall>()
        if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull) {
            val tcArray = message.getAsJsonArray("tool_calls")
            for (tc in tcArray) {
                val tcObj = tc.asJsonObject
                val function = tcObj.getAsJsonObject("function")
                toolCalls.add(ToolCall(
                    id = tcObj.get("id").asString,
                    type = tcObj.get("type")?.asString ?: "function",
                    function = FunctionCall(
                        name = function.get("name").asString,
                        arguments = function.get("arguments").asString
                    )
                ))
            }
        }

        val usage = if (json.has("usage")) {
            val u = json.getAsJsonObject("usage")
            Usage(
                promptTokens = u.get("prompt_tokens")?.asInt ?: 0,
                completionTokens = u.get("completion_tokens")?.asInt ?: 0,
                totalTokens = u.get("total_tokens")?.asInt ?: 0
            )
        } else null

        return ChatResponse(
            content = content,
            toolCalls = if (toolCalls.isEmpty()) null else toolCalls,
            finishReason = finishReason,
            usage = usage
        )
    }

    fun testConnection(): Boolean {
        return try {
            val messages = listOf(ChatMessage(role = "user", content = "Say hi"))
            val response = chat(messages)
            response.content != null || response.toolCalls != null
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}")
            false
        }
    }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // Data classes
    data class ChatMessage(
        val role: String,
        val content: String? = null,
        @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
        @SerializedName("tool_call_id") val toolCallId: String? = null,
        val name: String? = null
    )

    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall
    )

    data class FunctionCall(
        val name: String,
        val arguments: String
    )

    data class ToolDefinition(
        val type: String = "function",
        val function: FunctionDef
    )

    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )

    data class ChatResponse(
        val content: String?,
        val toolCalls: List<ToolCall>?,
        val finishReason: String?,
        val usage: Usage?
    )

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}
