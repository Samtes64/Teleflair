package com.teleboard.teleboard.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.teleboard.teleboard.keyboard.theme.AppleIcon
import com.teleboard.teleboard.keyboard.theme.AppleIconView
import com.teleboard.teleboard.keyboard.theme.CircularGlideMenuView
import java.io.File
import java.util.concurrent.Executors

class StickerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface StickerPickerListener {
        fun onSendSticker(sticker: StickerEntity, choice: DeliveryChoice = DeliveryChoice.AUTO_ADAPT)
        fun onClosePicker()
    }

    var pickerListener: StickerPickerListener? = null
    var dbHelper: TeleboardDatabaseHelper? = null
    var deliveryCoordinator: SmartDeliveryCoordinator? = null

    private var currentTab: String = "all" // "all", "favorites", "recents", or packId
    private var selectedSticker: StickerEntity? = null
    private var currentStickersList = mutableListOf<StickerEntity>()

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 8MB in-memory LRU thumbnail cache
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    // Luxury Minimalist Dark Obsidian Palette
    private val bgDark = Color.parseColor("#080b11")
    private val cardSurface = Color.parseColor("#111722")
    private val cardBorder = Color.parseColor("#1c2637")
    private val accentCyan = Color.parseColor("#00c2ff")
    private val accentBlue = Color.parseColor("#0077b6")
    private val accentCyanGlow = Color.parseColor("#00e5ff")
    private val textPrimary = Color.parseColor("#ffffff")
    private val textMuted = Color.parseColor("#74869e")
    private val pillInactiveBg = Color.parseColor("#101520")
    private val pillInactiveBorder = Color.parseColor("#1d2738")
    private val pillActiveBg = Color.parseColor("#00b4d8")

    // Views
    private val mainContentLayout: LinearLayout
    private val tabsLayout: LinearLayout
    private val gridScrollView: ScrollView
    private val sectionsContainer: LinearLayout
    private val emptyView: LinearLayout
    private val emptyText: TextView

    // Bottom Navigation Bar: Normal App Navbar with 3 Icons (Keyboard, Center CTA Circle, Import)
    private val bottomNavBar: LinearLayout
    private val ctaButton: FrameLayout

    // Circular Glide Menu (iPad Radial Selection)
    private val circularMenu: CircularGlideMenuView

    private var currentTargetPackage: String? = null
    private var currentSubContext: String? = null

    init {
        setBackgroundColor(bgDark)
        clipChildren = false
        clipToPadding = false

        // 1. Main vertical container
        mainContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // --- TOP ROW: Overflowing Sticker Pack Horizontal Chips ---
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            val padH = dpToPx(8)
            setPadding(padH, dpToPx(8), padH, dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(42))
        }
        tabsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabScroll.addView(tabsLayout)
        mainContentLayout.addView(tabScroll)

        // --- CENTER: Scrollable Container with Pack Labels and 4-Col Grid ---
        val gridFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        gridScrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        sectionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpToPx(6)
            setPadding(p, 0, p, dpToPx(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        gridScrollView.addView(sectionsContainer)
        gridFrame.addView(gridScrollView)

        // Empty State View
        emptyView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val emptyIcon = AppleIconView(context).apply {
            iconType = AppleIcon.STICKER_FACE
            iconColor = textMuted
            iconStrokeWidthDp = 1.7f
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)).apply {
                bottomMargin = dpToPx(8)
            }
        }
        emptyView.addView(emptyIcon)

        emptyText = TextView(context).apply {
            text = "No stickers available"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(textMuted)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(10)
            }
        }
        emptyView.addView(emptyText)

        val emptyImportBtn = TextView(context).apply {
            text = "Import Telegram Pack"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(textPrimary)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            background = createPillDrawable(Color.parseColor("#0077b6"), cornerRadius = dpToPx(14))
            val padH = dpToPx(16)
            val padV = dpToPx(8)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                launchAppImportScreen()
            }
        }
        emptyView.addView(emptyImportBtn)
        gridFrame.addView(emptyView)

        mainContentLayout.addView(gridFrame)

        // --- BOTTOM BAR: Modern App Bottom Navigation Bar (3 Icons: Keyboard, Center CTA Circle, Import) ---
        bottomNavBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#0e121a"))
                setStroke(dpToPx(1f), Color.parseColor("#18202e"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56f)
            )
        }

        // 1. Left Action: Keyboard Icon (Switch to Text Typing Keyboard)
        val leftTab = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)

            val kbIcon = AppleIconView(context).apply {
                iconType = AppleIcon.KEYBOARD
                iconColor = Color.parseColor("#8ea2bd")
                iconStrokeWidthDp = 1.7f
                val s = dpToPx(24f)
                layoutParams = FrameLayout.LayoutParams(s, s).apply {
                    gravity = Gravity.CENTER
                }
            }
            addView(kbIcon)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                pickerListener?.onClosePicker()
            }
        }
        bottomNavBar.addView(leftTab)

        // 2. Center: The PROMINENT CIRCULAR CTA (Vibrant Indigo-Purple Circle as in Reference Image)
        val centerTab = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )

            val ctaSize = dpToPx(44f)
            ctaButton = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(ctaSize, ctaSize).apply {
                    gravity = Gravity.CENTER
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#5b5cf6")) // Vibrant luxury purple-indigo as in reference image
                    setStroke(dpToPx(1.5f), Color.parseColor("#8b71f9"))
                }
                elevation = dpToPx(6f).toFloat()

                val planeIcon = AppleIconView(context).apply {
                    iconType = AppleIcon.TELEGRAM_PLANE
                    iconColor = Color.WHITE
                    iconStrokeWidthDp = 1.9f
                    val s = dpToPx(22f)
                    layoutParams = FrameLayout.LayoutParams(s, s).apply {
                        gravity = Gravity.CENTER
                    }
                }
                addView(planeIcon)

                // Single Tap: Sends selected sticker directly as an Image!
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    executeSingleTapSendImage()
                }

                // Long Press (Hold): iPad circular radial glide selection menu!
                setOnLongClickListener {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    val rootLoc = IntArray(2)
                    this@StickerPickerView.getLocationOnScreen(rootLoc)
                    val cx = (loc[0] - rootLoc[0]) + width / 2f
                    val cy = (loc[1] - rootLoc[1]) + height / 2f
                    circularMenu.show(cx, cy, selectedSticker ?: currentStickersList.firstOrNull())
                    true
                }
            }
            addView(ctaButton)
        }
        bottomNavBar.addView(centerTab)

        // 3. Right Action: Plus / Import Icon (NO "IMPORT" text)
        val rightTab = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)

            val plusIcon = AppleIconView(context).apply {
                iconType = AppleIcon.PLUS
                iconColor = Color.parseColor("#8ea2bd")
                iconStrokeWidthDp = 1.9f
                val s = dpToPx(22f)
                layoutParams = FrameLayout.LayoutParams(s, s).apply {
                    gravity = Gravity.CENTER
                }
            }
            addView(plusIcon)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                launchAppImportScreen()
            }
        }
        bottomNavBar.addView(rightTab)

        mainContentLayout.addView(bottomNavBar)
        addView(mainContentLayout)

        // --- Circular Glide Menu Overlay (iPad Radial Selection) ---
        circularMenu = CircularGlideMenuView(context).apply {
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            onItemSelectedListener = { choice ->
                val stickerToSend = selectedSticker ?: currentStickersList.firstOrNull()
                if (stickerToSend != null) {
                    pickerListener?.onSendSticker(stickerToSend, choice)
                    pickerListener?.onClosePicker()
                }
            }
        }
        addView(circularMenu)
    }

    // --- UX: Single Tap sends selected sticker directly as Image ---
    private fun executeSingleTapSendImage() {
        val stickerToSend = selectedSticker ?: currentStickersList.firstOrNull()
        if (stickerToSend != null) {
            animateCtaTapPulse()
            pickerListener?.onSendSticker(stickerToSend, DeliveryChoice.RICH_IMAGE)
            pickerListener?.onClosePicker()
        } else {
            Toast.makeText(context, "Select a sticker first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateCtaTapPulse() {
        ctaButton.animate()
            .scaleX(0.85f).scaleY(0.85f).setDuration(70)
            .withEndAction {
                ctaButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
            }.start()
    }

    // --- Launch Teleflair Import Screen in Companion App ---
    private fun launchAppImportScreen() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                putExtra("route", "/import")
                putExtra("initial_tab", 1)
            }
            if (intent != null) {
                context.startActivity(intent)
                pickerListener?.onClosePicker()
            } else {
                Toast.makeText(context, "Open Teleflair app to import packs", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch Teleflair: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Data Loading & Pack Population ---
    fun reloadData() {
        val helper = dbHelper ?: return
        executor.execute {
            // Ensure any legacy dummy packs are completely removed
            helper.cleanupDummyPacks()
            val packs = helper.getAllPacks()
            mainHandler.post {
                buildPackTabs(packs)
                loadTabContent(currentTab)
            }
        }
    }

    private fun buildPackTabs(packs: List<StickerPackEntity>) {
        tabsLayout.removeAllViews()

        // 1. "ALL" Pack Chip
        tabsLayout.addView(createPackChip("ALL", "all"))

        // 2. "FAVORITES" Chip
        tabsLayout.addView(createPackChip("FAVORITES", "favorites"))

        // 3. "RECENT" Chip
        tabsLayout.addView(createPackChip("RECENT", "recents"))

        // 4. Each Installed Sticker Pack (Clean uppercase typography, zero emojis, no imported)
        for (pack in packs) {
            val title = cleanPackTitle(if (pack.title.isNotEmpty()) pack.title else pack.name).uppercase()
            if (title.equals("IMPORTED", ignoreCase = true) || pack.name.equals("Imported", ignoreCase = true)) {
                continue
            }
            tabsLayout.addView(createPackChip(title, pack.id))
        }
    }

    private fun cleanPackTitle(rawTitle: String): String {
        return rawTitle.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Cs}\\s]+"), "").trim()
    }

    private fun createPackChip(title: String, tag: String): TextView {
        val isSelected = (currentTab == tag)
        return TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.08f
            setTypeface(Typeface.create("sans-serif-medium", if (isSelected) Typeface.BOLD else Typeface.NORMAL))
            setTextColor(if (isSelected) textPrimary else textMuted)
            background = if (isSelected) {
                createPillDrawable(pillActiveBg, strokeColor = accentCyanGlow, strokeWidth = dpToPx(1), cornerRadius = dpToPx(14))
            } else {
                createPillDrawable(pillInactiveBg, strokeColor = pillInactiveBorder, strokeWidth = dpToPx(1), cornerRadius = dpToPx(14))
            }
            val padH = dpToPx(13)
            setPadding(padH, 0, padH, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28)).apply {
                rightMargin = dpToPx(6)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                currentTab = tag
                reloadData()
            }
        }
    }

    private fun loadTabContent(tabKey: String) {
        val helper = dbHelper ?: return
        selectedSticker = null
        updateCtaState(null)

        executor.execute {
            if (tabKey == "all") {
                val packs = helper.getAllPacks()
                val packMap = mutableMapOf<StickerPackEntity, List<StickerEntity>>()
                val allList = mutableListOf<StickerEntity>()
                for (pack in packs) {
                    val sList = helper.getStickersByPack(pack.id)
                    if (sList.isNotEmpty()) {
                        packMap[pack] = sList
                        allList.addAll(sList)
                    }
                }
                mainHandler.post {
                    currentStickersList.clear()
                    currentStickersList.addAll(allList)
                    populateGroupedPacks(packMap)
                }
            } else {
                val stickers = when (tabKey) {
                    "recents" -> helper.getRecentStickers(40)
                    "favorites" -> helper.getFavoriteStickers()
                    else -> helper.getStickersByPack(tabKey)
                }
                val packTitle = when (tabKey) {
                    "recents" -> "RECENT"
                    "favorites" -> "FAVORITES"
                    else -> cleanPackTitle(helper.getPackById(tabKey)?.title ?: "STICKERS").uppercase()
                }

                mainHandler.post {
                    currentStickersList.clear()
                    currentStickersList.addAll(stickers)
                    populateSinglePack(packTitle, stickers)
                }
            }
        }
    }

    // --- All stickers shown separated by Apple-style Section Labels ---
    private fun populateGroupedPacks(packMap: Map<StickerPackEntity, List<StickerEntity>>) {
        sectionsContainer.removeAllViews()

        if (packMap.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            gridScrollView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        gridScrollView.visibility = View.VISIBLE

        val displayWidth = context.resources.displayMetrics.widthPixels
        val itemSize = (displayWidth - dpToPx(40)) / 4

        for ((pack, stickers) in packMap) {
            val title = cleanPackTitle(if (pack.title.isNotEmpty()) pack.title else pack.name).uppercase()
            val headerView = createSectionHeader(title, "${stickers.size}")
            sectionsContainer.addView(headerView)

            val grid = createPackGrid(stickers, itemSize)
            sectionsContainer.addView(grid)
        }
    }

    private fun populateSinglePack(label: String, stickers: List<StickerEntity>) {
        sectionsContainer.removeAllViews()

        if (stickers.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            gridScrollView.visibility = View.GONE
            emptyText.text = if (label == "FAVORITES") "No favorites yet" else "No recent stickers"
            return
        }

        emptyView.visibility = View.GONE
        gridScrollView.visibility = View.VISIBLE

        val displayWidth = context.resources.displayMetrics.widthPixels
        val itemSize = (displayWidth - dpToPx(40)) / 4

        val headerView = createSectionHeader(label, "${stickers.size}")
        sectionsContainer.addView(headerView)

        val grid = createPackGrid(stickers, itemSize)
        sectionsContainer.addView(grid)
    }

    private fun createSectionHeader(title: String, countText: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(6)
            setPadding(padH, dpToPx(8), padH, dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            val labelView = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(Color.parseColor("#7e8fa6"))
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(labelView)

            val badgeView = TextView(context).apply {
                text = "$countText ITEMS"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setTextColor(Color.parseColor("#576980"))
                setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
                background = createPillDrawable(Color.parseColor("#111722"), cornerRadius = dpToPx(6))
                val p = dpToPx(5)
                setPadding(p, dpToPx(2), p, dpToPx(2))
            }
            addView(badgeView)
        }
    }

    private fun createPackGrid(stickers: List<StickerEntity>, itemSize: Int): GridLayout {
        return GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val p = dpToPx(2)
            setPadding(p, p, p, dpToPx(6))

            for (sticker in stickers) {
                val itemFrame = FrameLayout(context).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = itemSize
                        height = itemSize
                        val m = dpToPx(3)
                        setMargins(m, m, m, m)
                    }
                    background = createPillDrawable(cardSurface, strokeColor = cardBorder, strokeWidth = dpToPx(1), cornerRadius = dpToPx(14))
                }

                val imageView = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        val pad = dpToPx(8)
                        setMargins(pad, pad, pad, pad)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                itemFrame.addView(imageView)

                loadStickerThumbnail(sticker.filePath, imageView)

                // Single Tap UX: Select sticker / Tap already selected to send Image!
                itemFrame.setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (selectedSticker?.id == sticker.id) {
                        animateCtaTapPulse()
                        pickerListener?.onSendSticker(sticker, DeliveryChoice.RICH_IMAGE)
                        pickerListener?.onClosePicker()
                    } else {
                        highlightSelectedItem(itemFrame)
                        selectedSticker = sticker
                        updateCtaState(sticker)
                    }
                }

                // Long Press UX: Opens the luxury circular glide menu!
                itemFrame.setOnLongClickListener {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    highlightSelectedItem(itemFrame)
                    selectedSticker = sticker
                    updateCtaState(sticker)

                    val loc = IntArray(2)
                    itemFrame.getLocationOnScreen(loc)
                    val rootLoc = IntArray(2)
                    this@StickerPickerView.getLocationOnScreen(rootLoc)
                    val cx = (loc[0] - rootLoc[0]) + itemFrame.width / 2f
                    val cy = (loc[1] - rootLoc[1]) + itemFrame.height / 2f
                    circularMenu.show(cx, cy, sticker)
                    true
                }

                addView(itemFrame)
            }
        }
    }

    private fun highlightSelectedItem(selectedView: View) {
        for (i in 0 until sectionsContainer.childCount) {
            val child = sectionsContainer.getChildAt(i)
            if (child is GridLayout) {
                for (j in 0 until child.childCount) {
                    child.getChildAt(j).background = createPillDrawable(cardSurface, strokeColor = cardBorder, strokeWidth = dpToPx(1), cornerRadius = dpToPx(14))
                }
            }
        }
        selectedView.background = createPillDrawable(
            Color.parseColor("#152336"),
            strokeColor = accentCyanGlow,
            strokeWidth = dpToPx(2),
            cornerRadius = dpToPx(14)
        )
    }

    private fun updateCtaState(sticker: StickerEntity?) {
        if (sticker != null) {
            ctaButton.background = createPillDrawable(
                Color.parseColor("#0077b6"),
                strokeColor = Color.parseColor("#00e5ff"),
                strokeWidth = dpToPx(2),
                cornerRadius = dpToPx(21)
            )
            ctaButton.alpha = 1.0f
        } else {
            ctaButton.background = createPillDrawable(
                Color.parseColor("#005c8a"),
                strokeColor = Color.parseColor("#00b4d8"),
                strokeWidth = dpToPx(1.5f),
                cornerRadius = dpToPx(21)
            )
            ctaButton.alpha = 0.95f
        }
    }

    fun setTargetPackage(packageName: String?) {
        this.currentTargetPackage = packageName
        updateContext(packageName, currentSubContext)
    }

    fun updateContext(packageName: String?, subContext: String?) {
        this.currentTargetPackage = packageName
        this.currentSubContext = subContext
    }

    private fun loadStickerThumbnail(filePath: String, imageView: ImageView) {
        val cached = bitmapCache.get(filePath)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        executor.execute {
            val file = File(filePath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bitmap != null) {
                    bitmapCache.put(filePath, bitmap)
                    mainHandler.post {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    // --- Drawable Helpers ---
    private fun createPillDrawable(
        color: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0,
        cornerRadius: Int = dpToPx(14)
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius.toFloat()
            setColor(color)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
