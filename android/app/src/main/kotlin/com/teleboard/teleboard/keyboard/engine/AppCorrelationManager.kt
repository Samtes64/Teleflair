package com.teleboard.teleboard.keyboard.engine

enum class AppContextCategory {
    MESSAGING,
    EMAIL_WORK,
    BROWSER_SEARCH,
    GENERAL
}

class AppCorrelationManager {

    private val messagingPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.plus",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.discord",
        "com.instagram.android",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.viber.voip"
    )

    private val emailPackages = setOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.Slack",
        "com.microsoft.teams",
        "com.samsung.android.email.provider"
    )

    private val browserPackages = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser"
    )

    // Slang and casual boosts for messaging
    private val casualTokens = setOf(
        "hey", "lol", "haha", "yeah", "ok", "cool", "omg", "btw", "thanks", "thx", "yep", "sure", "idk", "tbh"
    )

    // Formal tokens for email/work
    private val formalTokens = setOf(
        "regards", "sincerely", "please", "attached", "meeting", "schedule", "forward", "confirm", "available", "update"
    )

    fun getCategory(packageName: String?): AppContextCategory {
        if (packageName == null) return AppContextCategory.GENERAL
        val pkg = packageName.lowercase()
        return when {
            messagingPackages.any { pkg.contains(it) } -> AppContextCategory.MESSAGING
            emailPackages.any { pkg.contains(it) } -> AppContextCategory.EMAIL_WORK
            browserPackages.any { pkg.contains(it) } -> AppContextCategory.BROWSER_SEARCH
            else -> AppContextCategory.GENERAL
        }
    }

    fun applyWeightAdjustment(word: String, baseScore: Float, category: AppContextCategory): Float {
        val lower = word.lowercase()
        return when (category) {
            AppContextCategory.MESSAGING -> {
                if (casualTokens.contains(lower)) baseScore * 1.65f else baseScore
            }
            AppContextCategory.EMAIL_WORK -> {
                if (formalTokens.contains(lower)) baseScore * 1.75f else if (casualTokens.contains(lower)) baseScore * 0.4f else baseScore
            }
            AppContextCategory.BROWSER_SEARCH -> {
                baseScore
            }
            AppContextCategory.GENERAL -> {
                baseScore
            }
        }
    }
}
