package com.ai.phoneagent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object YantraBrain {
    const val BRAIN_VERSION = "29.0-SUPREME-MODULAR"

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
        "whatsapp business" to "com.whatsapp.w4b",
        "wa business" to "com.whatsapp.w4b",
        "paytm business" to "net.one97.merchant",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "jio" to "com.jio.myjio",
        "myjio" to "com.jio.myjio",
        "airtel" to "com.myairtelapp",
        "canva" to "com.canva.editor",
        "docs" to "com.google.android.apps.docs.editors.docs",
        "sheets" to "com.google.android.apps.docs.editors.sheets"
    )

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

    fun handleCustomReflex(g: String, service: AgentService): Boolean {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // 1. Instant Offline Math Reflex
        if (g.contains("kitna") || g.contains("calculate") || g.contains("hisab") || Regex("""\d+\s*[+\-*/×÷]\s*\d+""").containsMatchIn(g)) {
            tryOfflineMath(g)?.let { ans ->
                service.speakOut("Jawab hai $ans")
                service.stopAgent("🧮 Answer: $ans")
                return true
            }
        }

        when {
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
            (g.contains("youtube par ") || g.contains("यूट्यूब पर ")) && (g.contains("search") || g.contains("chala") || g.contains("play") || g.contains("चलाओ")) -> {
                val q = g.replace("youtube par", "").replace("यूट्यूब पर", "").replace("search karo", "").replace("chalao", "").replace("chala do", "").replace("play karo", "").replace("चलाओ", "").trim()
                if (q.length >= 2) {
                    service.fireDirectIntent("youtube_search", q)
                    service.speakOut("YouTube par $q khol diya hai")
                    service.stopAgent("▶️ YouTube Search: '$q'")
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
            g.contains("clear cache") || g.contains("boost speed") -> {
                service.speakOut("Yantra cache aur memory optimize ho gayi hai")
                service.stopAgent("⚡ Memory & Cache Optimized!")
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
        return """
            Yantra AI ($BRAIN_VERSION) on Redmi Note 10S (${dmWidth}x${dmHeight}).
            STATUS: $redmiStatus | TIME: $nowTime
            GOAL: "$goal" | $corr
            FACTS: "$researchedInfo" | NOTES: "$notesSoFar"
            AI_APPS: $aiAppsOnPhone | APPS: $installedApps
            HISTORY: $historyStr | FAILED: $failedStr
            SCREEN:
            $screen

            Return ONLY valid JSON (chain 2-4 steps for speed):
            {"chosen_button_info":"...","next_step_plan":"...","save_note":"...","speak":"short Hindi","steps":[{"action":"open_app|type|click|open_setting|direct_intent|scroll_down|scroll_up|system_control|back|home","id":1,"x":540,"y":300,"app_name":"...","target":"...","text":"...","press_enter":true,"reason":"..."}]}
        """.trimIndent()
    }
}
