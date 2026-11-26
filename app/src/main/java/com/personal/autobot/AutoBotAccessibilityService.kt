package com.personal.autobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.autobot.ai.ActionStep
import kotlinx.coroutines.*

class AutoBotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoBotAccessibilityService? = null
        private const val TAG = "AutoBotService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service CONNECTED successfully!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    fun extractScreenContext(): String {
        val rootNode = rootInActiveWindow ?: return "Empty screen"
        val sb = StringBuilder()
        collectVisibleText(rootNode, sb)
        return if (sb.isEmpty()) "No readable text nodes" else sb.toString()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            sb.append("Text: \"").append(text).append("\" | ")
        }
        if (!desc.isNullOrEmpty() && desc != text) {
            sb.append("Desc: \"").append(desc).append("\" | ")
        }
        for (i in 0 until node.childCount) {
            collectVisibleText(node.getChild(i), sb)
        }
    }

    fun clickAt(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke()
            }
        }, null)
    }

    fun clickByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else if (node.parent?.isClickable == true) {
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    fun inputText(targetHintOrText: String, textToEnter: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetHintOrText)
        for (node in nodes) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                return true
            }
        }
        return false
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun executeAiPlan(steps: List<ActionStep>, onStepUpdate: ((String) -> Unit)? = null, onFinished: (() -> Unit)? = null) {
        serviceScope.launch {
            for ((index, step) in steps.withIndex()) {
                withContext(Dispatchers.Main) {
                    onStepUpdate?.invoke("Executing Step ${index + 1}/${steps.size}: ${step.action} ${step.target}")
                }

                when (step.action) {
                    "launch" -> {
                        val pkg = step.target
                        try {
                            if (pkg.contains("youtube", ignoreCase = true)) {
                                // 1. Try launching YouTube via native component first
                                val ytExplicit = packageManager.getLaunchIntentForPackage("com.google.android.youtube")?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                if (ytExplicit != null) {
                                    startActivity(ytExplicit)
                                } else {
                                    val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    startActivity(ytIntent)
                                }
                            } else {
                                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                if (launchIntent != null) {
                                    startActivity(launchIntent)
                                } else {
                                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("package:$pkg")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(viewIntent)
                                }
                            }
                        } catch (e: Exception) {
                            try {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(webIntent)
                            } catch (_: Exception) {}
                        }
                        delay(step.durationMs.coerceAtLeast(4000))
                    }
                    "click_text" -> {
                        val clicked = clickByText(step.target)
                        if (!clicked && step.target.contains("Search", ignoreCase = true)) {
                            val metrics = resources.displayMetrics
                            // YouTube top search icon is at top right
                            clickAt(metrics.widthPixels - 140f, 160f)
                        }
                        delay(step.durationMs.coerceAtLeast(2000))
                    }
                    "click_xy" -> {
                        val metrics = resources.displayMetrics
                        val realX = (step.x / 1080f) * metrics.widthPixels
                        val realY = (step.y / 2400f) * metrics.heightPixels
                        clickAt(realX, realY)
                        delay(step.durationMs.coerceAtLeast(2000))
                    }
                    "type_text" -> {
                        if (!inputText(step.target, step.text)) {
                            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            val arguments = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                            }
                            focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }
                        delay(step.durationMs.coerceAtLeast(2000))
                    }
                    "delay" -> {
                        delay(step.durationMs)
                    }
                    "swipe_down" -> {
                        val metrics = resources.displayMetrics
                        val midX = metrics.widthPixels / 2f
                        val midY = metrics.heightPixels / 2f
                        swipe(midX, midY + 400, midX, midY - 400, 300)
                        delay(1500)
                    }
                    "swipe_up" -> {
                        val metrics = resources.displayMetrics
                        val midX = metrics.widthPixels / 2f
                        val midY = metrics.heightPixels / 2f
                        swipe(midX, midY - 400, midX, midY + 400, 300)
                        delay(1500)
                    }
                    "back" -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(1000)
                    }
                    "home" -> {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        delay(1000)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onFinished?.invoke()
            }
        }
    }
}
