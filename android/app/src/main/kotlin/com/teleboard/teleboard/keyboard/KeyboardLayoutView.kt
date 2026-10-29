package com.teleboard.teleboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.teleboard.teleboard.keyboard.gesture.GestureTrailView
import com.teleboard.teleboard.keyboard.gesture.GlideTypingDetector
import com.teleboard.teleboard.keyboard.physics.KeyboardPhysicsView
import com.teleboard.teleboard.keyboard.theme.KeyboardPalette
import com.teleboard.teleboard.keyboard.theme.SoundFeedbackPlayer
import com.teleboard.teleboard.keyboard.theme.ThemeManager

enum class KeyboardMode {
    ALPHA,
    NUMERIC,
    SYMBOLS
}

class KeyboardLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface KeyboardActionListener {
        fun onCharacter(text: String)
        fun onWord(word: String)
        fun onGlideWord(bestWord: String, alternativeCandidates: List<String>) {
            onWord(bestWord)
        }
        fun onBackspace()
        fun onEnter()
        fun onOpenStickers()
        fun onPaste()
    }

    var actionListener: KeyboardActionListener? = null
    var enterActionLabel: String = "↵"
        set(value) {
            field = value
            enterKeyView?.invalidate()
        }

    val themeManager = ThemeManager(context)
    val soundPlayer = SoundFeedbackPlayer(context)
    var currentPalette: KeyboardPalette = themeManager.getCurrentPalette()

    // --- Customization Settings ---
    var baseKeyTextSizeSp: Float = 21.0f
    var subTextSizeSp: Float = 10.0f
    var keyRowHeightDp: Float = 44.0f
    var totalKeyboardHeightDp: Float = 236.0f
    var keyCornerRadiusDp: Float = 6.5f
    var showSubscripts: Boolean = true
    var longPressDelayMs: Long = 450L

    var physicsView: KeyboardPhysicsView? = null
    var gestureTrailView: GestureTrailView? = null
    var glideDetector: GlideTypingDetector? = null
    val keyCentroids = HashMap<Char, PointF>()

    private var isShifted = false
    private var isCapsLock = false
    private var mode = KeyboardMode.ALPHA

    private var enterKeyView: ActionKeyView? = null
    private var shiftKeyView: SpecialKeyView? = null

    private val compoundKeyViews = mutableListOf<CompoundKeyView>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRepeatingBackspace = false
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (isRepeatingBackspace) {
                actionListener?.onBackspace()
                soundPlayer.playKeySound(this@KeyboardLayoutView)
                mainHandler.postDelayed(this, 35)
            }
        }
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        loadCustomizationSettings()
        setBackgroundColor(currentPalette.bg)
        val padH = dpToPx(3f)
        val padV = dpToPx(2f)
        val padBottom = dpToPx(8f)
        setPadding(padH, padV, padH, padBottom)
        buildKeyboard()
    }

    fun loadCustomizationSettings() {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

        // Font Size: small (18.5sp), medium (21.0sp - default), large (23.5sp), xlarge (26.0sp)
        val fontSizePref = prefs.getString("flutter.keyboard_font_size", "medium") ?: "medium"
        when (fontSizePref.lowercase()) {
            "small" -> {
                baseKeyTextSizeSp = 18.5f
                subTextSizeSp = 8.5f
            }
            "large" -> {
                baseKeyTextSizeSp = 23.5f
                subTextSizeSp = 11.5f
            }
            "xlarge" -> {
                baseKeyTextSizeSp = 26.0f
                subTextSizeSp = 13.0f
            }
            else -> {
                baseKeyTextSizeSp = 21.0f
                subTextSizeSp = 10.0f
            }
        }

        // Keyboard Height: compact, standard (default), tall, extra_tall
        val heightPref = prefs.getString("flutter.keyboard_height_scale", "standard") ?: "standard"
        when (heightPref.lowercase()) {
            "compact" -> {
                keyRowHeightDp = 38.0f
                totalKeyboardHeightDp = 210.0f
            }
            "tall" -> {
                keyRowHeightDp = 47.0f
                totalKeyboardHeightDp = 248.0f
            }
            "extra_tall" -> {
                keyRowHeightDp = 51.0f
                totalKeyboardHeightDp = 264.0f
            }
            else -> {
                keyRowHeightDp = 43.0f
                totalKeyboardHeightDp = 232.0f
            }
        }

        // Key Shape: rounded (default, 6.5dp), pill (15.0dp), box (3.5dp)
        val shapePref = prefs.getString("flutter.keyboard_key_shape", "rounded") ?: "rounded"
        keyCornerRadiusDp = when (shapePref.lowercase()) {
            "pill" -> 15.0f
            "box" -> 3.5f
            else -> 6.5f
        }

        // Show Subscripts: boolean (default true)
        showSubscripts = prefs.getBoolean("flutter.keyboard_show_subscripts", true)

        // Long Press Delay: 300, 450 (default), 600
        val delayPref = prefs.getString("flutter.keyboard_long_press_delay", "450") ?: "450"
        longPressDelayMs = when (delayPref) {
            "300" -> 300L
            "600" -> 600L
            else -> 450L
        }
    }

    fun refreshTheme() {
        loadCustomizationSettings()
        currentPalette = themeManager.getCurrentPalette()
        setBackgroundColor(currentPalette.bg)
        buildKeyboard()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = dpToPx(totalKeyboardHeightDp)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, exactHeightSpec)
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Dynamically compute key centroids for accurate DTW glide matching
        if (changed || keyCentroids.isEmpty()) {
            keyCentroids.clear()
            for (keyView in compoundKeyViews) {
                val loc = IntArray(2)
                keyView.getLocationInWindow(loc)
                val thisLoc = IntArray(2)
                getLocationInWindow(thisLoc)

                val relX = (loc[0] - thisLoc[0]) + (keyView.width / 2f)
                val relY = (loc[1] - thisLoc[1]) + (keyView.height / 2f)
                val charKey = keyView.mainChar.lowercase().firstOrNull()
                if (charKey != null) {
                    keyCentroids[charKey] = PointF(relX, relY)
                }
            }
        }
    }

    private var isCurrentlyGliding = false

    private fun cancelKeyHighlights() {
        for (k in compoundKeyViews) {
            if (k.isPressed) {
                k.isPressed = false
                k.invalidate()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val glideEnabled = prefs.getBoolean("flutter.keyboard_glide_enabled", true)

        if (glideEnabled && mode == KeyboardMode.ALPHA) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    isCurrentlyGliding = false
                    glideDetector?.onTouchDown(ev.x, ev.y)
                    gestureTrailView?.addPoint(ev.x, ev.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (glideDetector?.onTouchMove(ev.x, ev.y) == true) {
                        isCurrentlyGliding = true
                        gestureTrailView?.addPoint(ev.x, ev.y)
                        cancelKeyHighlights()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isCurrentlyGliding) {
                        isCurrentlyGliding = false
                        gestureTrailView?.clearTrail()
                        cancelKeyHighlights()
                        val candidates = glideDetector?.onTouchUpCandidates() ?: emptyList()
                        if (candidates.isNotEmpty()) {
                            actionListener?.onGlideWord(candidates.first(), candidates)
                        }
                        return true
                    }
                    glideDetector?.clear()
                    gestureTrailView?.clearTrail()
                }
                MotionEvent.ACTION_CANCEL -> {
                    isCurrentlyGliding = false
                    glideDetector?.clear()
                    gestureTrailView?.clearTrail()
                    cancelKeyHighlights()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun buildKeyboard() {
        removeAllViews()
        compoundKeyViews.clear()

        buildTopToolbar()

        when (mode) {
            KeyboardMode.ALPHA -> buildAlphaLayout()
            KeyboardMode.NUMERIC -> buildNumericLayout()
            KeyboardMode.SYMBOLS -> buildSymbolsLayout()
        }
    }

    private fun buildTopToolbar() {
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(30f)).apply {
                bottomMargin = dpToPx(3f)
                leftMargin = dpToPx(3f)
                rightMargin = dpToPx(3f)
            }
        }

        val spacer = View(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        toolbar.addView(spacer)

        // Functional Paste Button next to Stickers CTA
        val clipBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(keyCornerRadiusDp).toFloat()
                setColor(currentPalette.keyBgSpecial)
                setStroke(dpToPx(0.8f), Color.parseColor("#2a3b50"))
            }
            val padH = dpToPx(9f)
            setPadding(padH, 0, padH, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(26f)).apply {
                rightMargin = dpToPx(8f)
            }
            val clipIcon = com.teleboard.teleboard.keyboard.theme.AppleIconView(context).apply {
                iconType = com.teleboard.teleboard.keyboard.theme.AppleIcon.CLIPBOARD
                iconColor = currentPalette.textSpecial
                iconStrokeWidthDp = 1.5f
                layoutParams = LayoutParams(dpToPx(14f), dpToPx(14f)).apply {
                    rightMargin = dpToPx(5f)
                }
            }
            addView(clipIcon)

            val clipTxt = TextView(context).apply {
                text = "PASTE"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(currentPalette.textSpecial)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                letterSpacing = 0.06f
            }
            addView(clipTxt)

            setOnClickListener {
                soundPlayer.playKeySound(this)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                actionListener?.onPaste()
            }
        }
        toolbar.addView(clipBtn)

        // The One Perfect CTA to Sticker Keyboard
        val stickersCta = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(13f).toFloat()
                setColor(Color.parseColor("#122336"))
                setStroke(dpToPx(1f), Color.parseColor("#00b4d8"))
            }
            val padH = dpToPx(10f)
            setPadding(padH, 0, padH, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(26f))

            val icon = com.teleboard.teleboard.keyboard.theme.AppleIconView(context).apply {
                iconType = com.teleboard.teleboard.keyboard.theme.AppleIcon.STICKER_FACE
                iconColor = Color.parseColor("#00c2ff")
                iconStrokeWidthDp = 1.5f
                layoutParams = LayoutParams(dpToPx(15f), dpToPx(15f)).apply {
                    rightMargin = dpToPx(5f)
                }
            }
            addView(icon)

            val txt = TextView(context).apply {
                text = "STICKERS"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.parseColor("#00c2ff"))
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                letterSpacing = 0.08f
            }
            addView(txt)

            setOnClickListener {
                soundPlayer.playKeySound(this)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                actionListener?.onOpenStickers()
            }
        }
        toolbar.addView(stickersCta)

        addView(toolbar)
    }

    private fun buildAlphaLayout() {
        val row1 = listOf(
            Pair("q", "1"), Pair("w", "2"), Pair("e", "3"), Pair("r", "4"), Pair("t", "5"),
            Pair("y", "6"), Pair("u", "7"), Pair("i", "8"), Pair("o", "9"), Pair("p", "0")
        )
        addView(createCompoundRow(row1))

        val row2 = listOf(
            Pair("a", "@"), Pair("s", "#"), Pair("d", "£"), Pair("f", "_"), Pair("g", "&"),
            Pair("h", "-"), Pair("j", "+"), Pair("k", "("), Pair("l", ")")
        )
        addView(createCompoundRow(row2, sidePadding = dpToPx(16f)))

        val row3Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp)).apply {
                bottomMargin = dpToPx(4f)
            }
        }

        val shiftKey = SpecialKeyView(
            context = context,
            label = if (isCapsLock) "⇪" else if (isShifted) "▲" else "⇧",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = if (isCapsLock || isShifted) Color.parseColor("#a8c7fa") else currentPalette.textSpecial,
            textSizeSp = 16f
        ) {
            handleShiftToggle()
        }
        shiftKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        shiftKeyView = shiftKey
        row3Layout.addView(shiftKey)

        val row3 = listOf(
            Pair("z", "*"), Pair("x", "\""), Pair("c", "'"), Pair("v", ":"),
            Pair("b", ";"), Pair("n", "!"), Pair("m", "?")
        )
        for (pair in row3) {
            val key = createCompoundKey(pair.first, pair.second, weight = 1f)
            row3Layout.addView(key)
        }

        val backspaceKey = SpecialKeyView(
            context = context,
            label = "⌫",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = currentPalette.textSpecial,
            textSizeSp = 16f,
            onClick = null
        )
        backspaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        setupBackspaceTouch(backspaceKey)
        row3Layout.addView(backspaceKey)

        addView(row3Layout)

        val row4Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp))
        }

        val numKey = SpecialKeyView(
            context = context,
            label = "?123",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = currentPalette.textSpecial,
            textSizeSp = 13.5f
        ) {
            mode = KeyboardMode.NUMERIC
            buildKeyboard()
        }
        numKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(numKey)

        val commaKey = SpecialKeyView(
            context = context,
            label = ",",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = currentPalette.textSpecial,
            textSizeSp = 18f
        ) {
            actionListener?.onCharacter(",")
        }
        commaKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(commaKey)

        val stickerKey = SpecialKeyView(
            context = context,
            label = "STICKER_ICON",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = currentPalette.textSpecial,
            textSizeSp = 16f
        ) {
            actionListener?.onOpenStickers()
        }
        stickerKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(stickerKey)

        val spaceKey = SpecialKeyView(
            context = context,
            label = "English (UK)",
            normalBg = currentPalette.keyBgNormal,
            pressedBg = currentPalette.keyBgNormalPressed,
            textColor = currentPalette.textMuted,
            textSizeSp = 12.5f
        ) {
            actionListener?.onCharacter(" ")
        }
        spaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 4.4f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(spaceKey)

        val dotKey = SpecialKeyView(
            context = context,
            label = ".",
            normalBg = currentPalette.keyBgSpecial,
            pressedBg = currentPalette.keyBgSpecialPressed,
            textColor = currentPalette.textSpecial,
            textSizeSp = 18f
        ) {
            actionListener?.onCharacter(".")
        }
        dotKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(dotKey)

        val enterKey = ActionKeyView(
            context = context,
            normalBg = currentPalette.actionKeyBg,
            pressedBg = currentPalette.actionKeyBgPressed,
            iconColor = currentPalette.actionKeyTextColor
        ) {
            actionListener?.onEnter()
        }
        enterKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.45f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        enterKeyView = enterKey
        row4Layout.addView(enterKey)

        addView(row4Layout)
    }

    private fun handleShiftToggle() {
        if (isShifted && !isCapsLock) {
            isCapsLock = true
        } else if (isCapsLock) {
            isCapsLock = false
            isShifted = false
        } else {
            isShifted = true
        }
        updateShiftedVisuals()
    }

    private fun updateShiftedVisuals() {
        shiftKeyView?.label = if (isCapsLock) "⇪" else if (isShifted) "▲" else "⇧"
        shiftKeyView?.textColor = if (isCapsLock || isShifted) Color.parseColor("#a8c7fa") else currentPalette.textSpecial
        shiftKeyView?.invalidate()

        for (key in compoundKeyViews) {
            key.isShifted = (isShifted || isCapsLock)
        }
    }

    private fun buildNumericLayout() {
        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        addView(createSimpleRow(row1))

        val row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
        addView(createSimpleRow(row2))

        val row3Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp)).apply {
                bottomMargin = dpToPx(4f)
            }
        }
        val symKey = SpecialKeyView(context, "=\\<", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 13f) {
            mode = KeyboardMode.SYMBOLS
            buildKeyboard()
        }
        symKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row3Layout.addView(symKey)

        val r3 = listOf(".", ",", "?", "!", "'")
        for (k in r3) {
            val key = SpecialKeyView(context, k, currentPalette.keyBgNormal, currentPalette.keyBgNormalPressed, currentPalette.textPrimary, baseKeyTextSizeSp) {
                actionListener?.onCharacter(k)
            }
            key.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.2f).apply {
                setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
            }
            row3Layout.addView(key)
        }

        val backspaceKey = SpecialKeyView(context, "⌫", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 16f, onClick = null)
        backspaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        setupBackspaceTouch(backspaceKey)
        row3Layout.addView(backspaceKey)
        addView(row3Layout)

        val row4Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp))
        }
        val abcKey = SpecialKeyView(context, "ABC", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 13.5f) {
            mode = KeyboardMode.ALPHA
            buildKeyboard()
        }
        abcKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(abcKey)

        val commaKey = SpecialKeyView(context, ",", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 18f) {
            actionListener?.onCharacter(",")
        }
        commaKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(commaKey)

        val stickerKey = SpecialKeyView(context, "STICKER_ICON", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 16f) {
            actionListener?.onOpenStickers()
        }
        stickerKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(stickerKey)

        val spaceKey = SpecialKeyView(context, "English (UK)", currentPalette.keyBgNormal, currentPalette.keyBgNormalPressed, currentPalette.textMuted, 12.5f) {
            actionListener?.onCharacter(" ")
        }
        spaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 4.4f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(spaceKey)

        val dotKey = SpecialKeyView(context, ".", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 18f) {
            actionListener?.onCharacter(".")
        }
        dotKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(dotKey)

        val enterKey = ActionKeyView(context, currentPalette.actionKeyBg, currentPalette.actionKeyBgPressed, currentPalette.actionKeyTextColor) {
            actionListener?.onEnter()
        }
        enterKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.45f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        enterKeyView = enterKey
        row4Layout.addView(enterKey)

        addView(row4Layout)
    }

    private fun buildSymbolsLayout() {
        val row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
        addView(createSimpleRow(row1))

        val row2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
        addView(createSimpleRow(row2))

        val row3Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp)).apply {
                bottomMargin = dpToPx(4f)
            }
        }
        val numKey = SpecialKeyView(context, "?123", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 13.5f) {
            mode = KeyboardMode.NUMERIC
            buildKeyboard()
        }
        numKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row3Layout.addView(numKey)

        val r3 = listOf(".", ",", "?", "!", "'")
        for (k in r3) {
            val key = SpecialKeyView(context, k, currentPalette.keyBgNormal, currentPalette.keyBgNormalPressed, currentPalette.textPrimary, baseKeyTextSizeSp) {
                actionListener?.onCharacter(k)
            }
            key.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.2f).apply {
                setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
            }
            row3Layout.addView(key)
        }

        val backspaceKey = SpecialKeyView(context, "⌫", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 16f, onClick = null)
        backspaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        setupBackspaceTouch(backspaceKey)
        row3Layout.addView(backspaceKey)
        addView(row3Layout)

        val row4Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp))
        }
        val abcKey = SpecialKeyView(context, "ABC", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 13.5f) {
            mode = KeyboardMode.ALPHA
            buildKeyboard()
        }
        abcKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.35f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(abcKey)

        val commaKey = SpecialKeyView(context, ",", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 18f) {
            actionListener?.onCharacter(",")
        }
        commaKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(commaKey)

        val stickerKey = SpecialKeyView(context, "STICKER_ICON", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 16f) {
            actionListener?.onOpenStickers()
        }
        stickerKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(stickerKey)

        val spaceKey = SpecialKeyView(context, "English (UK)", currentPalette.keyBgNormal, currentPalette.keyBgNormalPressed, currentPalette.textMuted, 12.5f) {
            actionListener?.onCharacter(" ")
        }
        spaceKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 4.4f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(spaceKey)

        val dotKey = SpecialKeyView(context, ".", currentPalette.keyBgSpecial, currentPalette.keyBgSpecialPressed, currentPalette.textSpecial, 18f) {
            actionListener?.onCharacter(".")
        }
        dotKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.0f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        row4Layout.addView(dotKey)

        val enterKey = ActionKeyView(context, currentPalette.actionKeyBg, currentPalette.actionKeyBgPressed, currentPalette.actionKeyTextColor) {
            actionListener?.onEnter()
        }
        enterKey.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1.45f).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        enterKeyView = enterKey
        row4Layout.addView(enterKey)

        addView(row4Layout)
    }

    private fun createCompoundRow(keys: List<Pair<String, String>>, sidePadding: Int = 0): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp)).apply {
                bottomMargin = dpToPx(4f)
                if (sidePadding > 0) {
                    setPadding(sidePadding, 0, sidePadding, 0)
                }
            }
        }
        for (pair in keys) {
            row.addView(createCompoundKey(pair.first, pair.second, weight = 1f))
        }
        return row
    }

    private fun createCompoundKey(mainChar: String, subChar: String, weight: Float): CompoundKeyView {
        val key = CompoundKeyView(
            context = context,
            mainChar = mainChar,
            subChar = subChar,
            normalBg = currentPalette.keyBgNormal,
            pressedBg = currentPalette.keyBgNormalPressed,
            textWhite = currentPalette.textPrimary,
            textSubscript = currentPalette.textSubscript,
            onKeyCommit = { charToCommit ->
                actionListener?.onCharacter(charToCommit)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                    updateShiftedVisuals()
                }
            },
            onKeyTapFeedback = { x, y ->
                soundPlayer.playKeySound(this)
                physicsView?.triggerKeyTapImpulse(x, y, currentPalette.keyBgSpecial)
            }
        )
        key.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), weight).apply {
            setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
        }
        compoundKeyViews.add(key)
        return key
    }

    private fun createSimpleRow(keys: List<String>): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(keyRowHeightDp)).apply {
                bottomMargin = dpToPx(4f)
            }
        }
        for (k in keys) {
            val key = SpecialKeyView(context, k, currentPalette.keyBgNormal, currentPalette.keyBgNormalPressed, currentPalette.textPrimary, baseKeyTextSizeSp) {
                actionListener?.onCharacter(k)
            }
            key.layoutParams = LayoutParams(0, dpToPx(keyRowHeightDp), 1f).apply {
                setMargins(dpToPx(2f), 0, dpToPx(2f), 0)
            }
            row.addView(key)
        }
        return row
    }

    private fun setupBackspaceTouch(keyView: View) {
        keyView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.invalidate()
                    soundPlayer.playKeySound(v)
                    actionListener?.onBackspace()
                    isRepeatingBackspace = true
                    mainHandler.postDelayed(backspaceRunnable, 280)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.invalidate()
                    isRepeatingBackspace = false
                    mainHandler.removeCallbacks(backspaceRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    // --- High-Performance Single-Pass Canvas Views ---

    inner class CompoundKeyView(
        context: Context,
        val mainChar: String,
        val subChar: String,
        private val normalBg: Int,
        private val pressedBg: Int,
        textWhite: Int,
        textSubscript: Int,
        private val onKeyCommit: (String) -> Unit,
        private val onKeyTapFeedback: ((Float, Float) -> Unit)? = null
    ) : View(context) {

        var isShifted: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textWhite
            textSize = spToPx(baseKeyTextSizeSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSubscript
            textSize = spToPx(subTextSizeSp)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        private val bgRect = RectF()
        private val cornerRadius get() = dpToPx(keyCornerRadiusDp)

        private val handler = Handler(Looper.getMainLooper())
        private var longPressTriggered = false
        private var startX = 0f
        private var startY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private val longPressRunnable = Runnable {
            if (subChar.isNotEmpty()) {
                longPressTriggered = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onKeyCommit(subChar)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            bgPaint.color = if (isPressed) pressedBg else normalBg
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            if (showSubscripts && subChar.isNotEmpty()) {
                val subX = width - dpToPx(3.5f)
                val subY = dpToPx(10.5f)
                canvas.drawText(subChar, subX, subY, subTextPaint)
            }

            val charToDraw = if (isShifted) mainChar.uppercase() else mainChar.lowercase()
            val textY = (height / 2f) - ((mainTextPaint.descent() + mainTextPaint.ascent()) / 2f) + dpToPx(0.5f)
            canvas.drawText(charToDraw, width / 2f, textY, mainTextPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    longPressTriggered = false
                    isPressed = true
                    invalidate()
                    onKeyTapFeedback?.invoke(event.rawX, event.rawY)
                    handler.postDelayed(longPressRunnable, longPressDelayMs)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPressed) {
                        val dx = Math.abs(event.x - startX)
                        val dy = Math.abs(event.y - startY)
                        if (dx > touchSlop || dy > touchSlop || event.x < 0 || event.x > width || event.y < 0 || event.y > height) {
                            handler.removeCallbacks(longPressRunnable)
                            isPressed = false
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (isPressed && !longPressTriggered) {
                        val charToCommit = if (isShifted) mainChar.uppercase() else mainChar.lowercase()
                        onKeyCommit(charToCommit)
                    }
                    isPressed = false
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    isPressed = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
        private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
    }

    inner class SpecialKeyView(
        context: Context,
        var label: String,
        private val normalBg: Int,
        private val pressedBg: Int,
        var textColor: Int,
        private val textSizeSp: Float,
        private val onClick: (() -> Unit)? = null
    ) : View(context) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            val scaleFactor = baseKeyTextSizeSp / 21.0f
            textSize = spToPx(textSizeSp * scaleFactor)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val bgRect = RectF()
        private val cornerRadius get() = dpToPx(keyCornerRadiusDp)

        private var startX = 0f
        private var startY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            bgPaint.color = if (isPressed) pressedBg else normalBg
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            if (label == "STICKER_ICON") {
                val cx = width / 2f
                val cy = height / 2f
                val r = Math.min(width, height) * 0.28f
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    style = Paint.Style.STROKE
                    strokeWidth = dpToPx(1.6f)
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawCircle(cx, cy, r, iconPaint)
                canvas.drawCircle(cx - r * 0.36f, cy - r * 0.14f, r * 0.11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; style = Paint.Style.FILL })
                canvas.drawCircle(cx + r * 0.36f, cy - r * 0.14f, r * 0.11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; style = Paint.Style.FILL })
                val mouthRect = RectF(cx - r * 0.45f, cy - r * 0.18f, cx + r * 0.45f, cy + r * 0.50f)
                canvas.drawArc(mouthRect, 25f, 130f, false, iconPaint)
            } else {
                textPaint.color = textColor
                val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText(label, width / 2f, textY, textPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (onClick == null) return super.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isPressed = true
                    invalidate()
                    soundPlayer.playKeySound(this)
                    physicsView?.triggerKeyTapImpulse(event.rawX, event.rawY, normalBg)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPressed) {
                        val dx = Math.abs(event.x - startX)
                        val dy = Math.abs(event.y - startY)
                        if (dx > touchSlop || dy > touchSlop || event.x < 0 || event.x > width || event.y < 0 || event.y > height) {
                            isPressed = false
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isPressed) {
                        isPressed = false
                        invalidate()
                        onClick.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
        private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
    }

    inner class ActionKeyView(
        context: Context,
        private val normalBg: Int,
        private val pressedBg: Int,
        private val iconColor: Int,
        private val onClick: () -> Unit
    ) : View(context) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            val scaleFactor = baseKeyTextSizeSp / 21.0f
            textSize = spToPx(16.5f * scaleFactor)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val bgRect = RectF()
        private val cornerRadius get() = dpToPx(keyCornerRadiusDp)

        private var startX = 0f
        private var startY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            bgPaint.color = if (isPressed) pressedBg else normalBg
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            val icon = when (enterActionLabel.lowercase()) {
                "search" -> "Search"
                "send" -> "➤"
                "done" -> "✓"
                "go" -> "➔"
                "next" -> "➔"
                else -> "↵"
            }
            val textY = (height / 2f) - ((iconPaint.descent() + iconPaint.ascent()) / 2f)
            canvas.drawText(icon, width / 2f, textY, iconPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isPressed = true
                    invalidate()
                    soundPlayer.playKeySound(this)
                    physicsView?.triggerKeyTapImpulse(event.rawX, event.rawY, normalBg)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPressed) {
                        val dx = Math.abs(event.x - startX)
                        val dy = Math.abs(event.y - startY)
                        if (dx > touchSlop || dy > touchSlop || event.x < 0 || event.x > width || event.y < 0 || event.y > height) {
                            isPressed = false
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isPressed) {
                        isPressed = false
                        invalidate()
                        onClick.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
        private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
    }
}
