package com.teleboard.teleboard.keyboard.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class GestureTrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trailPoints = mutableListOf<GesturePoint>()
    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#a8c7fa")
        strokeWidth = dpToPx(3.5f)
    }

    fun addPoint(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        trailPoints.add(GesturePoint(x, y, now))
        // Keep trail limited to last 350ms
        pruneOldPoints(now)
        invalidate()
    }

    fun clearTrail() {
        trailPoints.clear()
        invalidate()
    }

    private fun pruneOldPoints(now: Long) {
        val cutoff = now - 350L
        while (trailPoints.isNotEmpty() && trailPoints.first().time < cutoff) {
            trailPoints.removeAt(0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        pruneOldPoints(now)

        if (trailPoints.size < 2) return

        trailPath.reset()
        trailPath.moveTo(trailPoints.first().x, trailPoints.first().y)

        for (i in 1 until trailPoints.size) {
            val p0 = trailPoints[i - 1]
            val p1 = trailPoints[i]
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            trailPath.quadTo(p0.x, p0.y, midX, midY)
        }

        canvas.drawPath(trailPath, trailPaint)

        // If trail points still exist, schedule next frame for smooth fading
        if (trailPoints.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
