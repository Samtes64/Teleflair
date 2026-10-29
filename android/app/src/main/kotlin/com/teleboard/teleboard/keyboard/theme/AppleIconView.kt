package com.teleboard.teleboard.keyboard.theme

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

enum class AppleIcon {
    GLOBE,
    STICKER_FACE,
    HEART,
    CLOCK,
    PAPER_PLANE_PLUS,
    DOWNLOAD_TRAY,
    KEYBOARD,
    IMAGE_FRAME,
    CHAT_SMS,
    AUTO_SPARKLE,
    CLIPBOARD,
    PLUS,
    TEXT_ART,
    MEME_STIPPLE,
    TELEGRAM_PLANE,
    BACK_ARROW,
    ASCII_RAMP
}

class AppleIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var iconType: AppleIcon = AppleIcon.STICKER_FACE
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            strokePaint.color = value
            fillPaint.color = value
            invalidate()
        }

    var iconStrokeWidthDp: Float = 1.7f
        set(value) {
            field = value
            strokePaint.strokeWidth = dpToPx(value)
            invalidate()
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        strokeWidth = dpToPx(iconStrokeWidthDp)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val r = Math.min(w, h) * 0.40f

        strokePaint.color = iconColor
        fillPaint.color = iconColor
        strokePaint.strokeWidth = dpToPx(iconStrokeWidthDp)
        path.reset()

        when (iconType) {
            AppleIcon.GLOBE -> drawGlobe(canvas, cx, cy, r)
            AppleIcon.STICKER_FACE -> drawStickerFace(canvas, cx, cy, r)
            AppleIcon.HEART -> drawHeart(canvas, cx, cy, r)
            AppleIcon.CLOCK -> drawClock(canvas, cx, cy, r)
            AppleIcon.PAPER_PLANE_PLUS -> drawPaperPlanePlus(canvas, cx, cy, r)
            AppleIcon.DOWNLOAD_TRAY -> drawDownloadTray(canvas, cx, cy, r)
            AppleIcon.KEYBOARD -> drawKeyboard(canvas, cx, cy, r)
            AppleIcon.IMAGE_FRAME -> drawImageFrame(canvas, cx, cy, r)
            AppleIcon.CHAT_SMS -> drawChatSms(canvas, cx, cy, r)
            AppleIcon.AUTO_SPARKLE -> drawAutoSparkle(canvas, cx, cy, r)
            AppleIcon.CLIPBOARD -> drawClipboard(canvas, cx, cy, r)
            AppleIcon.PLUS -> drawPlus(canvas, cx, cy, r)
            AppleIcon.TEXT_ART -> drawTextArt(canvas, cx, cy, r)
            AppleIcon.MEME_STIPPLE -> drawMemeStipple(canvas, cx, cy, r)
            AppleIcon.TELEGRAM_PLANE -> drawTelegramPlane(canvas, cx, cy, r)
            AppleIcon.BACK_ARROW -> drawBackArrow(canvas, cx, cy, r)
            AppleIcon.ASCII_RAMP -> drawAsciiRamp(canvas, cx, cy, r)
        }
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Outer globe circle
        canvas.drawCircle(cx, cy, r, strokePaint)
        // Equator
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
        // Meridian ellipse
        val ovalRect = RectF(cx - r * 0.48f, cy - r, cx + r * 0.48f, cy + r)
        canvas.drawOval(ovalRect, strokePaint)
        // Center meridian vertical line
        canvas.drawLine(cx, cy - r, cx, cy + r, strokePaint)
    }

    private fun drawStickerFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Sticker head circle
        canvas.drawCircle(cx, cy, r, strokePaint)
        // Left eye
        canvas.drawCircle(cx - r * 0.36f, cy - r * 0.14f, r * 0.11f, fillPaint)
        // Right eye
        canvas.drawCircle(cx + r * 0.36f, cy - r * 0.14f, r * 0.11f, fillPaint)
        // Cute smiling mouth arc
        val mouthRect = RectF(cx - r * 0.45f, cy - r * 0.18f, cx + r * 0.45f, cy + r * 0.50f)
        canvas.drawArc(mouthRect, 25f, 130f, false, strokePaint)
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.moveTo(cx, cy + r * 0.75f)
        path.cubicTo(
            cx - r * 0.70f, cy + r * 0.25f,
            cx - r * 0.95f, cy - r * 0.20f,
            cx - r * 0.50f, cy - r * 0.65f
        )
        path.cubicTo(
            cx - r * 0.20f, cy - r * 0.85f,
            cx, cy - r * 0.35f,
            cx, cy - r * 0.30f
        )
        path.cubicTo(
            cx, cy - r * 0.35f,
            cx + r * 0.20f, cy - r * 0.85f,
            cx + r * 0.50f, cy - r * 0.65f
        )
        path.cubicTo(
            cx + r * 0.95f, cy - r * 0.20f,
            cx + r * 0.70f, cy + r * 0.25f,
            cx, cy + r * 0.75f
        )
        path.close()
        canvas.drawPath(path, strokePaint)
    }

    private fun drawClock(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, strokePaint)
        // Hour hand at ~10:00
        canvas.drawLine(cx, cy, cx - r * 0.38f, cy - r * 0.38f, strokePaint)
        // Minute hand at ~2:00
        canvas.drawLine(cx, cy, cx + r * 0.52f, cy - r * 0.22f, strokePaint)
        // Center hub dot
        canvas.drawCircle(cx, cy, r * 0.10f, fillPaint)
    }

    private fun drawPaperPlanePlus(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Main paper plane body
        path.moveTo(cx + r * 0.60f, cy - r * 0.60f) // tip
        path.lineTo(cx - r * 0.70f, cy + r * 0.15f) // bottom left wing
        path.lineTo(cx - r * 0.15f, cy + r * 0.10f) // inner crease
        path.lineTo(cx + r * 0.05f, cy + r * 0.70f) // bottom right fold
        path.close()

        // Center crease fold line
        path.moveTo(cx + r * 0.60f, cy - r * 0.60f)
        path.lineTo(cx - r * 0.15f, cy + r * 0.10f)

        canvas.drawPath(path, strokePaint)

        // Crisp Plus badge in top right corner
        val bx = cx + r * 0.65f
        val by = cy - r * 0.55f
        val br = r * 0.30f
        fillPaint.color = if (iconColor == Color.WHITE) Color.WHITE else iconColor
        canvas.drawCircle(bx, by, br, fillPaint)

        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (iconColor == Color.WHITE) Color.parseColor("#0077b6") else Color.parseColor("#0c111a")
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(1.3f)
            strokeCap = Paint.Cap.ROUND
        }
        val len = br * 0.55f
        canvas.drawLine(bx - len, by, bx + len, by, plusPaint)
        canvas.drawLine(bx, by - len, bx, by + len, plusPaint)
    }

    private fun drawDownloadTray(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Downward Arrow stem
        canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.20f, strokePaint)
        // Arrowhead
        path.moveTo(cx - r * 0.35f, cy - r * 0.10f)
        path.lineTo(cx, cy + r * 0.20f)
        path.lineTo(cx + r * 0.35f, cy - r * 0.10f)
        canvas.drawPath(path, strokePaint)

        // Open U-tray bracket at bottom
        path.reset()
        path.moveTo(cx - r * 0.65f, cy + r * 0.10f)
        path.lineTo(cx - r * 0.65f, cy + r * 0.58f)
        path.lineTo(cx + r * 0.65f, cy + r * 0.58f)
        path.lineTo(cx + r * 0.65f, cy + r * 0.10f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawKeyboard(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rect = RectF(cx - r * 0.85f, cy - r * 0.55f, cx + r * 0.85f, cy + r * 0.55f)
        canvas.drawRoundRect(rect, dpToPx(3f), dpToPx(3f), strokePaint)
        // Spacebar
        canvas.drawLine(cx - r * 0.40f, cy + r * 0.30f, cx + r * 0.40f, cy + r * 0.30f, strokePaint)
        // Top row key dots
        val dotR = r * 0.07f
        canvas.drawCircle(cx - r * 0.50f, cy - r * 0.22f, dotR, fillPaint)
        canvas.drawCircle(cx - r * 0.16f, cy - r * 0.22f, dotR, fillPaint)
        canvas.drawCircle(cx + r * 0.16f, cy - r * 0.22f, dotR, fillPaint)
        canvas.drawCircle(cx + r * 0.50f, cy - r * 0.22f, dotR, fillPaint)
        // Mid row key dots
        canvas.drawCircle(cx - r * 0.33f, cy + r * 0.05f, dotR, fillPaint)
        canvas.drawCircle(cx, cy + r * 0.05f, dotR, fillPaint)
        canvas.drawCircle(cx + r * 0.33f, cy + r * 0.05f, dotR, fillPaint)
    }

    private fun drawImageFrame(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rect = RectF(cx - r * 0.75f, cy - r * 0.65f, cx + r * 0.75f, cy + r * 0.65f)
        canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), strokePaint)
        // Sun dot
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.14f, fillPaint)
        // Mountain peak
        path.moveTo(cx - r * 0.60f, cy + r * 0.45f)
        path.lineTo(cx - r * 0.10f, cy - r * 0.05f)
        path.lineTo(cx + r * 0.25f, cy + r * 0.25f)
        path.lineTo(cx + r * 0.55f, cy + r * 0.05f)
        path.lineTo(cx + r * 0.65f, cy + r * 0.45f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawChatSms(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bubble = RectF(cx - r * 0.75f, cy - r * 0.65f, cx + r * 0.75f, cy + r * 0.35f)
        canvas.drawRoundRect(bubble, dpToPx(6f), dpToPx(6f), strokePaint)
        // Tail
        path.moveTo(cx - r * 0.35f, cy + r * 0.35f)
        path.lineTo(cx - r * 0.55f, cy + r * 0.70f)
        path.lineTo(cx - r * 0.10f, cy + r * 0.35f)
        canvas.drawPath(path, strokePaint)
        // Lines
        canvas.drawLine(cx - r * 0.45f, cy - r * 0.20f, cx + r * 0.45f, cy - r * 0.20f, strokePaint)
        canvas.drawLine(cx - r * 0.45f, cy + r * 0.05f, cx + r * 0.20f, cy + r * 0.05f, strokePaint)
    }

    private fun drawAutoSparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.moveTo(cx, cy - r * 0.80f)
        path.quadTo(cx, cy, cx + r * 0.80f, cy)
        path.quadTo(cx, cy, cx, cy + r * 0.80f)
        path.quadTo(cx, cy, cx - r * 0.80f, cy)
        path.quadTo(cx, cy, cx, cy - r * 0.80f)
        path.close()
        canvas.drawPath(path, strokePaint)
    }

    private fun drawClipboard(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val board = RectF(cx - r * 0.65f, cy - r * 0.60f, cx + r * 0.65f, cy + r * 0.75f)
        canvas.drawRoundRect(board, dpToPx(3f), dpToPx(3f), strokePaint)
        // Top clip
        val clip = RectF(cx - r * 0.30f, cy - r * 0.80f, cx + r * 0.30f, cy - r * 0.48f)
        canvas.drawRoundRect(clip, dpToPx(2f), dpToPx(2f), strokePaint)
        // Lines
        canvas.drawLine(cx - r * 0.38f, cy - r * 0.10f, cx + r * 0.38f, cy - r * 0.10f, strokePaint)
        canvas.drawLine(cx - r * 0.38f, cy + r * 0.18f, cx + r * 0.38f, cy + r * 0.18f, strokePaint)
        canvas.drawLine(cx - r * 0.38f, cy + r * 0.45f, cx + r * 0.15f, cy + r * 0.45f, strokePaint)
    }

    private fun drawPlus(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val len = r * 0.70f
        canvas.drawLine(cx - len, cy, cx + len, cy, strokePaint)
        canvas.drawLine(cx, cy - len, cx, cy + len, strokePaint)
    }

    private fun drawTextArt(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rect = RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f)
        canvas.drawRoundRect(rect, dpToPx(3f), dpToPx(3f), strokePaint)
        val d = r * 0.35f
        val dotR = r * 0.13f
        canvas.drawCircle(cx - d, cy - d, dotR, fillPaint)
        canvas.drawCircle(cx + d, cy - d, dotR, fillPaint)
        canvas.drawCircle(cx - d, cy + d, dotR, fillPaint)
        canvas.drawCircle(cx + d, cy + d, dotR, fillPaint)
    }

    private fun drawMemeStipple(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Center sparkle star
        path.moveTo(cx, cy - r * 0.70f)
        path.quadTo(cx, cy, cx + r * 0.70f, cy)
        path.quadTo(cx, cy, cx, cy + r * 0.70f)
        path.quadTo(cx, cy, cx - r * 0.70f, cy)
        path.quadTo(cx, cy, cx, cy - r * 0.70f)
        path.close()
        canvas.drawPath(path, strokePaint)
        // 4 corner stipple dots
        val d = r * 0.52f
        val dotR = r * 0.10f
        canvas.drawCircle(cx - d, cy - d, dotR, fillPaint)
        canvas.drawCircle(cx + d, cy - d, dotR, fillPaint)
        canvas.drawCircle(cx - d, cy + d, dotR, fillPaint)
        canvas.drawCircle(cx + d, cy + d, dotR, fillPaint)
    }

    private fun drawTelegramPlane(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.moveTo(cx + r * 0.70f, cy - r * 0.70f) // tip
        path.lineTo(cx - r * 0.75f, cy + r * 0.15f) // bottom left
        path.lineTo(cx - r * 0.15f, cy + r * 0.15f) // crease
        path.lineTo(cx + r * 0.10f, cy + r * 0.75f) // bottom right
        path.close()
        // Center crease
        path.moveTo(cx + r * 0.70f, cy - r * 0.70f)
        path.lineTo(cx - r * 0.15f, cy + r * 0.15f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawBackArrow(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Left arrow stem
        canvas.drawLine(cx + r * 0.50f, cy, cx - r * 0.50f, cy, strokePaint)
        // Left chevron head
        path.moveTo(cx - r * 0.15f, cy - r * 0.40f)
        path.lineTo(cx - r * 0.50f, cy)
        path.lineTo(cx - r * 0.15f, cy + r * 0.40f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawAsciiRamp(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Hash # symbol
        val d = r * 0.28f
        val len = r * 0.65f
        canvas.drawLine(cx - len, cy - d, cx + len, cy - d, strokePaint)
        canvas.drawLine(cx - len, cy + d, cx + len, cy + d, strokePaint)
        canvas.drawLine(cx - d + r * 0.08f, cy - len, cx - d - r * 0.08f, cy + len, strokePaint)
        canvas.drawLine(cx + d + r * 0.08f, cy - len, cx + d - r * 0.08f, cy + len, strokePaint)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
