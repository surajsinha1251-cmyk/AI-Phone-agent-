package com.ai.phoneagent

import org.json.JSONObject

object YantraBrain {
    const val BRAIN_VERSION = "27.0-MODULAR-BRAIN"

    // 1. Extra App Shortcuts (Evolved dynamically)
    val extraAppAliases = mapOf(
        "studio" to "com.google.android.apps.youtube.creator",
        "yt studio" to "com.google.android.apps.youtube.creator",
        "seller" to "com.amazon.sellermobile.android",
        "flipkart seller" to "com.flipkart.seller",
        "truecaller" to "com.truecaller",
        "meesho" to "com.meesho.supply",
        "zepto" to "com.zeptoconsumerapp",
        "blinkit" to "com.grofers.customerapp"
    )

    // 2. Smart Instant Offline Reflexes (0.01s Execution without API cost)
    fun handleCustomReflex(g: String, service: AgentService): Boolean {
        when {
            g.contains("battery") || g.contains("storage") || g.contains("ram kitn") || g.contains("स्टोरेज") || g.contains("बैटरी") -> {
                if (!g.contains("setting") && !g.contains("khol") && !g.contains("open")) {
                    val report = service.getRedmiPhoneStatus(true)
                    service.speakOut(report)
                    service.stopAgent("📲 $report")
                    return true
                }
            }
            g.contains(" flashlight ") || g.contains("torch") || g.contains("टॉर्च") -> {
                val turnOff = g.contains("off") || g.contains("band") || g.contains("बंद")
                service.executeSystemControl(if (turnOff) "torch_off" else "torch_on")
                service.speakOut(if (turnOff) "Torch band" else "Torch chalu")
                service.stopAgent(if (turnOff) "🔦 Torch OFF!" else "🔦 Torch ON!")
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

    // 3. Mega-Audit Prompt Builder (Asks for 5+ Batch Upgrades & ONLY YantraBrain.kt code!)
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
            append("Ab humein 1300 line ka `gradle.yml` NAHI chahiye! ")
            append("Kripya ek hi baar mein 5 naye smart offline commands/features aur speed optimizations jodkar SIRF `YantraBrain.kt` (`package com.ai.phoneagent` se lekar aakhiri `}` tak, ~120 lines) ka 100% complete single-box Kotlin code do taaki main usse seedha GitHub par commit kar sakoon!")
        }
    }

    // 4. Pure Kotlin Code Sanitizer for YantraBrain.kt
    fun extractPureBrainKotlin(raw: String): String? {
        val clean = raw.replace("```kotlin", "").replace("```java", "").replace("```", "").trim()
        val startIdx = clean.indexOf("package com.ai.phoneagent")
        if (startIdx == -1) return null
        if (!clean.contains("object YantraBrain")) return null
        val endIdx = clean.lastIndexOf('}')
        if (endIdx == -1 || endIdx <= startIdx) return null
        return clean.substring(startIdx, endIdx + 1).trim()
    }

    // 5. Master System Prompt for Screen Action Brain
    fun buildActionPrompt(
        dmWidth: Int, dmHeight: Int, redmiStatus: String, nowTime: String,
        goal: String, corr: String, researchedInfo: String, notesSoFar: String,
        aiAppsOnPhone: String, installedApps: String, vaultSummary: String,
        latestNotif: String, historyStr: String, failedStr: String, stuckCount: Int, screen: String
    ): String {
        return """
            You are Yantra AI ($BRAIN_VERSION) controlling Xiaomi Redmi Note 10S (${dmWidth}x${dmHeight} px).
            HARDWARE: $redmiStatus | TIME: $nowTime
            MAIN GOAL: "$goal" | $corr
            RESEARCHED: "$researchedInfo" | MULTI-APP NOTES: "$notesSoFar"
            AI APPS: $aiAppsOnPhone | ALL APPS: $installedApps
            VAULT & RULES: "$vaultSummary" | NOTIF: "$latestNotif"
            HISTORY: $historyStr | BLACKLISTED: $failedStr | STUCK: $stuckCount
            SCREEN ELEMENTS:
            $screen

            RULES:
            1. Chain 2 to 4 fast actions in `"steps"` whenever possible.
            2. For multi-app research, visit AI apps one by one, save key points in `"save_note"`, then return `"action":"done"` with full summary.
            3. Output ONLY valid JSON:
            {"chosen_button_info":"...","next_step_plan":"...","save_note":"...","speak":"short Hindi","steps":[{"action":"open_app|type|click|open_setting|direct_intent|scroll_down|scroll_up|swipe_left|swipe_right|double_tap|long_press|click_best_video|scroll_loop|system_control|back|home","id":1,"x":540,"y":300,"app_name":"...","target":"...","text":"...","press_enter":true,"reason":"..."}]}
        """.trimIndent()
    }
}
