package com.teleboard.teleboard.keyboard.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.teleboard.teleboard.keyboard.DeliveryChoice
import com.teleboard.teleboard.keyboard.StickerEntity
import java.io.File

class CircularGlideMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class RadialItem(
        val choice: DeliveryChoice,
        val label: String,
        val iconType: AppleIcon,
        val angleDeg: Float
    )

    private val items = listOf(
        RadialItem(DeliveryChoice.RICH_IMAGE, "IMAGE", AppleIcon.IMAGE_FRAME, -90f),
        RadialItem(DeliveryChoice.UNIVERSAL_SMS, "SMS LINK", AppleIcon.CHAT_SMS, 0f),
        RadialItem(DeliveryChoice.AUTO_ADAPT, "AUTO", AppleIcon.AUTO_SPARKLE, 90f),
        RadialItem(DeliveryChoice.TEXT_ART, "ART", AppleIcon.TEXT_ART, 180f)
    )

    var onItemSelectedListener: ((DeliveryChoice) -> Unit)? = null
    var onDismissListener: (() -> Unit)? = null

    private var originX = 0f
    private var originY = 0f
    private var activeIndex = -1
    private var currentStickerBitmap: Bitmap? = null
    private var openScale = 0f

    // Minimalist Luxury Apple Glass Paints
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e606080d") // 90% obsidian glass blur
    }
    private val orbitalRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1f2c40")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }
    private val nodeDefaultBg = Color.parseColor("#121824")
    private val nodeHoverBg = Color.parseColor("#003854")
    private val nodeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#223046")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
    }
    private val activeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00e5ff")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
    }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4d00e5ff")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.8f)
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7d92ad")
        textSize = dpToPx(10f)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11f)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.10f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val iconPainter = AppleIconView(context)

    fun show(cx: Float, cy: Float, sticker: StickerEntity?) {
        val w = if (width > 0) width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
        val h = if (height > 0) height.toFloat() else dpToPx(260f)

        val safePadTop = dpToPx(94f)
        val safePadBottom = dpToPx(96f)
        val safePadLeft = dpToPx(92f)
        val safePadRight = dpToPx(100f)

        this.originX = Math.max(safePadLeft, Math.min(w - safePadRight, cx))
        if (h <= safePadTop + safePadBottom) {
            this.originY = h / 2f
        } else {
            val targetY = cy - dpToPx(65f)
            this.originY = Math.max(safePadTop, Math.min(h - safePadBottom, targetY))
        }
        this.activeIndex = -1

        if (sticker != null) {
            val file = File(sticker.filePath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                currentStickerBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            }
        }

        visibility = VISIBLE
        alpha = 1f
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener {
                openScale = it.animatedValue as Float
                invalidate()
            }
        }
        anim.start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun dismiss() {
        val anim = ValueAnimator.ofFloat(openScale, 0f).apply {
            duration = 130
            addUpdateListener {
                openScale = it.animatedValue as Float
                invalidate()
            }
        }
        anim.start()
        postDelayed({
            visibility = GONE
            onDismissListener?.invoke()
            currentStickerBitmap?.recycle()
            currentStickerBitmap = null
        }, 140)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - originX
        val dy = event.y - originY
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val radThreshold = dpToPx(18f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (dist > radThreshold) {
                    val deg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val newIndex = when {
                        deg >= -135f && deg < -45f -> 0 // TOP (-90°): IMAGE
                        deg >= -45f && deg < 45f -> 1   // RIGHT (0°): SMS LINK
                        deg >= 45f && deg < 135f -> 2   // BOTTOM (90°): AUTO
                        else -> 3                       // LEFT (180°): ART
                    }
                    if (newIndex != activeIndex) {
                        activeIndex = newIndex
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                    }
                } else {
                    if (activeIndex != -1) {
                        activeIndex = -1
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activeIndex in 0 until items.size) {
                    val choice = items[activeIndex].choice
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onItemSelectedListener?.invoke(choice)
                }
                dismiss()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismiss()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || openScale <= 0f) return

        // 1. Obsidian Glass Blur Scrim
        scrimPaint.alpha = (openScale * 230).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(openScale, openScale)

        val orbitalRadius = dpToPx(52f)

        // 2. Orbital Hairline Ring
        canvas.drawCircle(0f, 0f, orbitalRadius, orbitalRingPaint)

        // 3. Dynamic Glow Beam to Active Petal
        if (activeIndex in 0 until items.size) {
            val item = items[activeIndex]
            val rad = Math.toRadians(item.angleDeg.toDouble())
            val px = (orbitalRadius * Math.cos(rad)).toFloat()
            val py = (orbitalRadius * Math.sin(rad)).toFloat()
            canvas.drawLine(0f, 0f, px, py, beamPaint)
        }

        // 4. Draw 4 Floating Minimalist Glass Nodes
        for (i in 0 until items.size) {
            val item = items[i]
            val isActive = (i == activeIndex)
            val rad = Math.toRadians(item.angleDeg.toDouble())
            val px = (orbitalRadius * Math.cos(rad)).toFloat()
            val py = (orbitalRadius * Math.sin(rad)).toFloat()

            val nodeRadius = if (isActive) dpToPx(20f) else dpToPx(16f)

            // Node Circle
            fillPaint.color = if (isActive) nodeHoverBg else nodeDefaultBg
            canvas.drawCircle(px, py, nodeRadius, fillPaint)

            if (isActive) {
                canvas.drawCircle(px, py, nodeRadius, activeGlowPaint)
            } else {
                canvas.drawCircle(px, py, nodeRadius, nodeBorderPaint)
            }

            // Apple Vector Icon
            canvas.save()
            val iconSize = (nodeRadius * 1.1f).toInt()
            val left = (px - iconSize / 2f).toInt()
            val top = (py - iconSize / 2f).toInt()
            canvas.translate(left.toFloat(), top.toFloat())
            iconPainter.iconType = item.iconType
            iconPainter.iconColor = if (isActive) Color.WHITE else Color.parseColor("#7d92ad")
            iconPainter.iconStrokeWidthDp = if (isActive) 1.8f else 1.5f
            iconPainter.layout(0, 0, iconSize, iconSize)
            iconPainter.draw(canvas)
            canvas.restore()

            // Label Below / Above
            val labelY = if (item.angleDeg == -90f) {
                py - nodeRadius - dpToPx(5f)
            } else {
                py + nodeRadius + dpToPx(11f)
            }
            val paintToUse = if (isActive) activeTextPaint else textPaint
            canvas.drawText(item.label, px, labelY, paintToUse)
        }

        // 5. Center Hub: Floating Disc with Sticker Thumbnail
        val centerHubRadius = dpToPx(18f)
        val bmp = currentStickerBitmap
        if (bmp != null) {
            val clipPath = Path().apply {
                addCircle(0f, 0f, centerHubRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            val bmpRect = Rect(0, 0, bmp.width, bmp.height)
            val destRect = RectF(-centerHubRadius, -centerHubRadius, centerHubRadius, centerHubRadius)
            canvas.drawBitmap(bmp, bmpRect, destRect, null)
            canvas.restore()
        } else {
            fillPaint.color = Color.parseColor("#0077b6")
            canvas.drawCircle(0f, 0f, centerHubRadius, fillPaint)
        }

        val hubBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00c2ff")
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(1.8f)
        }
        canvas.drawCircle(0f, 0f, centerHubRadius, hubBorderPaint)

        canvas.restore()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
