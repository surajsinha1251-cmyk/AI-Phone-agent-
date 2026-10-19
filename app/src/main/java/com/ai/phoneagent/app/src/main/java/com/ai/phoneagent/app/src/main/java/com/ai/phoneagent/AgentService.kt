package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AgentService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingLayout: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var cmdInput: EditText
    private lateinit var actionBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var params: WindowManager.LayoutParams

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false
    private var currentGoal = ""
    private val history = mutableListOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        createFloatingBar()
    }

    private fun createFloatingBar() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        floatingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE0F172A"))
            setPadding(24, 24, 24, 24)
        }

        statusTv = TextView(this).apply {
            text = "🤖 AI Ready: Kya kaam karna hai?"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 13f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        cmdInput = EditText(this).apply {
            hint = "Bolo ya likho (jaise: YouTube par song chalao)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                // Jab user type karna chahe toh keyboard kholne do
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                windowManager.updateViewLayout(floatingLayout, params)
            }
        }

        actionBtn = Button(this).apply {
            text = "▶ Run"
            setOnClickListener {
                val q = cmdInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    currentGoal = q
                    startAgentLoop()
                }
            }
        }

        stopBtn = Button(this).apply {
            text = "⏹"
            setOnClickListener {
                stopAgent("⏹ Aapne AI ko rok diya.")
            }
        }

        row.addView(cmdInput)
        row.addView(actionBtn)
        row.addView(stopBtn)

        floatingLayout.addView(statusTv)
        floatingLayout.addView(row)

        windowManager.addView(floatingLayout, params)
    }

    private fun setTouchPassThrough(passThrough: Boolean) {
        if (passThrough) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        windowManager.updateViewLayout(floatingLayout, params)
    }

    private fun startAgentLoop() {
        if (isRunning) return
        isRunning = true
        actionBtn.text = "⏳..."
        setTouchPassThrough(true) // Niche wale app par click hone do

        scope.launch {
            while (isRunning) {
                statusTv.setTextColor(Color.parseColor("#60A5FA"))
                statusTv.text = "🔍 Screen dekh aur soch raha hoon..."

                val screenData = readScreenElements()
                val decision = askGeminiBrain(currentGoal, screenData)

                if (!isRunning) break

                val action = decision.optString("action", "wait")
                val reason = decision.optString("reason", "")
                val msg = decision.optString("message", "")

                history.add("Action: $action ($reason)")
                if (history.size > 8) history.removeAt(0)

                when (action) {
                    "click" -> {
                        val x = decision.optInt("x", 500)
                        val y = decision.optInt("y", 500)
                        statusTv.text = "👆 Click: $reason"
                        performTap(x.toFloat(), y.toFloat())
                    }
                    "type" -> {
                        val x = decision.optInt("x", -1)
                        val y = decision.optInt("y", -1)
                        val text = decision.optString("text", "")
                        statusTv.text = "⌨️ Typing: $text"
                        if (x > 0 && y > 0) {
                            performTap(x.toFloat(), y.toFloat())
                            delay(800)
                        }
                        typeTextInFocusedOrAvailable(text)
                    }
                    "open_app" -> {
                        val appName = decision.optString("app_name", "")
                        statusTv.text = "🚀 App khol raha hoon: $appName"
                        openAppByName(appName)
                    }
                    "scroll_down" -> {
                        statusTv.text = "📜 Niche scroll kar raha hoon..."
                        performScrollDown()
                    }
                    "back" -> {
                        statusTv.text = "🔙 Back ja raha hoon..."
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    "home" -> {
                        statusTv.text = "🏠 Home screen..."
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    "ask_user" -> {
                        // JAB PASSWORD, OTP, CAPTCHA YA CONFUSION HO!
                        vibratePhone()
                        pauseForUser("⚠️ AI Ruka Hai: $msg\n(Solve karke ▶ Resume dabayein)")
                        break
                    }
                    "done" -> {
                        vibratePhone()
                        stopAgent("✅ Kaam Poora: $msg")
                        break
                    }
                }
                delay(3000) // Agla step lene se pehle screen load hone do
            }
        }
    }

    private fun pauseForUser(alertText: String) {
        isRunning = false
        statusTv.setTextColor(Color.parseColor("#FBBF24")) // Yellow Alert
        statusTv.text = alertText
        actionBtn.text = "▶ Resume"
    }

    private fun stopAgent(finalText: String) {
        isRunning = false
        statusTv.setTextColor(Color.parseColor("#4ADE80"))
        statusTv.text = finalText
        actionBtn.text = "▶ Run"
        history.clear()
        setTouchPassThrough(false)
    }

    // 1. SCREEN KI AANKHEIN: Har button, text aur password field ko padhna
    private fun readScreenElements(): String {
        val root = rootInActiveWindow ?: return "Screen khali hai"
        val list = mutableListOf<String>()
        val pkg = root.packageName?.toString() ?: "unknown"
        list.add("Current App Package: $pkg")

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Apne khud ke floating bar ko ignore karo
            if (rect.top < 220 && pkg == packageName) return

            val text = (node.text ?: node.contentDescription ?: "").toString().trim()
            val isClick = node.isClickable
            val isEdit = node.isEditable
            val isPass = node.isPassword

            if (text.isNotEmpty() || isEdit || isPass || isClick) {
                val cx = rect.centerX()
                val cy = rect.centerY()
                if (cx > 0 && cy > 0 && rect.width() > 10 && rect.height() > 10) {
                    val tag = when {
                        isPass -> "[PASSWORD_BOX]"
                        isEdit -> "[INPUT_BOX]"
                        isClick -> "[BUTTON]"
                        else -> "[TEXT]"
                    }
                    val cleanText = text.replace("\n", " ").take(45)
                    list.add("$tag \"$cleanText\" at (x=$cx, y=$cy)")
                }
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return list.take(80).joinToString("\n")
    }

    // 2. GEMINI DIMAAG: Agla kadam sochna
    private suspend fun askGeminiBrain(goal: String, screen: String): JSONObject = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("AgentPrefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("apiKey", "") ?: ""
        if (apiKey.isEmpty()) {
            return@withContext JSONObject().put("action", "ask_user").put("message", "Pehle App mein Gemini API Key save karein!")
        }

        val prompt = """
            You are an Autonomous Android Phone Agent.
            USER GOAL: "$goal"
            RECENT HISTORY: ${history.joinToString(" -> ")}
            CURRENT SCREEN ELEMENTS:
            $screen

            RULES:
            1. Decide the single next step to achieve the USER GOAL.
            2. CRITICAL: If the screen requires a Password, PIN, OTP, Bank login, Payment confirmation, CAPTCHA, or if you are stuck/unsure, you MUST return action "ask_user" with a clear Hindi message telling the user what to do manually.
            3. If you need to open an app not on screen, use action "open_app" with "app_name" (e.g. "YouTube", "WhatsApp", "Chrome", "Settings").
            4. Respond ONLY with a valid JSON object in one of these exact formats:
            {"action": "click", "x": 540, "y": 900, "reason": "Clicking search"}
            {"action": "type", "x": 540, "y": 300, "text": "hello", "reason": "Typing query"}
            {"action": "open_app", "app_name": "YouTube", "reason": "Opening YouTube"}
            {"action": "scroll_down", "reason": "Looking for more items"}
            {"action": "back", "reason": "Going back"}
            {"action": "home", "reason": "Going to home screen"}
            {"action": "ask_user", "message": "Yahan Password/OTP daal kar Resume dabayein", "reason": "Password needed"}
            {"action": "done", "message": "Kaam ho gaya!"}
        """.trimIndent()

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val resText = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(resText)
            val reply = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            return@withContext JSONObject(reply)
        } catch (e: Exception) {
            return@withContext JSONObject().put("action", "ask_user").put("message", "API Error: ${e.message}")
        }
    }

    // 3. HAATH: Tap, Scroll, Type aur App Open karna
    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 120)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun performScrollDown() {
        val path = Path().apply {
            moveTo(500f, 1500f)
            lineTo(500f, 400f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun typeTextInFocusedOrAvailable(text: String) {
        val root = rootInActiveWindow ?: return
        fun findAndType(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isEditable) {
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                return true
            }
            for (i in 0 until node.childCount) {
                if (findAndType(node.getChild(i))) return true
            }
            return false
        }
        findAndType(root)
    }

    private fun openAppByName(appName: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    private fun vibratePhone() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
