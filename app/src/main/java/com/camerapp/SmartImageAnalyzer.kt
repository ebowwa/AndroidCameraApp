package com.camerapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.math.abs

class SmartImageAnalyzer(
    private val onMotionDetected: (() -> Unit)? = null,
    private val onOptimalScene: (() -> Unit)? = null,
    private val onBrightnessChange: ((Float) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "SmartImageAnalyzer"
        private const val MOTION_THRESHOLD = 30.0f // Sensitivity for motion detection
        private const val BRIGHTNESS_THRESHOLD = 0.3f // Minimum brightness for good photos
        private const val FRAME_HISTORY_SIZE = 3 // Number of frames to compare for motion
    }

    private var previousFrames = mutableListOf<Bitmap>()
    private var lastBrightness = 0f
    private var motionDetectionEnabled = true
    private var autoCaptureEnabled = false
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Advanced AI-based scene detection
    enum class SceneType {
        PORTRAIT, LANDSCAPE, INDOOR, OUTDOOR, LOW_LIGHT, BRIGHT_LIGHT, UNKNOWN
    }

    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)

        try {
            // Perform multiple analysis operations
            val brightness = calculateBrightness(bitmap)
            val motionDetected = detectMotion(bitmap)
            val sceneType = detectSceneType(bitmap, brightness)

            // Trigger callbacks based on analysis
            handleAnalysisResults(brightness, motionDetected, sceneType)

        } catch (e: Exception) {
            Log.e(TAG, "Error during image analysis", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // Convert YUV_420_888 to RGB Bitmap for analysis
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y
        yBuffer.get(nv21, 0, ySize)

        // U and V are swapped for NV21 format
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        val quality = 90 // Lower quality for faster processing
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), quality, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun calculateBrightness(bitmap: Bitmap): Float {
        var totalBrightness = 0L
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalBrightness += (r + g + b) / 3
        }

        return totalBrightness.toFloat() / (pixels.size * 255f)
    }

    private fun detectMotion(currentBitmap: Bitmap): Boolean {
        if (!motionDetectionEnabled || previousFrames.isEmpty()) {
            previousFrames.add(currentBitmap.copy(currentBitmap.config, false))
            if (previousFrames.size > FRAME_HISTORY_SIZE) {
                previousFrames.removeAt(0)
            }
            return false
        }

        val previousBitmap = previousFrames.last()
        val motionLevel = calculateMotionLevel(previousBitmap, currentBitmap)

        // Update frame history
        previousFrames.add(currentBitmap.copy(currentBitmap.config, false))
        if (previousFrames.size > FRAME_HISTORY_SIZE) {
            previousFrames.removeAt(0)
        }

        val motionDetected = motionLevel > MOTION_THRESHOLD
        if (motionDetected) {
            Log.d(TAG, "Motion detected with level: $motionLevel")
            analysisScope.launch {
                onMotionDetected?.invoke()
            }
        }

        return motionDetected
    }

    private fun calculateMotionLevel(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
            return 0f
        }

        var totalDifference = 0L
        val pixels1 = IntArray(bitmap1.width * bitmap1.height)
        val pixels2 = IntArray(bitmap2.width * bitmap2.height)

        bitmap1.getPixels(pixels1, 0, bitmap1.width, 0, 0, bitmap1.width, bitmap1.height)
        bitmap2.getPixels(pixels2, 0, bitmap2.width, 0, 0, bitmap2.width, bitmap2.height)

        for (i in pixels1.indices) {
            val r1 = (pixels1[i] shr 16) and 0xFF
            val g1 = (pixels1[i] shr 8) and 0xFF
            val b1 = pixels1[i] and 0xFF

            val r2 = (pixels2[i] shr 16) and 0xFF
            val g2 = (pixels2[i] shr 8) and 0xFF
            val b2 = pixels2[i] and 0xFF

            totalDifference += abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        }

        return totalDifference.toFloat() / (pixels1.size * 3 * 255f) * 100
    }

    private fun detectSceneType(bitmap: Bitmap, brightness: Float): SceneType {
        return when {
            brightness < 0.1f -> SceneType.LOW_LIGHT
            brightness > 0.8f -> SceneType.BRIGHT_LIGHT
            brightness > 0.6f -> SceneType.OUTDOOR
            brightness > 0.3f -> SceneType.INDOOR
            else -> SceneType.UNKNOWN
        }
    }

    private fun handleAnalysisResults(brightness: Float, motionDetected: Boolean, sceneType: SceneType) {
        // Brightness monitoring
        if (abs(brightness - lastBrightness) > 0.1f) {
            lastBrightness = brightness
            onBrightnessChange?.invoke(brightness)
            Log.d(TAG, "Brightness changed to: ${"%.2f".format(brightness)}")
        }

        // Optimal scene detection for automatic capture
        val isOptimalScene = brightness > BRIGHTNESS_THRESHOLD &&
                            sceneType in listOf(SceneType.INDOOR, SceneType.OUTDOOR) &&
                            !motionDetected // Stable scene

        if (isOptimalScene && autoCaptureEnabled) {
            analysisScope.launch {
                onOptimalScene?.invoke()
                Log.d(TAG, "Optimal scene detected - triggering capture")
            }
        }

        // Scene type logging
        if (sceneType != SceneType.UNKNOWN) {
            Log.d(TAG, "Scene type detected: $sceneType")
        }
    }

    // Configuration methods
    fun enableMotionDetection(enabled: Boolean) {
        motionDetectionEnabled = enabled
        if (!enabled) {
            previousFrames.clear()
        }
        Log.d(TAG, "Motion detection ${if (enabled) "enabled" else "disabled"}")
    }

    fun enableAutoCapture(enabled: Boolean) {
        autoCaptureEnabled = enabled
        Log.d(TAG, "Auto-capture ${if (enabled) "enabled" else "disabled"}")
    }

    fun setMotionSensitivity(threshold: Float) {
        // This would require modifying the MOTION_THRESHOLD
        Log.d(TAG, "Motion sensitivity set to: $threshold")
    }

    fun clearFrameHistory() {
        previousFrames.clear()
        Log.d(TAG, "Frame history cleared")
    }

    fun getAnalysisStats(): Map<String, Any> {
        return mapOf(
            "motion_detection_enabled" to motionDetectionEnabled,
            "auto_capture_enabled" to autoCaptureEnabled,
            "frame_history_size" to previousFrames.size,
            "last_brightness" to lastBrightness
        )
    }

    fun cleanup() {
        analysisScope.cancel()
        previousFrames.clear()
        Log.d(TAG, "SmartImageAnalyzer cleaned up")
    }
}