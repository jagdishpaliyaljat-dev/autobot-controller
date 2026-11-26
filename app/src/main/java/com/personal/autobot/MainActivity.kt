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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnScheduleTask = findViewById<Button>(R.id.btnScheduleTask)
        val rbYouTube = findViewById<RadioButton>(R.id.rbYouTube)
        val etDelayMinutes = findViewById<EditText>(R.id.etDelayMinutes)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnPermissions.setOnClickListener {
            // Open Accessibility Settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)

            // Request ignore battery optimization
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val powerIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(powerIntent)
                } catch (_: Exception) {}
            }
            Toast.makeText(this, "Turn ON 'AutoBot Controller' in Accessibility Settings", Toast.LENGTH_LONG).show()
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
            tvStatus.text = "Status: Task scheduled for $targetPkg in $minutes min(s)."
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
