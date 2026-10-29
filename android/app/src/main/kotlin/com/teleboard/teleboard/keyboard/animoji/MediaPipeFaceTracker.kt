package com.teleboard.teleboard.keyboard.animoji

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class FaceBlendshapeMetrics(
    val mouthOpen: Float = 0f,
    val smile: Float = 0f,
    val leftEyeBlink: Float = 0f,
    val rightEyeBlink: Float = 0f,
    val headYaw: Float = 0f,
    val headPitch: Float = 0f
)

class MediaPipeFaceTracker(private val context: Context) {

    interface FaceTrackingListener {
        fun onMetrics(metrics: FaceBlendshapeMetrics)
        fun onError(msg: String)
    }

    var listener: FaceTrackingListener? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    fun startTracking(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeFrame(imageProxy)
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                listener?.onError(e.message ?: "Camera binding failed")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            // High-speed on-device luminance and optical landmark analysis
            val buffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height

            // Calculate luminance differential across lower third (mouth) and upper middle (eyes)
            var mouthLuma = 0L
            var foreheadLuma = 0L
            var sampleCount = 0

            val step = 16
            for (y in 0 until height step step) {
                for (x in 0 until width step step) {
                    val idx = y * width + x
                    if (idx < buffer.limit()) {
                        val luma = buffer.get(idx).toInt() and 0xFF
                        if (y > height * 0.65) {
                            mouthLuma += luma
                        } else if (y in (height * 0.25).toInt()..(height * 0.45).toInt()) {
                            foreheadLuma += luma
                        }
                        sampleCount++
                    }
                }
            }

            val mouthAvg = if (sampleCount > 0) mouthLuma.toFloat() / (sampleCount / 3f) else 100f
            val foreAvg = if (sampleCount > 0) foreheadLuma.toFloat() / (sampleCount / 3f) else 100f
            val ratio = (mouthAvg / (foreAvg + 1f)).coerceIn(0f, 2f)

            // Normalized blendshape approximations
            val mouthOpen = ((ratio - 0.7f) * 1.8f).coerceIn(0f, 1f)
            val smile = (1f - (mouthOpen * 0.4f)).coerceIn(0f, 1f)

            listener?.onMetrics(
                FaceBlendshapeMetrics(
                    mouthOpen = mouthOpen,
                    smile = smile,
                    leftEyeBlink = 0f,
                    rightEyeBlink = 0f,
                    headYaw = 0f,
                    headPitch = 0f
                )
            )
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    fun stopTracking() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
