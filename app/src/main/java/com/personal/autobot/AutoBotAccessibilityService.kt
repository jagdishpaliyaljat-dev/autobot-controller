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
        val stroke = GestureDescription.StrokeDescription(path, 0, 150)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Gesture Completed successfully at ($x, $y)")
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture Cancelled at ($x, $y)")
                onComplete?.invoke()
            }
        }, null)
        Log.d(TAG, "dispatchGesture returned: $dispatched for ($x, $y)")
    }

    fun clickByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            // Find clickable ancestor or self
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked node for text '$targetText': $clicked")
                    return true
                }
                current = current.parent
            }
            // If none was clickable, click at its bounds center
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                return true
            }
        }
        return false
    }

    fun clickSearchElement(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        // Search by view id containing search or description
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = (node.text?.toString() ?: "").lowercase()
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName?.toString() ?: "").lowercase()

            if (text.contains("search") || desc.contains("search") || viewId.contains("search")) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    current = current.parent
                }
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
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
                            when {
                                pkg.contains("youtube", ignoreCase = true) -> {
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
                                }
                                pkg.equals("camera", ignoreCase = true) || pkg.contains("camera", ignoreCase = true) -> {
                                    val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(camIntent)
                                }
                                pkg.equals("dialer", ignoreCase = true) || pkg.contains("dialer", ignoreCase = true) -> {
                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(dialIntent)
                                }
                                pkg.equals("gallery", ignoreCase = true) || pkg.contains("gallery", ignoreCase = true) -> {
                                    val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                                        type = "image/*"
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(galleryIntent)
                                }
                                pkg.equals("calculator", ignoreCase = true) || pkg.contains("calc", ignoreCase = true) -> {
                                    val calcIntent = packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                                        ?: packageManager.getLaunchIntentForPackage("com.android.calculator2")
                                    calcIntent?.let {
                                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(it)
                                    }
                                }
                                else -> {
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
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error launching $pkg", e)
                        }
                        delay(step.durationMs.coerceAtLeast(3000))
                    }
                    "click_text" -> {
                        val clicked = if (step.target.contains("Search", ignoreCase = true)) {
                            clickSearchElement() || clickByText(step.target)
                        } else {
                            clickByText(step.target)
                        }

                        if (!clicked && step.target.contains("Search", ignoreCase = true)) {
                            val metrics = resources.displayMetrics
                            // YouTube & standard top search icon is top-right
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
                        val text = step.text
                        var typed = false
                        // 1. Try finding input field by target
                        val rootNode = rootInActiveWindow
                        if (rootNode != null && step.target.isNotBlank()) {
                            val nodes = rootNode.findAccessibilityNodeInfosByText(step.target)
                            for (node in nodes) {
                                val arguments = Bundle().apply {
                                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                                }
                                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                                    typed = true
                                    break
                                }
                            }
                        }
                        // 2. Try currently focused input node
                        if (!typed && rootNode != null) {
                            val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            val arguments = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                            }
                            if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                                typed = true
                            }
                        }
                        // 3. Fallback: Search any editable node in window
                        if (!typed && rootNode != null) {
                            val queue = ArrayDeque<AccessibilityNodeInfo>()
                            queue.add(rootNode)
                            while (queue.isNotEmpty()) {
                                val node = queue.removeFirst()
                                if (node.isEditable) {
                                    val arguments = Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                                    }
                                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                                        typed = true
                                        break
                                    }
                                }
                                for (i in 0 until node.childCount) {
                                    node.getChild(i)?.let { queue.add(it) }
                                }
                            }
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
