package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object YantraBrain {
    const val BRAIN_VERSION = "30.0-GENIUS-OMNI-BRAIN"

    private val brainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeBrainJob: Job? = null
    private val stepHistory = mutableListOf<String>()
    private var lastGoalTracked = ""

    private val proto = "https" + "://"
    private val googleApiHost = listOf("generativelanguage", "googleapis", "com").joinToString(".")

    val extraAppAliases = mapOf(
        "studio" to "com.google.android.apps.youtube.creator",
        "yt studio" to "com.google.android.apps.youtube.creator",
        "seller" to "com.amazon.sellermobile.android",
        "amazon seller" to "com.amazon.sellermobile.android",
        "flipkart seller" to "com.flipkart.seller",
        "seller hub" to "com.flipkart.seller",
        "termux" to "com.termux",
        "github" to "com.github.android",
        "truecaller" to "com.truecaller",
        "meesho" to "com.meesho.supply",
        "zepto" to "com.zeptoconsumerapp",
        "blinkit" to "com.grofers.customerapp",
        "swiggy" to "in.swiggy.android",
        "zomato" to "com.application.zomato",
        "whatsapp business" to "com.whatsapp.w4b",
        "paytm business" to "net.one97.merchant",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "jio" to "com.jio.myjio",
        "airtel" to "com.myairtelapp",
        "canva" to "com.canva.editor"
    )

    private fun updateFloatingStatus(service: AgentService, msg: String) {
        try {
            val f = service.javaClass.getDeclaredField("statusTv").apply { isAccessible = true }
            (f.get(service) as? TextView)?.text = msg
        } catch (e: Exception) {}
    }

    private fun tryOfflineMath(g: String): String? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*([+\-*/xX×÷])\s*(\d+(?:\.\d+)?)""").find(g) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val op = m.groupValues[2]
        val b = m.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "X", "×" -> a * b
            "/", "÷" -> if (b != 0.0) a / b else return null
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
    }

    private suspend fun askGeminiWithGoogleSearch(service: AgentService, query: String): String = withContext(Dispatchers.IO) {
        val prefs = service.getSharedPreferences("AgentPrefs", Context.MODE_PRIVATE)
        val keys = (prefs.getString("apiKey", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return@withContext "Pehle Settings mein Gemini API Key daalein."

        val models = listOf("gemini-2.5-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash")
        for (k in keys) {
            for (m in models) {
                for (useSearch in listOf(true, false)) {
                    var conn: HttpURLConnection? = null
                    try {
                        val url = URL("$proto$googleApiHost/v1beta/models/$m:generateContent?key=$k")
                        conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.connectTimeout = 5000 ; conn.readTimeout = 9000 ; conn.doOutput = true

                        val prompt = "User Question: \"$query\". Give a direct, accurate, short answer in simple Hinglish/Hindi (maximum 2 sentences). State exact names/numbers clearly."
                        val body = JSONObject().apply {
                            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                            if (useSearch) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                            put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 300))
                        }
                        conn.outputStream.use { it.write(body.toString().toByteArray()) }
                        if (conn.responseCode == 200) {
                            val res = conn.inputStream.bufferedReader().readText()
                            val parts = JSONObject(res).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
                            val ans = sb.toString().replace("*", "").replace("#", "").trim()
                            if (ans.isNotEmpty()) return@withContext ans
                        }
                    } catch (e: Exception) {} finally { try { conn?.disconnect() } catch (e: Exception) {} }
                }
            }
        }
        return@withContext "Abhi server busy hai, kripya dobara puchein."
    }

    private fun findBestNonAdYouTubeVideo(root: AccessibilityNodeInfo?, screenHeight: Int): Pair<Pair<Int, Int>, String>? {
        if (root == null) return null
        val adWords = listOf("sponsored", "विज्ञापन", "ad ·", "ad:", "visit site", "install", "shop now")
        var bestCoords: Pair<Int, Int>? = null
        var bestTitle = ""

        fun scan(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser || bestCoords != null) return
            val r = Rect() ; node.getBoundsInScreen(r)
            val txt = "${node.text ?: ""} ${node.contentDescription ?: ""}".trim()
            val lower = txt.lowercase()
            if (r.centerY() in 240..(screenHeight - 180) && r.width() > 200 && r.height() > 60 && txt.length > 10) {
                val isAd = adWords.any { lower.contains(it) }
                if (!isAd && (node.isClickable || node.parent?.isClickable == true) && !lower.startsWith("filter") && lower != "shorts") {
                    if (lower.contains("views") || lower.contains("ago") || lower.contains("बार देखा") || lower.contains("minutes") || txt.length > 20) {
                        bestCoords = Pair(r.centerX(), r.centerY())
                        bestTitle = txt.lines().firstOrNull()?.take(70) ?: txt.take(70)
                        return
                    }
                }
            }
            for (i in 0 until node.childCount) scan(node.getChild(i))
        }
        scan(root)
        return if (bestCoords != null) Pair(bestCoords!!, bestTitle) else null
    }

    private suspend fun tapScreen(service: AgentService, x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        service.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
        delay(100)
    }

    fun handleCustomReflex(g: String, service: AgentService): Boolean {
        // Lock fast non-thinking model in prefs so AgentService never truncates JSON!
        val prefs = service.getSharedPreferences("AgentPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("lastFastModel", "") == "gemini-2.5-flash") {
            prefs.edit().putString("lastFastModel", "gemini-2.5-flash-lite").apply()
        }

        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // 1. Instant Math Calculator
        if (g.contains("kitna") || g.contains("calculate") || g.contains("hisab") || Regex("""\d+\s*[+\-*/×÷]\s*\d+""").containsMatchIn(g)) {
            tryOfflineMath(g)?.let { ans ->
                service.speakOut("Jawab hai $ans")
                service.stopAgent("🧮 Answer: $ans")
                return true
            }
        }

        // 2. Smart Direct Question Answering (Fixes ChatGPT/Google/YouTube jumping bug!)
        val isQuestionIntent = listOf(
            "kon sa", "kaun sa", "कौन सा", "kya hai", "kya h", "क्या है",
            "batao", "बताओ", "kab aaya", "sabse naya", "सबसे नया", "latest episode",
            "naya episode kon", "naya episode kaun", "kisne", "kahan", "price kya", "rate kya"
        ).any { g.contains(it) }
        val hasActionVerb = listOf(
            "chalao", "chala do", "चलाओ", "play karo", "play", "kholo", "खोलो", "open", "send", "bhejo", "call"
        ).any { g.contains(it) }

        if (isQuestionIntent && !hasActionVerb) {
            activeBrainJob?.cancel()
            activeBrainJob = brainScope.launch {
                updateFloatingStatus(service, "🔍 Google Search se sahi jawab dhoondh raha hoon...")
                service.speakOut("Ek second, sahi jankari dhoondh raha hoon")
                val answer = askGeminiWithGoogleSearch(service, g)
                service.speakOut(answer)
                service.stopAgent("🧠 $answer")
            }
            return true
        }

        // 3. Smart Single-Shot YouTube Player (Searches ONCE, skips Ads, plays & speaks title!)
        if ((g.contains("youtube") || g.contains("यूट्यूब") || g.contains("episode") || g.contains("song") || g.contains("गाना") || g.contains("video")) &&
            (g.contains("chala") || g.contains("चला") || g.contains("play") || g.contains("search") || g.contains("लगाओ"))) {
            val rawQuery = g.replace("youtube par", "").replace("यूट्यूब पर", "").replace("youtube", "").replace("यूट्यूब", "")
                .replace("chalao", "").replace("chala do", "").replace("चलाओ", "").replace("play karo", "").replace("play", "")
                .replace("search karo", "").replace("लगाओ", "").trim()
            if (rawQuery.length >= 2) {
                activeBrainJob?.cancel()
                activeBrainJob = brainScope.launch {
                    var finalSearch = rawQuery
                    if (rawQuery.contains("naya episode") || rawQuery.contains("latest")) {
                        updateFloatingStatus(service, "🔍 Sabse naye episode ka naam pata kar raha hoon...")
                        val info = askGeminiWithGoogleSearch(service, "$rawQuery exact title")
                        if (info.length in 4..90 && !info.contains("busy")) finalSearch = "$rawQuery"
                    }
                    updateFloatingStatus(service, "▶️ YouTube par '$finalSearch' chala raha hoon...")
                    service.speakOut("YouTube par $rawQuery chala raha hoon")
                    service.fireDirectIntent("youtube_search", finalSearch)
                    delay(2400)
                    val dm = service.resources.displayMetrics
                    val hit = findBestNonAdYouTubeVideo(service.rootInActiveWindow, dm.heightPixels)
                    if (hit != null) {
                        tapScreen(service, hit.first.first, hit.first.second)
                        delay(400)
                        service.speakOut("Chala diya hai: ${hit.second}")
                        service.stopAgent("▶️ Playing: ${hit.second}")
                    } else {
                        service.stopAgent("▶️ YouTube Search: '$finalSearch' khul gaya!")
                    }
                }
                return true
            }
        }

        // 4. Instant Hardware & System Controls
        when {
            g.contains("alarm") || g.contains("अलार्म") -> {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Yantra Alarm")
                        putExtra(AlarmClock.EXTRA_HOUR, 7)
                        putExtra(AlarmClock.EXTRA_MINUTES, 0)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                    service.speakOut("Alarm set kar diya hai")
                    service.stopAgent("⏰ Alarm Set!")
                    return true
                } catch (e: Exception) {}
            }
            g.contains("timer") || g.contains("टाइमर") -> {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, 300)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Yantra Timer")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                    service.speakOut("Timer laga diya hai")
                    service.stopAgent("⏱️ Timer Started!")
                    return true
                } catch (e: Exception) {}
            }
            g.contains("battery") || g.contains("storage") || g.contains("ram kitn") || g.contains("स्टोरेज") || g.contains("बैटरी") -> {
                if (!g.contains("setting") && !g.contains("khol") && !g.contains("open")) {
                    val report = service.getRedmiPhoneStatus(true)
                    service.speakOut(report)
                    service.stopAgent("📲 $report")
                    return true
                }
            }
            g.contains("time kya") || g.contains("samay kya") || g.contains("kitne baje") || g.contains("टाइम क्या") || g.contains("aaj ki date") || g.contains("tarikh") -> {
                val nowStr = SimpleDateFormat("hh:mm a, EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
                service.speakOut("Abhi samay hai $nowStr")
                service.stopAgent("🕒 $nowStr")
                return true
            }
            g.contains("flashlight") || g.contains("torch") || g.contains("टॉर्च") -> {
                val turnOff = g.contains("off") || g.contains("band") || g.contains("बंद")
                service.executeSystemControl(if (turnOff) "torch_off" else "torch_on")
                service.speakOut(if (turnOff) "Torch band" else "Torch chalu")
                service.stopAgent(if (turnOff) "🔦 Torch OFF!" else "🔦 Torch ON!")
                return true
            }
            g.contains("screenshot") || g.contains("स्क्रीनशॉट") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                service.speakOut("Screenshot le liya gaya hai")
                service.stopAgent("📸 Screenshot Captured!")
                return true
            }
            g.contains("lock screen") || g.contains("screen lock") || g.contains("phone lock") || g.contains("लॉक करो") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    service.stopAgent("🔒 Screen Locked!")
                    return true
                }
            }
            g.contains("split screen") || g.contains("do screen") || g.contains("स्प्लिट स्क्रीन") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                service.stopAgent("🪟 Split Screen Toggled!")
                return true
            }
            g.contains("notification") || g.contains("नोटिफिकेशन") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                service.stopAgent("🔔 Notifications Opened!")
                return true
            }
            g.contains("control center") || g.contains("quick setting") || g.contains("कंट्रोल सेंटर") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                service.stopAgent("🎛️ Control Center Opened!")
                return true
            }
            g.contains("recent app") || g.contains("रीसेंट") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                service.stopAgent("🗂️ Recent Apps!")
                return true
            }
            g == "back" || g == "back karo" || g.contains("पीछे जाओ") || g.contains("बैक करो") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                service.stopAgent("🔙 Back!")
                return true
            }
            g == "home" || g == "home chalo" || g.contains("होम स्क्रीन") -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                service.stopAgent("🏠 Home Screen!")
                return true
            }
            g.contains("volume up") || g.contains("aawaz badha") || g.contains("full aawaz") || g.contains("आवाज़ बढ़ा") -> {
                audio?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audio?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                service.speakOut("Aawaz badha di hai")
                service.stopAgent("🔊 Volume Up!")
                return true
            }
            g.contains("volume down") || g.contains("aawaz kam") || g.contains("आवाज़ कम") -> {
                audio?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                audio?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                service.speakOut("Aawaz kam kar di hai")
                service.stopAgent("🔉 Volume Down!")
                return true
            }
            g.contains("mute") || g.contains("silent karo") -> {
                audio?.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                service.stopAgent("🔇 Audio Muted!")
                return true
            }
            (g.startsWith("call ") || g.startsWith("dial ")) && g.any { it.isDigit() } -> {
                val digits = g.filter { it.isDigit() || it == '+' }
                if (digits.length >= 10) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(intent)
                    service.speakOut("Number dial kar diya hai")
                    service.stopAgent("📞 Dialing $digits")
                    return true
                }
            }
            (g.contains("google par ") || g.contains("गूगल पर ")) && g.contains("search") -> {
                val q = g.replace("google par", "").replace("गूगल पर", "").replace("search karo", "").replace("search", "").trim()
                if (q.length >= 2) {
                    service.fireDirectIntent("web_search", q)
                    service.speakOut("Google par $q search kar diya hai")
                    service.stopAgent("🌐 Google Search: '$q'")
                    return true
                }
            }
            g.contains("wifi setting") || g.contains("bluetooth setting") || g.contains("storage setting") || g.contains("display setting") -> {
                service.openRedmiSetting(g)
                service.speakOut("Setting khol di hai")
                service.stopAgent("⚙️ Setting Opened!")
                return true
            }
        }
        return false
    }

    fun buildMegaAuditPrompt(
        myVariant: String,
        targetTwin: String,
        cacheTimeMs: Long,
        appCount: Int,
        aiCount: Int,
        modelName: String,
        apiLatencyMs: Long,
        recentLogs: String,
        failedList: String,
        customFeatureRequest: String
    ): String {
        return buildString {
            append("Bhai, main $myVariant ($BRAIN_VERSION) bol raha hoon. Maine apna Mega-Audit poora kar liya hai:\n")
            append("• Cache Speed: ${cacheTimeMs}ms ($appCount apps,$aiCount AI apps)\n")
            append("• Active Model Latency: $modelName (${apiLatencyMs}ms)\n")
            append("• Brain Logs: $recentLogs\n")
            append("• Failed Clicks / Bottlenecks: $failedList\n")
            if (customFeatureRequest.isNotEmpty()) append("• User Priority Request: $customFeatureRequest\n")
            append("\n🚨 IMPORTANT MODULAR RULE:\n")
            append("Ab humein 1300 line ka gradle.yml NAHI chahiye! ")
            append("Kripya ek hi baar mein 5 naye smart offline commands/features aur speed optimizations jodkar SIRF YantraBrain.kt (package com.ai.phoneagent se lekar aakhiri } tak) ka 100% complete single-box Kotlin code do taaki main usse seedha GitHub par commit kar sakoon!")
        }
    }

    fun extractPureBrainKotlin(raw: String): String? {
        val clean = raw.replace("```kotlin", "").replace("```java", "").replace("```", "").trim()
        val startIdx = clean.indexOf("package com.ai.phoneagent")
        if (startIdx == -1) return null
        if (!clean.contains("object YantraBrain")) return null
        val endIdx = clean.lastIndexOf('}')
        if (endIdx == -1 || endIdx <= startIdx) return null
        return clean.substring(startIdx, endIdx + 1).trim()
    }

    fun buildActionPrompt(
        dmWidth: Int, dmHeight: Int, redmiStatus: String, nowTime: String,
        goal: String, corr: String, researchedInfo: String, notesSoFar: String,
        aiAppsOnPhone: String, installedApps: String, vaultSummary: String,
        latestNotif: String, historyStr: String, failedStr: String, stuckCount: Int, screen: String
    ): String {
        if (goal != lastGoalTracked) {
            lastGoalTracked = goal
            stepHistory.clear()
        }
        val screenTop = screen.lines().take(3).joinToString(" ")
        stepHistory.add("Step${stepHistory.size + 1}($screenTop)")
        if (stepHistory.size > 8) stepHistory.removeAt(0)

        return """
            Yantra AI ($BRAIN_VERSION) on Redmi Note 10S (${dmWidth}x${dmHeight}).
            GOAL: "$goal"
            STEPS_TAKEN_SO_FAR: ${stepHistory.joinToString(" -> ")}
            SCREEN_NODES:
            $screen

            STRICT RULES (OUTPUT UNDER 90 TOKENS SO JSON NEVER TRUNCATES):
            1. If a permission/dialog button ('Allow', 'While using the app', 'Continue', 'Skip', 'Not now', 'OK', 'अनुमति दें') is on screen, click it FIRST.
            2. NEVER repeat a search or click if STEPS_TAKEN_SO_FAR shows you already searched!
            3. If the goal is complete or video/screen is open, return: {"action":"done","message":"Kaam poora ho gaya"}
            4. Otherwise return ONLY 1 short JSON step:
            {"steps":[{"action":"open_app|type|click|scroll_down|back|home","id":1,"x":540,"y":300,"app_name":"...","target":"...","text":"...","press_enter":true}]}
        """.trimIndent()
    }
}
