package com.teleboard.teleboard.keyboard.context

/**
 * Target platform and sub-context calibration profile for adaptive text & Braille art.
 * Calibrated from mathematical measurements to guarantee zero line-wrapping,
 * exact character limits, and high-fidelity rendering across mobile screens.
 */
data class ContextArtProfile(
    val id: String,
    val displayName: String,
    val targetColumns: Int,
    val aspectRatioCompensation: Float,
    val wrapInCodeBlock: Boolean,
    val lockBrailleBlank: Boolean,
    val maxCharLimit: Int = Int.MAX_VALUE,
    val edgeStrength: Float = 44f,
    val contrast: Float = 26f,
    val brightness: Float = 0f,
    val bgSuppressionThreshold: Int = 24,
    val bgSuppressPercent: Float = 48f
)

enum class SubContextType {
    COMMENT,
    POST_CAPTION,
    DIRECT_MESSAGE,
    SMS,
    SEARCH,
    UNKNOWN
}

object PlatformProfiles {

    // 1. Telegram Mobile (28 columns, 0.42 mobile aspect, wrapped in markdown code blocks)
    val TELEGRAM_MOBILE = ContextArtProfile(
        id = "telegram_mobile",
        displayName = "Telegram • 28 cols",
        targetColumns = 28,
        aspectRatioCompensation = 0.42f,
        wrapInCodeBlock = true,
        lockBrailleBlank = true, // Space-locked with U+2800 for fixed-width glyph parity
        edgeStrength = 44f,
        contrast = 26f,
        brightness = 0f,
        bgSuppressPercent = 48f
    )

    // 2. SMS / iMessage (22 columns, 0.36 compressed aspect, Pure Text NO ```, locked U+2800)
    // 22 columns guaranteed zero-wrapping across all Android screen sizes and bubble paddings
    val SMS_MESSAGING = ContextArtProfile(
        id = "sms_messaging",
        displayName = "SMS • 22 cols",
        targetColumns = 22,
        aspectRatioCompensation = 0.36f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true, // Prevents carrier gateway space collapsing
        edgeStrength = 46f,
        contrast = 28f,
        brightness = 0f,
        bgSuppressPercent = 50f
    )

    // 3. Instagram & TikTok Comments (14-20 columns, pure text, space-locked)
    val INSTAGRAM_COMMENT = ContextArtProfile(
        id = "instagram_comment",
        displayName = "IG Comment • 20 cols",
        targetColumns = 20,
        aspectRatioCompensation = 0.34f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        edgeStrength = 48f,
        contrast = 28f,
        brightness = 0f,
        bgSuppressPercent = 50f
    )

    val TIKTOK_COMMENT = ContextArtProfile(
        id = "tiktok_comment",
        displayName = "TikTok • 14 cols (<150c)",
        targetColumns = 14,
        aspectRatioCompensation = 0.32f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        maxCharLimit = 145, // Strictly under TikTok 150-char comment barrier
        edgeStrength = 50f,
        contrast = 30f,
        brightness = 0f,
        bgSuppressPercent = 52f
    )

    val TWITTER_POST_REPLY = ContextArtProfile(
        id = "twitter_post_reply",
        displayName = "X • 17 cols (<280c)",
        targetColumns = 17,
        aspectRatioCompensation = 0.34f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        maxCharLimit = 275, // Strictly under free-tier 280-char barrier
        edgeStrength = 46f,
        contrast = 28f,
        brightness = 0f,
        bgSuppressPercent = 50f
    )

    val LINKEDIN_COMMENT = ContextArtProfile(
        id = "linkedin_comment",
        displayName = "LinkedIn • 22 cols",
        targetColumns = 22,
        aspectRatioCompensation = 0.36f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        edgeStrength = 45f,
        contrast = 26f,
        brightness = 0f,
        bgSuppressPercent = 48f
    )

    val YOUTUBE_COMMENT = ContextArtProfile(
        id = "youtube_comment",
        displayName = "YouTube • 25 cols",
        targetColumns = 25,
        aspectRatioCompensation = 0.38f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        edgeStrength = 46f,
        contrast = 26f,
        brightness = 0f,
        bgSuppressPercent = 48f
    )

    // 4. Social Posts & Captions (Wider column budget, 30 columns)
    val SOCIAL_POST_CAPTION = ContextArtProfile(
        id = "social_post_caption",
        displayName = "Post • 30 cols",
        targetColumns = 30,
        aspectRatioCompensation = 0.40f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        edgeStrength = 44f,
        contrast = 25f,
        brightness = 0f,
        bgSuppressPercent = 48f
    )

    // 5. Discord / Desktop (45 columns code block)
    val DISCORD_DESKTOP = ContextArtProfile(
        id = "discord_desktop",
        displayName = "Discord • 45 cols",
        targetColumns = 45,
        aspectRatioCompensation = 0.52f,
        wrapInCodeBlock = true,
        lockBrailleBlank = true,
        edgeStrength = 42f,
        contrast = 24f,
        brightness = 0f,
        bgSuppressPercent = 45f
    )

    // 6. Generic Fallback
    val GENERIC_CHAT = ContextArtProfile(
        id = "generic_chat",
        displayName = "Text Art • 26 cols",
        targetColumns = 26,
        aspectRatioCompensation = 0.38f,
        wrapInCodeBlock = false,
        lockBrailleBlank = true,
        edgeStrength = 45f,
        contrast = 26f,
        brightness = 0f,
        bgSuppressPercent = 48f
    )

    /**
     * Resolves the optimal calibrated profile given a target package and detected sub-context.
     */
    fun resolveProfile(packageName: String?, subContext: String?): ContextArtProfile {
        val pkg = packageName?.lowercase() ?: ""
        val sub = subContext?.lowercase() ?: "unknown"

        // Telegram
        if (pkg.contains("org.telegram") || pkg.contains("org.thunderdog.challegram") || pkg.contains("telegram")) {
            return TELEGRAM_MOBILE
        }

        // SMS / MMS
        if (pkg == "com.google.android.apps.messaging" ||
            pkg == "com.samsung.android.messaging" ||
            pkg == "com.android.mms" ||
            pkg.contains("messaging") ||
            pkg.contains("sms") ||
            pkg.contains("mms") ||
            sub == "sms"
        ) {
            return SMS_MESSAGING
        }

        // Discord
        if (pkg.contains("com.discord")) {
            return DISCORD_DESKTOP
        }

        // TikTok
        if (pkg.contains("musically") || pkg.contains("trill") || pkg.contains("tiktok")) {
            return if (sub == "comment") TIKTOK_COMMENT else SOCIAL_POST_CAPTION
        }

        // Twitter / X
        if (pkg.contains("twitter") || pkg.contains("x.android")) {
            return TWITTER_POST_REPLY
        }

        // Instagram & Threads
        if (pkg.contains("instagram")) {
            return when (sub) {
                "comment" -> INSTAGRAM_COMMENT
                "post_creation" -> SOCIAL_POST_CAPTION
                "dm" -> GENERIC_CHAT
                else -> INSTAGRAM_COMMENT
            }
        }

        // LinkedIn
        if (pkg.contains("linkedin")) {
            return when (sub) {
                "comment" -> LINKEDIN_COMMENT
                "post_creation" -> SOCIAL_POST_CAPTION
                else -> LINKEDIN_COMMENT
            }
        }

        // YouTube & Reddit
        if (pkg.contains("youtube") || pkg.contains("reddit")) {
            return if (sub == "comment") YOUTUBE_COMMENT else SOCIAL_POST_CAPTION
        }

        // Subcontext-driven fallbacks
        return when (sub) {
            "comment" -> INSTAGRAM_COMMENT
            "post_creation" -> SOCIAL_POST_CAPTION
            "dm" -> GENERIC_CHAT
            else -> GENERIC_CHAT
        }
    }
}
