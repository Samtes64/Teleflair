package com.teleboard.teleboard.keyboard.physics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var alpha: Float,
    var maxLife: Float,
    var currentLife: Float,
    var color: Int
)

class ParticleEmitter {

    private val particles = mutableListOf<Particle>()
    private val random = Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Accelerometer-driven gravity vector (px/s^2)
    var gravityX: Float = 0f
    var gravityY: Float = 600f

    fun emitTapBurst(originX: Float, originY: Float, count: Int = 12, baseColor: Int = 0xFFA8C7FA.toInt()) {
        for (i in 0 until count) {
            val angle = random.nextFloat() * 2.0 * Math.PI
            val speed = 80f + random.nextFloat() * 220f
            val vx = (Math.cos(angle) * speed).toFloat()
            val vy = (Math.sin(angle) * speed).toFloat() - 80f // Upward impulse bias
            val radius = 2f + random.nextFloat() * 3.5f
            val life = 0.4f + random.nextFloat() * 0.4f

            particles.add(
                Particle(
                    x = originX,
                    y = originY,
                    vx = vx,
                    vy = vy,
                    radius = radius,
                    alpha = 1.0f,
                    maxLife = life,
                    currentLife = life,
                    color = baseColor
                )
            )
        }
    }

    fun update(dtSeconds: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.currentLife -= dtSeconds
            if (p.currentLife <= 0f) {
                iterator.remove()
                continue
            }

            p.vx += gravityX * dtSeconds
            p.vy += gravityY * dtSeconds

            // Air drag
            p.vx *= 0.96f
            p.vy *= 0.96f

            p.x += p.vx * dtSeconds
            p.y += p.vy * dtSeconds
            p.alpha = (p.currentLife / p.maxLife).coerceIn(0f, 1f)
        }
    }

    fun render(canvas: Canvas) {
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.alpha * 220).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    fun clear() {
        particles.clear()
    }

    fun hasActiveParticles(): Boolean = particles.isNotEmpty()

    fun getActiveParticleCount(): Int = particles.size
}
