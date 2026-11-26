package com.personal.autobot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.personal.autobot.ai.OpenRouterAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("autobot_prefs", Context.MODE_PRIVATE)

        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnRunAiTask = findViewById<Button>(R.id.btnRunAiTask)
        val btnScheduleTask = findViewById<Button>(R.id.btnScheduleTask)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etUserPrompt = findViewById<EditText>(R.id.etUserPrompt)
        val rbYouTube = findViewById<RadioButton>(R.id.rbYouTube)
        val etDelayMinutes = findViewById<EditText>(R.id.etDelayMinutes)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        etApiKey.setText(prefs.getString("openrouter_key", ""))

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
            Toast.makeText(this, "Turn ON 'AutoBot AI Agent' in Accessibility", Toast.LENGTH_LONG).show()
        }

        btnRunAiTask.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val prompt = etUserPrompt.text.toString().trim()

            if (prompt.isEmpty()) {
                Toast.makeText(this, "Please enter an AI prompt/command", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("openrouter_key", apiKey).apply()

            val service = AutoBotAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "Please enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            tvStatus.text = "Status: AI is analyzing prompt and screen..."
            activityScope.launch {
                val screenContext = service.extractScreenContext()
                val agent = OpenRouterAgent(apiKey)
                val steps = agent.planActionSteps(prompt, screenContext)

                if (steps.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Status: AI could not generate steps (check API key or network)."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Status: Executing ${steps.size} AI steps..."
                    }
                    service.executeAiPlan(steps) {
                        activityScope.launch(Dispatchers.Main) {
                            tvStatus.text = "Status: AI Task finished successfully!"
                        }
                    }
                }
            }
        }

        btnScheduleTask.setOnClickListener {
            val minutesStr = etDelayMinutes.text.toString().trim()
            val minutes = minutesStr.toIntOrNull() ?: 1
            val targetPkg = if (rbYouTube.isChecked) {
                "com.google.android.youtube"
            } else {
                "com.instagram.android"
            }

            scheduleTask(targetPkg, minutes)
            tvStatus.text = "Status: Preset scheduled for $targetPkg in $minutes min(s)."
        }
    }

    private fun scheduleTask(targetPackage: String, delayMinutes: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskSchedulerReceiver::class.java).apply {
            putExtra("PACKAGE_NAME", targetPackage)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)

        Toast.makeText(this, "Scheduled for $delayMinutes minute(s) from now!", Toast.LENGTH_SHORT).show()
    }
}
