package com.teleboard.teleboard.keyboard.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.teleboard.teleboard.keyboard.theme.KeyboardPalette

class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface SuggestionListener {
        fun onSuggestionSelected(word: String)
    }

    var listener: SuggestionListener? = null
    private var suggestions: List<String> = emptyList()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(1f)
    }

    private var palette: KeyboardPalette? = null
    private val candidateRects = mutableListOf<RectF>()

    fun setPalette(newPalette: KeyboardPalette) {
        palette = newPalette
        bgPaint.color = newPalette.suggestionBg
        dividerPaint.color = Color.argb(40, 255, 255, 255)
        invalidate()
    }

    fun setSuggestions(items: List<String>) {
        suggestions = items
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = dpToPx(30f).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        candidateRects.clear()
        if (suggestions.isEmpty()) return

        val count = suggestions.size
        val cellWidth = width.toFloat() / count
        val p = palette

        for (i in 0 until count) {
            val left = i * cellWidth
            val right = left + cellWidth
            val rect = RectF(left, 0f, right, height.toFloat())
            candidateRects.add(rect)

            // Divider line between candidates
            if (i > 0) {
                canvas.drawLine(left, dpToPx(6f), left, height - dpToPx(6f), dividerPaint)
            }

            val word = suggestions[i]
            val isCenter = (count == 3 && i == 1) || (count == 1 && i == 0)

            textPaint.color = if (isCenter) {
                p?.suggestionHighlight ?: Color.parseColor("#a8c7fa")
            } else {
                p?.suggestionText ?: Color.parseColor("#c4d1eb")
            }
            textPaint.typeface = if (isCenter) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            textPaint.textSize = spToPx(if (isCenter) 14.5f else 13.5f)

            val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(word, left + (cellWidth / 2f), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            for (i in candidateRects.indices) {
                if (candidateRects[i].contains(x, y) && i < suggestions.size) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onSuggestionSelected(suggestions[i])
                    return true
                }
            }
        }
        return true
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
