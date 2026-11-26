package com.personal.autobot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.personal.autobot.ai.ChatAdapter
import com.personal.autobot.ai.ChatMessage
import com.personal.autobot.ai.GeminiDirectAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private fun getDefaultBuiltinKey(): String {
        return try {
            val encoded = "QVEuQWI4Uk42SnVXZnpjQkpZYWtFMW8yb2Y0eGR0MWhCd21OTEVLZWhqdmNIM00tRzlQTHc="
            String(Base64.decode(encoded, Base64.DEFAULT)).trim()
        } catch (_: Exception) {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("autobot_prefs", Context.MODE_PRIVATE)

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etChatInput = findViewById<EditText>(R.id.etChatInput)
        val btnSendMessage = findViewById<ImageButton>(R.id.btnSendMessage)
        val btnToggleSettings = findViewById<ImageButton>(R.id.btnToggleSettings)
        val llSettingsPanel = findViewById<LinearLayout>(R.id.llSettingsPanel)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnPermissions = findViewById<AppCompatButton>(R.id.btnPermissions)
        val tvExecutionStatus = findViewById<TextView>(R.id.tvExecutionStatus)

        // Always ensure built-in verified key is loaded by default
        var savedKey = prefs.getString("gemini_api_key", "") ?: ""
        if (savedKey.isBlank()) {
            savedKey = getDefaultBuiltinKey()
            prefs.edit().putString("gemini_api_key", savedKey).apply()
        }
        etApiKey.setText(savedKey)

        // RecyclerView Setup
        chatAdapter = ChatAdapter(messages)
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.layoutManager = layoutManager
        rvChat.adapter = chatAdapter

        // Welcome Bot Message
        chatAdapter.addMessage(
            ChatMessage(
                "नमस्ते! मैं आपका AutoBot AI असिस्टेंट हूँ।\nआपकी Gemini Key पहले से एक्टिव और कनेक्टेड है! 🚀\nआप मुझे कुछ भी बोलें (जैसे: 'YouTube खोलकर गाना चलाओ', 'WhatsApp पर मैसेज भेजो', या 'सेटिंग्स चेक करो')—मैं स्क्रीन पर तुरंत वो काम कर दूंगा!",
                isUser = false
            )
        )

        // Toggle Settings
        btnToggleSettings.setOnClickListener {
            if (llSettingsPanel.visibility == View.VISIBLE) {
                llSettingsPanel.visibility = View.GONE
            } else {
                llSettingsPanel.visibility = View.VISIBLE
            }
        }

        // Grant Permissions
        btnPermissions.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val powerIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(powerIntent)
                } catch (_: Exception) {}
            }
            Toast.makeText(this, "Accessibility में जाकर 'AutoBot AI Agent' चालू करें", Toast.LENGTH_LONG).show()
        }

        // Send Message & Execute Auto Actions
        btnSendMessage.setOnClickListener {
            val userText = etChatInput.text.toString().trim()
            var apiKey = etApiKey.text.toString().trim()
            if (apiKey.isBlank()) {
                apiKey = getDefaultBuiltinKey()
            }

            if (userText.isEmpty()) return@setOnClickListener

            prefs.edit().putString("gemini_api_key", apiKey).apply()

            // 1. Add User Message to Chat
            chatAdapter.addMessage(ChatMessage(userText, isUser = true))
            etChatInput.text.clear()
            rvChat.scrollToPosition(messages.size - 1)

            val service = AutoBotAccessibilityService.instance
            if (service == null) {
                chatAdapter.addMessage(
                    ChatMessage(
                        "⚠️ कृपया पहले ऊपर सेटिंग्स में जाकर Accessibility Service ऑन करें ताकि मैं स्क्रीन पर एक्शन ले सकूँ!",
                        isUser = false
                    )
                )
                rvChat.scrollToPosition(messages.size - 1)
                return@setOnClickListener
            }

            tvExecutionStatus.text = "> 🧠 Gemini AI स्क्रीन और कमांड को एनालाइज कर रहा है..."

            activityScope.launch {
                val screenContext = service.extractScreenContext()
                val agent = GeminiDirectAgent(apiKey)
                val (botReply, steps) = agent.planActionSteps(userText, screenContext)

                withContext(Dispatchers.Main) {
                    // 2. Add AI reply to chat
                    chatAdapter.addMessage(ChatMessage(botReply, isUser = false))
                    rvChat.scrollToPosition(messages.size - 1)

                    if (steps.isNotEmpty()) {
                        tvExecutionStatus.text = "> ⚡ मोबाइल पर ${steps.size} ऑटोमैटिक एक्शन्स शुरू हो रहे हैं..."

                        // If the first action is to launch an app, minimize so that app gets focus immediately
                        if (steps.any { it.action == "launch" }) {
                            moveTaskToBack(true)
                        }

                        service.executeAiPlan(
                            steps,
                            onStepUpdate = { statusMsg ->
                                activityScope.launch(Dispatchers.Main) {
                                    tvExecutionStatus.text = "> $statusMsg"
                                }
                            },
                            onFinished = {
                                activityScope.launch(Dispatchers.Main) {
                                    tvExecutionStatus.text = "> ✅ टास्क सफलतापूर्वक पूरा हुआ!"
                                    chatAdapter.addMessage(ChatMessage("काम पूरा हो गया! 🎉", isUser = false))
                                    rvChat.scrollToPosition(messages.size - 1)
                                }
                            }
                        )
                    } else {
                        tvExecutionStatus.text = "> ℹ️ केवल बातचीत (कोई स्क्रीन एक्शन नहीं)"
                    }
                }
            }
        }
    }
}
