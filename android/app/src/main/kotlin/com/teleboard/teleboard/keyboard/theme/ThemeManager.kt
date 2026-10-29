package com.teleboard.teleboard.keyboard.theme

import android.content.Context
import android.graphics.Color

enum class KeyboardThemeId(val key: String) {
    MIDNIGHT_SLATE("midnight_slate"),
    PITCH_BLACK_OLED("pitch_black_oled"),
    SLATE_GRAY("slate_gray"),
    PAPER_WHITE("paper_white"),
    HIGH_CONTRAST_CYBER("high_contrast_cyber")
}

data class KeyboardPalette(
    val id: KeyboardThemeId,
    val name: String,
    val bg: Int,
    val keyBgNormal: Int,
    val keyBgNormalPressed: Int,
    val keyBgSpecial: Int,
    val keyBgSpecialPressed: Int,
    val actionKeyBg: Int,
    val actionKeyBgPressed: Int,
    val actionKeyTextColor: Int,
    val textPrimary: Int,
    val textSubscript: Int,
    val textSpecial: Int,
    val suggestionBg: Int,
    val suggestionText: Int,
    val suggestionHighlight: Int,
    val textMuted: Int = textSubscript
)

class ThemeManager(private val context: Context) {

    private val palettes = mapOf(
        KeyboardThemeId.MIDNIGHT_SLATE to KeyboardPalette(
            id = KeyboardThemeId.MIDNIGHT_SLATE,
            name = "Midnight Slate",
            bg = Color.parseColor("#040716"),
            keyBgNormal = Color.parseColor("#0e1534"),
            keyBgNormalPressed = Color.parseColor("#222f54"),
            keyBgSpecial = Color.parseColor("#26345d"),
            keyBgSpecialPressed = Color.parseColor("#3a4d85"),
            actionKeyBg = Color.parseColor("#bcc7d6"),
            actionKeyBgPressed = Color.parseColor("#9eaebb"),
            actionKeyTextColor = Color.parseColor("#10172a"),
            textPrimary = Color.parseColor("#ffffff"),
            textSubscript = Color.parseColor("#7e8fa8"),
            textSpecial = Color.parseColor("#d8e2f0"),
            suggestionBg = Color.parseColor("#080c20"),
            suggestionText = Color.parseColor("#c4d1eb"),
            suggestionHighlight = Color.parseColor("#a8c7fa")
        ),
        KeyboardThemeId.PITCH_BLACK_OLED to KeyboardPalette(
            id = KeyboardThemeId.PITCH_BLACK_OLED,
            name = "Pitch Black (OLED)",
            bg = Color.parseColor("#000000"),
            keyBgNormal = Color.parseColor("#111111"),
            keyBgNormalPressed = Color.parseColor("#252525"),
            keyBgSpecial = Color.parseColor("#181818"),
            keyBgSpecialPressed = Color.parseColor("#303030"),
            actionKeyBg = Color.parseColor("#e0e0e0"),
            actionKeyBgPressed = Color.parseColor("#cccccc"),
            actionKeyTextColor = Color.parseColor("#000000"),
            textPrimary = Color.parseColor("#ffffff"),
            textSubscript = Color.parseColor("#666666"),
            textSpecial = Color.parseColor("#e0e0e0"),
            suggestionBg = Color.parseColor("#0a0a0a"),
            suggestionText = Color.parseColor("#cccccc"),
            suggestionHighlight = Color.parseColor("#ffffff")
        ),
        KeyboardThemeId.SLATE_GRAY to KeyboardPalette(
            id = KeyboardThemeId.SLATE_GRAY,
            name = "Slate Gray",
            bg = Color.parseColor("#1e222b"),
            keyBgNormal = Color.parseColor("#2b313d"),
            keyBgNormalPressed = Color.parseColor("#3a4252"),
            keyBgSpecial = Color.parseColor("#353d4c"),
            keyBgSpecialPressed = Color.parseColor("#454f63"),
            actionKeyBg = Color.parseColor("#7289da"),
            actionKeyBgPressed = Color.parseColor("#5b6eae"),
            actionKeyTextColor = Color.parseColor("#ffffff"),
            textPrimary = Color.parseColor("#f0f3f8"),
            textSubscript = Color.parseColor("#8b96a8"),
            textSpecial = Color.parseColor("#e0e6ed"),
            suggestionBg = Color.parseColor("#252a35"),
            suggestionText = Color.parseColor("#d0d7de"),
            suggestionHighlight = Color.parseColor("#99b2ff")
        ),
        KeyboardThemeId.PAPER_WHITE to KeyboardPalette(
            id = KeyboardThemeId.PAPER_WHITE,
            name = "Paper White",
            bg = Color.parseColor("#e8eaed"),
            keyBgNormal = Color.parseColor("#ffffff"),
            keyBgNormalPressed = Color.parseColor("#dadce0"),
            keyBgSpecial = Color.parseColor("#d2d6dc"),
            keyBgSpecialPressed = Color.parseColor("#bdc1c6"),
            actionKeyBg = Color.parseColor("#1a73e8"),
            actionKeyBgPressed = Color.parseColor("#1557b0"),
            actionKeyTextColor = Color.parseColor("#ffffff"),
            textPrimary = Color.parseColor("#202124"),
            textSubscript = Color.parseColor("#5f6368"),
            textSpecial = Color.parseColor("#3c4043"),
            suggestionBg = Color.parseColor("#f1f3f4"),
            suggestionText = Color.parseColor("#3c4043"),
            suggestionHighlight = Color.parseColor("#1a73e8")
        ),
        KeyboardThemeId.HIGH_CONTRAST_CYBER to KeyboardPalette(
            id = KeyboardThemeId.HIGH_CONTRAST_CYBER,
            name = "Cyberpunk Neon",
            bg = Color.parseColor("#050811"),
            keyBgNormal = Color.parseColor("#0b1224"),
            keyBgNormalPressed = Color.parseColor("#192548"),
            keyBgSpecial = Color.parseColor("#15223e"),
            keyBgSpecialPressed = Color.parseColor("#223766"),
            actionKeyBg = Color.parseColor("#00ffcc"),
            actionKeyBgPressed = Color.parseColor("#00cca3"),
            actionKeyTextColor = Color.parseColor("#050811"),
            textPrimary = Color.parseColor("#00ffcc"),
            textSubscript = Color.parseColor("#ff007f"),
            textSpecial = Color.parseColor("#00e5ff"),
            suggestionBg = Color.parseColor("#080f20"),
            suggestionText = Color.parseColor("#00ffcc"),
            suggestionHighlight = Color.parseColor("#ff007f")
        )
    )

    fun getCurrentPalette(): KeyboardPalette {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val themeKey = prefs.getString("flutter.keyboard_theme", KeyboardThemeId.MIDNIGHT_SLATE.key)
        val id = KeyboardThemeId.values().find { it.key.equals(themeKey, ignoreCase = true) }
            ?: KeyboardThemeId.MIDNIGHT_SLATE
        return palettes[id] ?: palettes[KeyboardThemeId.MIDNIGHT_SLATE]!!
    }
}
