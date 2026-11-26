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

class GeminiDirectAgent(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun planActionSteps(userGoal: String, screenContext: String): Pair<String, List<ActionStep>> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are AutoBot, an autonomous Android AI Chatbot that controls the user's phone directly.
            The user talks to you via chat in Hindi, Hinglish, or English.
            Current visible screen elements on phone:
            [$screenContext]

            Respond in JSON with two keys:
            1. "reply": A friendly, helpful conversational reply in Hindi or Hinglish explaining what you are doing (e.g. "ज़रूर! मैं अभी YouTube खोलकर ट्रेंडिंग गाने प्ले कर रहा हूँ।").
            2. "steps": An array of executable action objects to perform on the phone.

            Possible actions in "steps":
            - {"action": "launch", "target": "package_name_e.g_com.google.android.youtube"}
            - {"action": "click_text", "target": "Exact text or button label on screen"}
            - {"action": "type_text", "target": "Input field label or hint", "text": "Text to enter"}
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
            - Gallery/Photos: com.google.android.apps.photos

            Output ONLY valid JSON:
            {"reply": "...", "steps": [...]}
        """.trimIndent()

        // If apiKey starts with sk-or, use OpenRouter, else use Official Google Gemini API
        if (apiKey.startsWith("sk-or")) {
            return@withContext queryOpenRouter(systemPrompt, userGoal)
        } else {
            return@withContext queryGeminiDirect(systemPrompt, userGoal)
        }
    }

    private fun queryGeminiDirect(systemPrompt: String, userGoal: String): Pair<String, List<ActionStep>> {
        // Updated to gemini-3.8-flash (or fallback gemini-1.5-flash) as requested by Google API
        val modelsToTry = listOf("gemini-3.8-flash", "gemini-1.5-flash", "gemini-1.5-pro")

        for (modelName in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemPrompt\n\nUser Message: $userGoal")
                            })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    if (json.has("error")) {
                        val errObj = json.getJSONObject("error")
                        val code = errObj.optInt("code", 0)
                        if (code == 404) {
                            // Try next model in list
                            return@use
                        }
                        return Pair("Gemini Error: ${errObj.optString("message")}", emptyList())
                    }

                    val candidates = json.optJSONArray("candidates") ?: return@use
                    if (candidates.length() == 0) return@use

                    val text = candidates.getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text")
                    return parseJsonResponse(text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair("Gemini API Error: Models available नहीं हैं या API Key अमान्य है।", emptyList())
    }

    private fun queryOpenRouter(systemPrompt: String, userGoal: String): Pair<String, List<ActionStep>> {
        val jsonBody = JSONObject().apply {
            put("model", "google/gemini-2.0-flash-001")
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "User Message: $userGoal")
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
                val body = response.body?.string() ?: return Pair("No response from OpenRouter", emptyList())
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: return Pair("Error: $body", emptyList())
                if (choices.length() == 0) return Pair("No choices returned", emptyList())

                val rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                return parseJsonResponse(rawContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair("एरर: ${e.message}", emptyList())
        }
    }

    private fun parseJsonResponse(rawJson: String): Pair<String, List<ActionStep>> {
        val clean = rawJson.replace("```json", "").replace("```", "").trim()
        val list = mutableListOf<ActionStep>()
        var reply = "टास्क प्रोसेस कर रहा हूँ..."
        try {
            val root = JSONObject(clean)
            reply = root.optString("reply", "टास्क प्रोसेस कर रहा हूँ...")
            val array = root.optJSONArray("steps") ?: JSONArray()
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
            reply = "रिस्पॉन्स: $clean"
        }
        return Pair(reply, list)
    }
}
