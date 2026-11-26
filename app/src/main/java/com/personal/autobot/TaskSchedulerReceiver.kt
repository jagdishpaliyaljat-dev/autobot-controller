package com.personal.autobot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

class TaskSchedulerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = AutoBotAccessibilityService.instance ?: return
        val targetPackage = intent.getStringExtra("PACKAGE_NAME") ?: "com.google.android.youtube"

        // 1. Launch target app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }

        // 2. Perform automated action steps
        if (targetPackage.contains("youtube")) {
            executeYouTubeFlow(service)
        } else {
            executeInstagramFlow(service)
        }
    }

    private fun executeYouTubeFlow(service: AutoBotAccessibilityService) {
        service.runMacroSequence(listOf(
            {
                delay(4000)
                // Center bottom '+' button
                service.clickAt(540f, 2180f)
            },
            {
                delay(2000)
                // Click 'Upload a video' or text
                if (!service.clickByText("Upload a video")) {
                    service.clickAt(540f, 1800f)
                }
            },
            {
                delay(2500)
                // Select first video from gallery thumbnail
                service.clickAt(200f, 600f)
            },
            {
                delay(2000)
                service.clickByText("Next")
            },
            {
                delay(2000)
                service.inputText("Add details", "Auto Uploaded Video #shorts")
            },
            {
                delay(1500)
                service.clickByText("Upload Short")
            }
        ))
    }

    private fun executeInstagramFlow(service: AutoBotAccessibilityService) {
        service.runMacroSequence(listOf(
            {
                delay(4000)
                // Instagram '+' button on bottom or top bar
                if (!service.clickByText("New post")) {
                    service.clickAt(540f, 2180f)
                }
            },
            {
                delay(2500)
                // First media item in grid
                service.clickAt(200f, 750f)
            },
            {
                delay(2000)
                service.clickByText("Next")
            },
            {
                delay(2000)
                service.clickByText("Next")
            },
            {
                delay(2000)
                service.clickByText("Share")
            }
        ))
    }
}
