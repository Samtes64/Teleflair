package com.teleboard.teleboard.keyboard.animoji

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import com.teleboard.teleboard.keyboard.SmartDeliveryCoordinator
import com.teleboard.teleboard.keyboard.StickerEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AnimojiRecorder(
    private val context: Context,
    private val deliveryCoordinator: SmartDeliveryCoordinator
) {

    interface RecordingCallback {
        fun onProgress(secondsLeft: Int)
        fun onFinished(sticker: StickerEntity)
        fun onError(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRecording = false
    private val recordedFrames = mutableListOf<Bitmap>()
    private var currentMetrics = FaceBlendshapeMetrics()

    fun updateMetrics(metrics: FaceBlendshapeMetrics) {
        currentMetrics = metrics
    }

    fun startRecording(callback: RecordingCallback) {
        if (isRecording) return
        isRecording = true
        recordedFrames.clear()

        var elapsedSeconds = 0
        val totalSeconds = 3
        val frameIntervalMs = 150L // ~7 frames per second

        val frameRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return

                val frame = renderPuppetFrame(currentMetrics)
                recordedFrames.add(frame)

                mainHandler.postDelayed(this, frameIntervalMs)
            }
        }

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                elapsedSeconds++
                callback.onProgress(totalSeconds - elapsedSeconds)

                if (elapsedSeconds >= totalSeconds) {
                    isRecording = false
                    mainHandler.removeCallbacks(frameRunnable)
                    finishAndDeliver(callback)
                } else {
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }

        mainHandler.post(frameRunnable)
        mainHandler.postDelayed(countdownRunnable, 1000)
    }

    private fun renderPuppetFrame(metrics: FaceBlendshapeMetrics): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw Minimalist Neon/Slate Avatar Puppet
        // Head
        paint.color = Color.parseColor("#1c2436")
        canvas.drawRoundRect(RectF(32f, 32f, 224f, 224f), 32f, 32f, paint)

        // Head border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#a8c7fa")
        canvas.drawRoundRect(RectF(32f, 32f, 224f, 224f), 32f, 32f, paint)

        // Eyes (reacting to blink)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#ffffff")
        val eyeRadius = 14f * (1f - metrics.leftEyeBlink).coerceAtLeast(0.2f)
        canvas.drawCircle(80f, 96f, eyeRadius, paint)
        canvas.drawCircle(176f, 96f, eyeRadius, paint)

        // Mouth (reacting to mouthOpen)
        paint.color = Color.parseColor("#d2e3fc")
        val mouthHeight = (10f + metrics.mouthOpen * 45f).coerceIn(8f, 55f)
        val mouthTop = 160f - (mouthHeight / 2f)
        val mouthBottom = 160f + (mouthHeight / 2f)
        canvas.drawRoundRect(RectF(88f, mouthTop, 168f, mouthBottom), 16f, 16f, paint)

        return bitmap
    }

    private fun finishAndDeliver(callback: RecordingCallback) {
        if (recordedFrames.isEmpty()) {
            callback.onError("No frames recorded")
            return
        }

        val cacheDir = File(context.cacheDir, "stickers").apply { if (!exists()) mkdirs() }
        val animojiFile = File(cacheDir, "animoji_${System.currentTimeMillis()}.webp")

        // Save representative WebP sticker frame
        try {
            val primaryFrame = recordedFrames[recordedFrames.size / 2]
            FileOutputStream(animojiFile).use { out ->
                primaryFrame.compress(Bitmap.CompressFormat.WEBP, 95, out)
            }

            val stickerEntity = StickerEntity(
                id = "animoji_${UUID.randomUUID()}",
                packId = "custom_face_stickers",
                fileId = animojiFile.name,
                emoji = "face",
                filePath = animojiFile.absolutePath,
                width = 256,
                height = 256,
                fileSize = animojiFile.length().toInt(),
                mimeType = "image/webp",
                isAnimated = true,
                contentHash = animojiFile.name,
                addedAt = System.currentTimeMillis().toString()
            )

            callback.onFinished(stickerEntity)
        } catch (e: Exception) {
            callback.onError(e.message ?: "Failed to encode animoji sticker")
        } finally {
            for (f in recordedFrames) {
                if (!f.isRecycled) f.recycle()
            }
            recordedFrames.clear()
        }
    }
}
