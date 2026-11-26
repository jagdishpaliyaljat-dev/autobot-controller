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

                    // Fix wrong media provider package
                    if (rawPkg.contains("media", ignoreCase = true) || rawPkg.contains("music", ignoreCase = true)) {
                        rawPkg = "com.google.android.youtube"
                    }

                    when {
                        // 1. OPEN / LAUNCH
                        rawAction.contains("OPEN") || rawAction.contains("LAUNCH") || rawAction.contains("START") -> {
                            val resolvedPkg = resolvePackage(rawPkg)
                            list.add(ActionStep(action = "launch", target = resolvedPkg, durationMs = 4000))
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
                            
                            // If app was not launched yet, ensure YouTube is launched
                            if (list.none { it.action == "launch" }) {
                                list.add(ActionStep(action = "launch", target = "com.google.android.youtube", durationMs = 4000))
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

                // Fallback guarantee: if user asked for music/youtube and list is empty
                if (list.isEmpty() && (originalPrompt.contains("music", ignoreCase = true) || originalPrompt.contains("song", ignoreCase = true) || originalPrompt.contains("youtube", ignoreCase = true))) {
                    list.add(ActionStep(action = "launch", target = "com.google.android.youtube", durationMs = 4000))
                    list.add(ActionStep(action = "click_text", target = "Search", durationMs = 2000))
                    list.add(ActionStep(action = "type_text", target = "Search", text = "trending songs", durationMs = 2000))
                    list.add(ActionStep(action = "click_xy", x = 1000f, y = 2200f, durationMs = 3000))
                    list.add(ActionStep(action = "click_xy", x = 500f, y = 700f, durationMs = 2000))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                reply = "टास्क प्रोसेस कर रहा हूँ..."
            }

            return Pair(reply, list)
        }

        private fun resolvePackage(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("youtube") -> "com.google.android.youtube"
                lower.contains("whatsapp") -> "com.whatsapp"
                lower.contains("insta") -> "com.instagram.android"
                lower.contains("chrome") -> "com.android.chrome"
                lower.contains("setting") -> "com.android.settings"
                lower.contains("camera") -> "com.google.android.GoogleCamera"
                else -> name
            }
        }
    }
}
