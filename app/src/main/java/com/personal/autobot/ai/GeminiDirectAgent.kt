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
            You are AutoBot, an Android automation action planner.
            You must translate the user's intent into concrete executable action steps on their phone.
            Current screen elements: [$screenContext]

            Allowed Action Names in "action" field:
            1. "launch" -> Opens app. "target" MUST be package name (e.g. "com.google.android.youtube", "com.whatsapp", "com.instagram.android", "com.android.settings", "com.android.chrome").
            2. "click_text" -> Taps button or element containing visible text in "target".
            3. "type_text" -> Types text into input. "target": label/hint, "text": string to enter.
            4. "click_xy" -> Taps specific coordinate. "x": float, "y": float.
            5. "delay" -> Waits. "durationMs": milliseconds (e.g. 2000).
            6. "press_enter" -> Simulates enter/search key.
            7. "swipe_down" -> Scrolls down.
            8. "swipe_up" -> Scrolls up.
            9. "back" -> Android back button.
            10. "home" -> Android home button.

            RULES:
            - If user asks to play music/song on YouTube:
              Step 1: {"action": "launch", "target": "com.google.android.youtube", "durationMs": 4000}
              Step 2: {"action": "click_text", "target": "Search", "durationMs": 2000}
              Step 3: {"action": "type_text", "target": "Search YouTube", "text": "trending music song", "durationMs": 2000}
              Step 4: {"action": "click_xy", "x": 1000, "y": 2100, "durationMs": 2500} (Keyboard search button)
              Step 5: {"action": "click_xy", "x": 500, "y": 700, "durationMs": 2000} (Tap first video result)

            - If user asks to open any app, use "launch" with correct package name.
            - If user asks general questions without phone actions, steps can be empty.

            OUTPUT FORMAT:
            You must return strictly valid JSON:
            {
              "reply": "Friendly explanation in Hindi explaining what actions you are executing",
              "steps": [ ...list of actions... ]
            }
        """.trimIndent()

        // If apiKey starts with sk-or, use OpenRouter, else use Official Google Gemini API
        if (apiKey.startsWith("sk-or")) {
            return@withContext queryOpenRouter(systemPrompt, userGoal)
        } else {
            return@withContext queryGeminiDirect(systemPrompt, userGoal)
        }
    }

    private fun queryGeminiDirect(systemPrompt: String, userGoal: String): Pair<String, List<ActionStep>> {
        val modelsToTry = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-latest")

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
                    return parseJsonResponse(text)
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
                var action = obj.optString("action")
                var target = obj.optString("target")

                // Normalization of loose AI actions
                if (action == "open_app" || action == "open" || action == "start") {
                    action = "launch"
                    if (target.contains("youtube", ignoreCase = true)) target = "com.google.android.youtube"
                    else if (target.contains("whatsapp", ignoreCase = true)) target = "com.whatsapp"
                    else if (target.contains("insta", ignoreCase = true)) target = "com.instagram.android"
                    else if (target.contains("chrome", ignoreCase = true)) target = "com.android.chrome"
                    else if (target.contains("setting", ignoreCase = true)) target = "com.android.settings"
                }

                if (action == "search") {
                    // Turn "search" into type & click search
                    action = "type_text"
                }

                if (action == "click") {
                    action = "click_text"
                }

                list.add(
                    ActionStep(
                        action = action,
                        target = target,
                        text = obj.optString("text", if (action == "type_text") target else ""),
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        durationMs = obj.optLong("durationMs", 1500)
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
