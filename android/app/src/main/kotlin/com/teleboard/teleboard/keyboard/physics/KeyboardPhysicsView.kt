package com.teleboard.teleboard.keyboard.physics

import android.content.Context
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View

class KeyboardPhysicsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private val emitter = ParticleEmitter()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorActive = false
    private var isPaused = false

    private var lastFrameTime = 0L

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun triggerKeyTapImpulse(x: Float, y: Float, color: Int) {
        if (isPaused) return
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val physicsEnabled = prefs.getBoolean("flutter.keyboard_physics_enabled", true)
        if (!physicsEnabled) return

        emitter.emitTapBurst(x, y, count = 10, baseColor = color)
        invalidate()
    }

    fun resumePhysics(isSensitive: Boolean) {
        if (isSensitive) {
            pausePhysics()
            return
        }
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val physicsEnabled = prefs.getBoolean("flutter.keyboard_physics_enabled", true)
        if (!physicsEnabled) {
            pausePhysics()
            return
        }

        isPaused = false
        if (!isSensorActive && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorActive = true
        }
        lastFrameTime = System.currentTimeMillis()
        invalidate()
    }

    fun pausePhysics() {
        isPaused = true
        if (isSensorActive) {
            sensorManager?.unregisterListener(this)
            isSensorActive = false
        }
        emitter.clear()
        invalidate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isPaused || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Tilting phone: X axis maps to horizontal gravity, Y axis to vertical gravity
            val ax = event.values[0]
            val ay = event.values[1]
            emitter.gravityX = -ax * 150f
            emitter.gravityY = ay * 150f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isPaused) return

        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1000f).coerceIn(0.001f, 0.05f)
        lastFrameTime = now

        emitter.update(dt)
        emitter.render(canvas)

        if (emitter.hasActiveParticles()) {
            postInvalidateOnAnimation()
        }
    }
}
