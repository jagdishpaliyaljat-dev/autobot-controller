package com.personal.autobot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.autobot.ai.ActionStep

class TaskSchedulerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = AutoBotAccessibilityService.instance ?: return
        val targetPackage = intent.getStringExtra("PACKAGE_NAME") ?: "com.google.android.youtube"

        if (targetPackage.contains("youtube")) {
            val youtubeSteps = listOf(
                ActionStep(action = "launch", target = "com.google.android.youtube", durationMs = 4000),
                ActionStep(action = "click_xy", x = 540f, y = 2180f, durationMs = 2000),
                ActionStep(action = "click_text", target = "Upload a video", durationMs = 2500),
                ActionStep(action = "click_xy", x = 200f, y = 600f, durationMs = 2000),
                ActionStep(action = "click_text", target = "Next", durationMs = 2000),
                ActionStep(action = "type_text", target = "Add details", text = "Auto Scheduled Video #shorts", durationMs = 2000),
                ActionStep(action = "click_text", target = "Upload Short", durationMs = 1500)
            )
            service.executeAiPlan(youtubeSteps)
        } else {
            val instaSteps = listOf(
                ActionStep(action = "launch", target = "com.instagram.android", durationMs = 4000),
                ActionStep(action = "click_text", target = "New post", durationMs = 2500),
                ActionStep(action = "click_xy", x = 200f, y = 750f, durationMs = 2000),
                ActionStep(action = "click_text", target = "Next", durationMs = 2000),
                ActionStep(action = "click_text", target = "Next", durationMs = 2000),
                ActionStep(action = "click_text", target = "Share", durationMs = 1500)
            )
            service.executeAiPlan(instaSteps)
        }
    }
}
