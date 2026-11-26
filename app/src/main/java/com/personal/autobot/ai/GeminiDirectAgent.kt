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
            You are AutoBot, an intelligent and versatile autonomous Android assistant.
            You can control the user's phone to open ANY requested app (WhatsApp, Camera, Instagram, Chrome, Settings, Calculator, YouTube, Maps, etc.) or perform requested screen actions.
            Screen: [$screenContext]

            INSTRUCTIONS:
            1. If the user asks to open or do something in an app, determine the correct Android package name for THAT specific app.
            2. Common apps:
               - WhatsApp: "com.whatsapp"
               - Instagram: "com.instagram.android"
               - Camera: "camera" or "com.google.android.GoogleCamera"
               - Chrome / Web Browser: "com.android.chrome"
               - Settings: "com.android.settings"
               - Calculator: "com.google.android.calculator"
               - Photos / Gallery: "com.google.android.apps.photos"
               - Dial / Phone: "com.google.android.dialer"
               - Maps: "com.google.android.apps.maps"
               - YouTube / Music: "com.google.android.youtube"
            3. Do NOT default to YouTube unless the user specifically asks for YouTube, videos, or songs!
            4. If the user is just saying hello or asking a general question, return an empty steps list [].

            Output ONLY a JSON object with "reply" and "steps".
            Examples:
            User: "WhatsApp kholo"
            {
              "reply": "Main WhatsApp khol raha hoon.",
              "steps": [
                 {"action": "OPEN_APP", "package": "com.whatsapp"}
              ]
            }

            User: "Camera open karo"
            {
              "reply": "Main Camera khol raha hoon.",
              "steps": [
                 {"action": "OPEN_APP", "package": "camera"}
              ]
            }

            User: "YouTube par song chalao"
            {
              "reply": "YouTube par gaana chala raha hoon.",
              "steps": [
                 {"action": "OPEN_APP", "package": "com.google.android.youtube"},
                 {"action": "WAIT", "duration": 2},
                 {"action": "SEARCH", "query": "trending songs"},
                 {"action": "CLICK_FIRST_RESULT"}
              ]
            }
        """.trimIndent()

        if (apiKey.startsWith("sk-or")) {
            return@withContext queryOpenRouter(systemPrompt, userGoal)
        } else {
            return@withContext queryGeminiDirect(systemPrompt, userGoal)
        }
    }

    private fun queryGeminiDirect(systemPrompt: String, userGoal: String): Pair<String, List<ActionStep>> {
        val modelsToTry = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-flash-latest")

        for (modelName in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemPrompt\n\nUser Request: $userGoal")
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
                        if (code == 404 || code == 503) {
                            return@use
                        }
                        return Pair("Gemini Error: ${errObj.optString("message")}", emptyList())
                    }

                    val candidates = json.optJSONArray("candidates") ?: return@use
                    if (candidates.length() == 0) return@use

                    val text = candidates.getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text")
                    return ActionParser.parse(text, userGoal)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair("Gemini API Error: Models available नहीं हैं।", emptyList())
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
                    put("content", "User Request: $userGoal")
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
                return ActionParser.parse(rawContent, userGoal)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair("एरर: ${e.message}", emptyList())
        }
    }
}
