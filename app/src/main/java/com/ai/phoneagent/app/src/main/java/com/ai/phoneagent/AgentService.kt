package com.ai.phoneagent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AgentPrefs", Context.MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val title = TextView(this).apply {
            text = "🤖 AI Phone Commander"
            textSize = 24f
            setTextColor(Color.parseColor("#818CF8"))
            setPadding(0, 0, 0, 40)
        }

        val apiInput = EditText(this).apply {
            hint = "Yahan Gemini API Key daalein (AIza...)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setText(prefs.getString("apiKey", ""))
        }

        val saveBtn = Button(this).apply {
            text = "💾 Save Gemini API Key"
            setOnClickListener {
                prefs.edit().putString("apiKey", apiInput.text.toString().trim()).apply()
                Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
            }
        }

        val overlayBtn = Button(this).apply {
            text = "1️⃣ Enable Floating Window Permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(context, "Floating Permission Pehle se ON hai!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val accBtn = Button(this).apply {
            text = "2️⃣ Enable Accessibility Service (Main Haath)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val infoText = TextView(this).apply {
            text = "\nKaise Use Karein:\n1. Gemini API Key save karein.\n2. Dono permissions ON karein.\n3. Screen ke upar Floating AI Bar aa jayega.\n4. Usmein bolkar ya likhkar koi bhi kaam batayein!"
            setTextColor(Color.LTGRAY)
            textSize = 14f
        }

        layout.addView(title)
        layout.addView(apiInput)
        layout.addView(saveBtn)
        layout.addView(overlayBtn)
        layout.addView(accBtn)
        layout.addView(infoText)

        setContentView(layout)
    }
}
