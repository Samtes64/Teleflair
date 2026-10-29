package com.teleboard.teleboard.keyboard

import android.content.BroadcastReceiver
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.teleboard.teleboard.keyboard.context.ContextScannerService
import com.teleboard.teleboard.keyboard.engine.SuggestionStripView
import com.teleboard.teleboard.keyboard.engine.TflitePredictionEngine
import com.teleboard.teleboard.keyboard.gesture.GestureTrailView
import com.teleboard.teleboard.keyboard.gesture.GlideTypingDetector
import com.teleboard.teleboard.keyboard.physics.KeyboardPhysicsView

class TeleboardInputMethodService : InputMethodService(),
    KeyboardLayoutView.KeyboardActionListener,
    StickerPickerView.StickerPickerListener,
    SuggestionStripView.SuggestionListener {

    private lateinit var dbHelper: TeleboardDatabaseHelper
    private lateinit var deliveryCoordinator: SmartDeliveryCoordinator
    private lateinit var predictionEngine: TflitePredictionEngine

    private var rootContainer: FrameLayout? = null
    private var mainRootLayout: LinearLayout? = null
    private var keyboardFrame: FrameLayout? = null
    private var keyboardView: KeyboardLayoutView? = null
    private var stickerPickerView: StickerPickerView? = null
    private var suggestionStripView: SuggestionStripView? = null
    private var physicsView: KeyboardPhysicsView? = null
    private var gestureTrailView: GestureTrailView? = null

    private val currentWordPrefix = StringBuilder()
    private var lastCommittedGlideWord: String? = null

    @Volatile private var currentPackage: String = ""
    @Volatile private var currentSubContext: String = "unknown"

    private val contextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.getStringExtra(ContextScannerService.EXTRA_PACKAGE_NAME) ?: ""
            val sub = intent?.getStringExtra(ContextScannerService.EXTRA_SUB_CONTEXT) ?: "unknown"
            if (pkg.isNotEmpty()) currentPackage = pkg
            currentSubContext = sub
            stickerPickerView?.updateContext(currentPackage, currentSubContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = TeleboardDatabaseHelper(this)
        deliveryCoordinator = SmartDeliveryCoordinator(this, dbHelper)
        predictionEngine = TflitePredictionEngine(this)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            contextReceiver,
            IntentFilter(ContextScannerService.ACTION_CONTEXT_MUTATION)
        )
    }

    override fun onCreateInputView(): View {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainRootLayout = rootLayout

        // 1. Suggestion Strip View (30dp)
        val ss = SuggestionStripView(this).apply {
            listener = this@TeleboardInputMethodService
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(30)
            )
        }
        suggestionStripView = ss
        rootLayout.addView(ss)

        // 2. Keyboard & Overlay Container
        val kbFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        keyboardFrame = kbFrame

        // 2a. Minimalist QWERTY Keyboard View
        val kb = KeyboardLayoutView(this).apply {
            actionListener = this@TeleboardInputMethodService
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        keyboardView = kb
        kbFrame.addView(kb)

        // 2b. Interactive Physics Particle View (transparent overlay)
        val pv = KeyboardPhysicsView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        physicsView = pv
        kb.physicsView = pv
        kbFrame.addView(pv)

        // 2c. Gesture Glide Trail View (transparent overlay)
        val gtv = GestureTrailView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        gestureTrailView = gtv
        kb.gestureTrailView = gtv
        kbFrame.addView(gtv)

        // Load user words into prediction engine
        predictionEngine.loadUserWords(dbHelper.getUserWords())

        // Initialize GlideTypingDetector with full lexicon words
        val detector = GlideTypingDetector(
            keyCentroids = kb.keyCentroids,
            initialTrieWords = predictionEngine.getAllLexiconWords()
        )
        kb.glideDetector = detector

        // 2d. Sticker Picker View
        val sp = StickerPickerView(this).apply {
            pickerListener = this@TeleboardInputMethodService
            dbHelper = this@TeleboardInputMethodService.dbHelper
            deliveryCoordinator = this@TeleboardInputMethodService.deliveryCoordinator
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        stickerPickerView = sp
        kbFrame.addView(sp)

        rootLayout.addView(kbFrame)
        rootContainer = kbFrame

        // Set palette on suggestion strip
        ss.setPalette(kb.currentPalette)

        adjustKeyboardLayoutHeight()

        return rootLayout
    }

    private fun getStickerPickerHeightDp(): Int {
        val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val heightPref = prefs.getString("flutter.keyboard_height_scale", "standard") ?: "standard"
        return when (heightPref.lowercase()) {
            "compact" -> 270
            "tall" -> 320
            "extra_tall" -> 345
            else -> 295
        }
    }

    private fun adjustKeyboardLayoutHeight() {
        if (stickerPickerView?.visibility == View.VISIBLE) {
            val pickerHeight = getStickerPickerHeightDp()
            keyboardFrame?.layoutParams?.height = dpToPx(pickerHeight)
            mainRootLayout?.layoutParams?.height = dpToPx(pickerHeight)
            keyboardFrame?.requestLayout()
            mainRootLayout?.requestLayout()
            window?.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return
        }

        val kbHeight = keyboardView?.totalKeyboardHeightDp ?: 232f
        val stripHeight = if (suggestionStripView?.visibility == View.VISIBLE) 30f else 0f
        val totalHeight = kbHeight + stripHeight

        keyboardFrame?.layoutParams?.height = dpToPx(kbHeight.toInt())
        keyboardFrame?.requestLayout()

        mainRootLayout?.layoutParams?.height = dpToPx(totalHeight.toInt())
        mainRootLayout?.requestLayout()

        window?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val root = mainRootLayout ?: return
        val loc = IntArray(2)
        root.getLocationInWindow(loc)
        val visibleTop = loc[1]
        outInsets.contentTopInsets = visibleTop
        outInsets.visibleTopInsets = visibleTop
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Check if field is sensitive (password, PIN)
        val isPassword = when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            else -> false
        }

        currentWordPrefix.clear()
        lastCommittedGlideWord = null
        keyboardView?.refreshTheme()
        adjustKeyboardLayoutHeight()
        keyboardView?.currentPalette?.let { suggestionStripView?.setPalette(it) }

        // Fast Heuristic Fallback
        val newPackage = info.packageName ?: ""
        val rawHint = info.hintText?.toString()?.lowercase() ?: ""
        val heuristicSubContext = when {
            rawHint.contains("comment") || rawHint.contains("reply") -> "comment"
            rawHint.contains("post") || rawHint.contains("share") -> "post_creation"
            rawHint.contains("message") || rawHint.contains("dm") || rawHint.contains("chat") -> "dm"
            else -> "unknown"
        }

        if (currentPackage != newPackage) {
            currentPackage = newPackage
            currentSubContext = heuristicSubContext
        } else if (heuristicSubContext != "unknown") {
            currentSubContext = heuristicSubContext
        }

        stickerPickerView?.updateContext(currentPackage, currentSubContext)

        // In password fields, ensure typing keyboard is visible and sticker picker is hidden
        if (isPassword) {
            suggestionStripView?.visibility = View.GONE
            physicsView?.pausePhysics() // Prevent timing-attack leakage
            showKeyboard()
        } else {
            // Default to Sticker Keyboard on input open as requested
            showStickerPicker()
        }

        // Configure Enter key action label
        val actionId = info.imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        val label = when (actionId) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "↵"
        }
        keyboardView?.enterActionLabel = label
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lastCommittedGlideWord = null
        currentWordPrefix.clear()
        physicsView?.pausePhysics() // Ensure 0% battery drain when keyboard closes
        gestureTrailView?.clearTrail()
    }

    private fun getCurrentWordPrefix(): String {
        val ic = currentInputConnection
        if (ic != null) {
            val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
            val prefix = before.takeLastWhile { it.isLetter() || it == '\'' }
            if (prefix.isNotEmpty()) {
                return prefix
            }
        }
        return currentWordPrefix.toString()
    }

    // --- KeyListener Callbacks ---

    override fun onCharacter(text: String) {
        lastCommittedGlideWord = null
        currentInputConnection?.commitText(text, 1)

        if (text == " " || text == "\n" || text.matches("[,.!?]".toRegex())) {
            val finishedWord = currentWordPrefix.toString().trim()
            if (finishedWord.isNotEmpty() && finishedWord.length >= 2) {
                dbHelper.learnWord(finishedWord)
                predictionEngine.learnWord(finishedWord, 300)
                keyboardView?.glideDetector?.addWord(finishedWord)
            }
            currentWordPrefix.clear()
        } else {
            currentWordPrefix.append(text)
        }

        updateSuggestions()
    }

    override fun onWord(word: String) {
        val ic = currentInputConnection ?: return
        lastCommittedGlideWord = null
        val prefix = getCurrentWordPrefix()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        currentWordPrefix.clear()
        ic.commitText("$word ", 1)
        dbHelper.learnWord(word)
        predictionEngine.learnWord(word, 500)
        keyboardView?.glideDetector?.addWord(word)
        updateSuggestions()
    }

    override fun onGlideWord(bestWord: String, alternativeCandidates: List<String>) {
        val ic = currentInputConnection ?: return
        val prefix = getCurrentWordPrefix()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        currentWordPrefix.clear()
        ic.commitText("$bestWord ", 1)
        lastCommittedGlideWord = bestWord
        dbHelper.learnWord(bestWord)
        predictionEngine.learnWord(bestWord, 500)
        keyboardView?.glideDetector?.addWord(bestWord)

        val stripCandidates = if (alternativeCandidates.isNotEmpty()) {
            alternativeCandidates
        } else {
            listOf(bestWord)
        }
        suggestionStripView?.setSuggestions(stripCandidates)
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        lastCommittedGlideWord = null
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
            if (currentWordPrefix.isNotEmpty()) {
                currentWordPrefix.deleteCharAt(currentWordPrefix.length - 1)
            }
        } else {
            ic.commitText("", 1)
            currentWordPrefix.clear()
        }
        updateSuggestions()
    }

    override fun onEnter() {
        lastCommittedGlideWord = null
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onOpenStickers() {
        lastCommittedGlideWord = null
        showStickerPicker()
    }

    override fun onPaste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val item = clip.getItemAt(0)
        val text = item.coerceToText(this)?.toString()

        if (!text.isNullOrEmpty()) {
            currentInputConnection?.commitText(text, 1)
            Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = item.uri
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val ic = currentInputConnection
            val ei = currentInputEditorInfo
            if (ic != null && ei != null) {
                val description = clip.description
                val mimeType = if (description != null && description.mimeTypeCount > 0) {
                    description.getMimeType(0)
                } else "image/png"
                val contentInfo = InputContentInfoCompat(
                    uri,
                    ClipDescription("clipboard_item", arrayOf(mimeType)),
                    null
                )
                val success = InputConnectionCompat.commitContent(
                    ic,
                    ei,
                    contentInfo,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    null
                )
                if (success) {
                    Toast.makeText(this, "Pasted image from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "App does not accept image paste", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- SuggestionListener Callback ---

    override fun onSuggestionSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = getCurrentWordPrefix()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        } else if (lastCommittedGlideWord != null) {
            val glideWord = lastCommittedGlideWord!!
            val checkLen = glideWord.length + 1
            val before = ic.getTextBeforeCursor(checkLen, 0)?.toString() ?: ""
            if (before == "$glideWord ") {
                ic.deleteSurroundingText(checkLen, 0)
            }
        }
        lastCommittedGlideWord = null
        currentWordPrefix.clear()
        ic.commitText("$word ", 1)
        dbHelper.learnWord(word)
        predictionEngine.learnWord(word, 500)
        keyboardView?.glideDetector?.addWord(word)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val info = currentInputEditorInfo ?: return
        val isPassword = when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            else -> false
        }

        if (isPassword) {
            suggestionStripView?.setSuggestions(emptyList())
            return
        }

        val prefix = getCurrentWordPrefix()
        val contextText = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val packageName = info.packageName

        val candidates = predictionEngine.getPredictions(
            prefix = prefix,
            contextSentence = contextText,
            packageName = packageName,
            isSensitive = false
        )
        suggestionStripView?.setSuggestions(candidates)
    }

    // --- StickerPickerListener Callbacks ---

    override fun onSendSticker(sticker: StickerEntity, choice: DeliveryChoice) {
        deliveryCoordinator.sendSticker(
            sticker = sticker,
            inputConnection = currentInputConnection,
            editorInfo = currentInputEditorInfo,
            choice = choice,
            subContext = currentSubContext
        )
        // Automatically dismiss sticker picker upon sending
        showKeyboard()
    }

    override fun onClosePicker() {
        showKeyboard()
    }

    private fun showStickerPicker() {
        keyboardView?.visibility = View.GONE
        suggestionStripView?.visibility = View.GONE
        val pickerHeight = getStickerPickerHeightDp()
        mainRootLayout?.layoutParams?.height = dpToPx(pickerHeight)
        keyboardFrame?.layoutParams?.height = dpToPx(pickerHeight)
        mainRootLayout?.requestLayout()
        window?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        stickerPickerView?.apply {
            updateContext(currentPackage, currentSubContext)
            visibility = View.VISIBLE
            reloadData()
        }
    }

    private fun showKeyboard() {
        stickerPickerView?.visibility = View.GONE
        suggestionStripView?.visibility = View.VISIBLE
        keyboardView?.visibility = View.VISIBLE
        keyboardView?.refreshTheme()
        adjustKeyboardLayoutHeight()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(contextReceiver)
        } catch (_: Exception) {}
        predictionEngine.close()
        keyboardView?.soundPlayer?.release()
        physicsView?.pausePhysics()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
