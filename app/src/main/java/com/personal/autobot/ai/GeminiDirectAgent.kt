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
            You are AutoBot, an autonomous Android AI assistant.
            You must control the user's phone to fulfill their request.
            Screen: [$screenContext]

            Output JSON with "reply" and "steps".
            Format:
            {
              "reply": "Friendly explanation in Hindi",
              "steps": [
                 {"action": "OPEN_APP", "package": "com.google.android.youtube"},
                 {"action": "WAIT", "duration": 2},
                 {"action": "SEARCH", "query": "trending music"},
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
                    return ActionParser.parse(text)
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
                return ActionParser.parse(rawContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair("एरर: ${e.message}", emptyList())
        }
    }
}
