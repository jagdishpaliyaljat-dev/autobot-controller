package com.personal.autobot.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ActionStep(
    val action: String, // "launch", "click_text", "click_xy", "type_text", "swipe_down", "swipe_up", "delay", "back", "home"
    val target: String = "",
    val text: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val durationMs: Long = 1000
)

class OpenRouterAgent(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun planActionSteps(userGoal: String, screenContext: String): List<ActionStep> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are AutoBot Mobile AI Agent. The user wants to perform an arbitrary task on their Android phone.
            Current Screen Elements visible:
            [$screenContext]

            Break down the user's command into an executable sequence of steps.
            Output ONLY a valid JSON array of action objects. No markdown formatting, no explanations.
            Possible actions:
            - {"action": "launch", "target": "package_name_e.g_com.google.android.youtube"}
            - {"action": "click_text", "target": "Exact text or button label on screen"}
            - {"action": "type_text", "target": "Input label or hint", "text": "What to type"}
            - {"action": "click_xy", "x": 540, "y": 1200}
            - {"action": "delay", "durationMs": 2000}
            - {"action": "swipe_down"}
            - {"action": "swipe_up"}
            - {"action": "back"}
            - {"action": "home"}

            Common packages:
            - YouTube: com.google.android.youtube
            - Instagram: com.instagram.android
            - WhatsApp: com.whatsapp
            - Chrome: com.android.chrome
            - Settings: com.android.settings
            - Camera: com.google.android.GoogleCamera
            - Gmail: com.google.android.gm
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "google/gemini-2.0-flash-001")
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "User Goal: $userGoal")
                })
            }
            put("messages", messages)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonResponse = JSONObject(body)
                val choices = jsonResponse.optJSONArray("choices") ?: return@withContext emptyList()
                if (choices.length() == 0) return@withContext emptyList()

                val rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val cleanJson = rawContent.replace("```json", "").replace("```", "").trim()
                parseSteps(cleanJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseSteps(jsonString: String): List<ActionStep> {
        val list = mutableListOf<ActionStep>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ActionStep(
                        action = obj.optString("action"),
                        target = obj.optString("target"),
                        text = obj.optString("text"),
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        durationMs = obj.optLong("durationMs", 1000)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
