package com.personal.autobot.ai

import org.json.JSONArray
import org.json.JSONObject

class ActionParser {

    companion object {
        fun parse(rawJson: String, originalPrompt: String = ""): Pair<String, List<ActionStep>> {
            val clean = rawJson.replace("```json", "").replace("```", "").trim()
            val list = mutableListOf<ActionStep>()
            var reply = "काम शुरू कर रहा हूँ..."

            try {
                val root = JSONObject(clean)
                reply = root.optString("reply", "टास्क पूरा कर रहा हूँ...")

                val stepsArray = root.optJSONArray("steps") ?: JSONArray()
                for (i in 0 until stepsArray.length()) {
                    val obj = stepsArray.getJSONObject(i)
                    val rawAction = obj.optString("action").uppercase()
                    var rawPkg = obj.optString("package", obj.optString("target", ""))

                    when {
                        // 1. OPEN / LAUNCH
                        rawAction.contains("OPEN") || rawAction.contains("LAUNCH") || rawAction.contains("START") -> {
                            val resolvedPkg = resolvePackage(rawPkg, originalPrompt)
                            list.add(ActionStep(action = "launch", target = resolvedPkg, durationMs = 3000))
                        }

                        // 2. WAIT / DELAY
                        rawAction.contains("WAIT") || rawAction.contains("DELAY") || rawAction.contains("SLEEP") -> {
                            val durationSec = obj.optDouble("duration", 2.0)
                            list.add(ActionStep(action = "delay", durationMs = (durationSec * 1000).toLong()))
                        }

                        // 3. SEARCH or PLAY MEDIA
                        rawAction.contains("SEARCH") || rawAction.contains("PLAY") -> {
                            var query = obj.optString("query", obj.optString("target", ""))
                            if (query.isEmpty() || query == "null") query = "trending music songs"
                            
                            // Only launch YouTube if no app has been launched AND the user specifically asked for media/video/music
                            if (list.none { it.action == "launch" } && isMediaPrompt(originalPrompt)) {
                                list.add(ActionStep(action = "launch", target = "com.google.android.youtube", durationMs = 3500))
                            }
                            // Search tap
                            list.add(ActionStep(action = "click_text", target = "Search", durationMs = 2000))
                            // Type query
                            list.add(ActionStep(action = "type_text", target = "Search", text = query, durationMs = 2000))
                            // Press search
                            list.add(ActionStep(action = "click_xy", x = 1000f, y = 2200f, durationMs = 3000))
                            // Tap first video result
                            list.add(ActionStep(action = "click_xy", x = 500f, y = 700f, durationMs = 2000))
                        }

                        // 4. CLICK FIRST RESULT
                        rawAction.contains("FIRST") || rawAction.contains("RESULT") -> {
                            list.add(ActionStep(action = "click_xy", x = 500f, y = 700f, durationMs = 2000))
                        }

                        // 5. CLICK
                        rawAction.contains("CLICK") -> {
                            val target = obj.optString("target", obj.optString("text", ""))
                            list.add(ActionStep(action = "click_text", target = target, durationMs = 1500))
                        }

                        // 6. TYPE
                        rawAction.contains("TYPE") -> {
                            val target = obj.optString("target", "")
                            val text = obj.optString("text", obj.optString("value", ""))
                            list.add(ActionStep(action = "type_text", target = target, text = text, durationMs = 1500))
                        }

                        // 7. BACK / HOME
                        rawAction.contains("BACK") -> list.add(ActionStep(action = "back", durationMs = 1000))
                        rawAction.contains("HOME") -> list.add(ActionStep(action = "home", durationMs = 1000))
                    }
                }

                // If user specifically asked for an app by voice/text but steps is empty, detect intent directly
                if (list.isEmpty()) {
                    val directApp = detectDirectApp(originalPrompt)
                    if (directApp != null) {
                        list.add(ActionStep(action = "launch", target = directApp, durationMs = 3000))
                        reply = "मैं ऐप खोल रहा हूँ..."
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                reply = "टास्क प्रोसेस कर रहा हूँ..."
            }

            return Pair(reply, list)
        }

        private fun isMediaPrompt(prompt: String): Boolean {
            val lower = prompt.lowercase()
            return lower.contains("music") || lower.contains("song") || lower.contains("youtube") || lower.contains("video") || lower.contains("gana")
        }

        private fun detectDirectApp(prompt: String): String? {
            val lower = prompt.lowercase()
            return when {
                lower.contains("whatsapp") -> "com.whatsapp"
                lower.contains("instagram") || lower.contains("insta") -> "com.instagram.android"
                lower.contains("camera") -> "camera"
                lower.contains("chrome") || lower.contains("browser") -> "com.android.chrome"
                lower.contains("setting") -> "com.android.settings"
                lower.contains("calculator") || lower.contains("hisab") -> "calculator"
                lower.contains("map") -> "com.google.android.apps.maps"
                lower.contains("youtube") -> "com.google.android.youtube"
                lower.contains("photo") || lower.contains("gallery") -> "gallery"
                lower.contains("dial") || lower.contains("phone") || lower.contains("call") -> "dialer"
                else -> null
            }
        }

        fun resolvePackage(name: String, prompt: String = ""): String {
            val lower = (name + " " + prompt).lowercase()
            return when {
                lower.contains("whatsapp") -> "com.whatsapp"
                lower.contains("instagram") || lower.contains("insta") -> "com.instagram.android"
                lower.contains("youtube") -> "com.google.android.youtube"
                lower.contains("chrome") || lower.contains("browser") -> "com.android.chrome"
                lower.contains("setting") -> "com.android.settings"
                lower.contains("camera") -> "camera"
                lower.contains("calculator") -> "calculator"
                lower.contains("gallery") || lower.contains("photos") -> "gallery"
                lower.contains("dialer") || lower.contains("dial") || lower.contains("phone") -> "dialer"
                lower.contains("map") -> "com.google.android.apps.maps"
                lower.contains("telegram") -> "org.telegram.messenger"
                name.isNotBlank() && name.contains(".") -> name
                else -> name
            }
        }
    }
}
