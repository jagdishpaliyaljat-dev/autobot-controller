package com.personal.autobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.autobot.ai.ActionStep
import kotlinx.coroutines.*

class AutoBotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoBotAccessibilityService? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    // 1. Scan current screen elements and return readable text context for AI
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

    // 2. Dispatch Tap Gesture at (X, Y)
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

    // 3. Click element matching visible text or description
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

    // 4. Enter text into input field
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

    // 5. Swipe Gestures
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // 6. Execute AI Action Steps Sequence
    fun executeAiPlan(steps: List<ActionStep>, onFinished: (() -> Unit)? = null) {
        serviceScope.launch {
            for (step in steps) {
                when (step.action) {
                    "launch" -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(step.target)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                        delay(step.durationMs.coerceAtLeast(3500))
                    }
                    "click_text" -> {
                        if (!clickByText(step.target)) {
                            // If text not clickable directly, try tap near top right search icon if searching
                            if (step.target.contains("Search", ignoreCase = true)) {
                                val metrics = resources.displayMetrics
                                clickAt(metrics.widthPixels - 120f, 150f)
                            }
                        }
                        delay(step.durationMs.coerceAtLeast(1500))
                    }
                    "click_xy" -> {
                        val metrics = resources.displayMetrics
                        val realX = (step.x / 1080f) * metrics.widthPixels
                        val realY = (step.y / 2400f) * metrics.heightPixels
                        clickAt(realX, realY)
                        delay(step.durationMs.coerceAtLeast(1500))
                    }
                    "type_text" -> {
                        if (!inputText(step.target, step.text)) {
                            // Fallback to active focused node
                            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            val arguments = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                            }
                            focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }
                        delay(step.durationMs.coerceAtLeast(1500))
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
            onFinished?.invoke()
        }
    }
}
