package com.personal.autobot.ai

import org.json.JSONArray
import org.json.JSONObject

class ActionParser {

    companion object {
        fun parse(rawJson: String): Pair<String, List<ActionStep>> {
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

                    when {
                        // 1. OPEN / LAUNCH APP
                        rawAction.contains("OPEN") || rawAction.contains("LAUNCH") || rawAction.contains("START") -> {
                            val pkg = when {
                                obj.has("package") -> obj.getString("package")
                                obj.has("target") -> obj.getString("target")
                                else -> "com.google.android.youtube"
                            }
                            val resolvedPkg = resolvePackage(pkg)
                            list.add(ActionStep(action = "launch", target = resolvedPkg, durationMs = 4000))
                        }

                        // 2. WAIT / DELAY
                        rawAction.contains("WAIT") || rawAction.contains("DELAY") || rawAction.contains("SLEEP") -> {
                            val durationSec = obj.optDouble("duration", 2.0)
                            val durationMs = (durationSec * 1000).toLong()
                            list.add(ActionStep(action = "delay", durationMs = durationMs))
                        }

                        // 3. SEARCH
                        rawAction.contains("SEARCH") -> {
                            val query = obj.optString("query", obj.optString("target", "trending songs"))
                            // Tap Search button on YouTube/App top bar
                            list.add(ActionStep(action = "click_text", target = "Search", durationMs = 2000))
                            // Type search query
                            list.add(ActionStep(action = "type_text", target = "Search", text = query, durationMs = 2000))
                            // Press keyboard enter / tap search
                            list.add(ActionStep(action = "click_xy", x = 1000f, y = 2200f, durationMs = 3000))
                        }

                        // 4. CLICK FIRST RESULT / PLAY
                        rawAction.contains("FIRST") || rawAction.contains("RESULT") || rawAction.contains("PLAY") -> {
                            // Tap first video result on screen
                            list.add(ActionStep(action = "click_xy", x = 500f, y = 700f, durationMs = 2000))
                        }

                        // 5. CLICK TEXT
                        rawAction.contains("CLICK_TEXT") || rawAction == "CLICK" -> {
                            val target = obj.optString("target", obj.optString("text", ""))
                            list.add(ActionStep(action = "click_text", target = target, durationMs = 1500))
                        }

                        // 6. TYPE TEXT
                        rawAction.contains("TYPE") || rawAction.contains("INPUT") -> {
                            val target = obj.optString("target", "")
                            val text = obj.optString("text", obj.optString("value", ""))
                            list.add(ActionStep(action = "type_text", target = target, text = text, durationMs = 1500))
                        }

                        // 7. CLICK XY
                        rawAction.contains("XY") -> {
                            list.add(
                                ActionStep(
                                    action = "click_xy",
                                    x = obj.optDouble("x", 500.0).toFloat(),
                                    y = obj.optDouble("y", 1000.0).toFloat(),
                                    durationMs = 1500
                                )
                            )
                        }

                        // 8. BACK / HOME
                        rawAction.contains("BACK") -> list.add(ActionStep(action = "back", durationMs = 1000))
                        rawAction.contains("HOME") -> list.add(ActionStep(action = "home", durationMs = 1000))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                reply = "रिस्पॉन्स पार्स करने में समस्या: $clean"
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
