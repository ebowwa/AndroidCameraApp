package com.camerapp.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.camera.core.ImageCapture

class AppPreferences(context: Context) {
    companion object {
        private const val TAG = "AppPreferences"
        private const val PREFS_NAME = "camera_settings"

        // Preference keys
        private const val KEY_CAMERA_ORIENTATION = "camera_orientation"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_AUTO_START_TRANSLATION = "auto_start_translation"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_CAPTURE_MODE = "capture_mode"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_TARGET_RESOLUTION_WIDTH = "target_resolution_width"
        private const val KEY_TARGET_RESOLUTION_HEIGHT = "target_resolution_height"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Camera orientation settings
    fun setCameraOrientation(orientation: CameraOrientation) {
        prefs.edit().putInt(KEY_CAMERA_ORIENTATION, orientation.ordinal).apply()
        Log.d(TAG, "Camera orientation set to: $orientation")
    }

    fun getCameraOrientation(): CameraOrientation {
        val index = prefs.getInt(KEY_CAMERA_ORIENTATION, CameraOrientation.PORTRAIT.ordinal)
        return CameraOrientation.values()[index]
    }

    // Image quality settings
    fun setImageQuality(quality: ImageQuality) {
        prefs.edit().putInt(KEY_IMAGE_QUALITY, quality.ordinal).apply()
        Log.d(TAG, "Image quality set to: $quality")
    }

    fun getImageQuality(): ImageQuality {
        val index = prefs.getInt(KEY_IMAGE_QUALITY, ImageQuality.MEDIUM.ordinal)
        return ImageQuality.values()[index]
    }

    // Speech recognition settings
    fun setAutoStartTranslation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_TRANSLATION, enabled).apply()
        Log.d(TAG, "Auto-start translation set to: $enabled")
    }

    fun shouldAutoStartTranslation(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START_TRANSLATION, false)
    }

    fun setConfidenceThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_CONFIDENCE_THRESHOLD, threshold).apply()
        Log.d(TAG, "Confidence threshold set to: $threshold%")
    }

    fun getConfidenceThreshold(): Int {
        return prefs.getInt(KEY_CONFIDENCE_THRESHOLD, 70)
    }

    // Advanced camera settings
    fun setCaptureMode(captureMode: Int) {
        prefs.edit().putInt(KEY_CAPTURE_MODE, captureMode).apply()
        Log.d(TAG, "Capture mode set to: $captureMode")
    }

    fun getCaptureMode(): Int {
        return prefs.getInt(KEY_CAPTURE_MODE, ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    }

    fun setJpegQuality(quality: Int) {
        prefs.edit().putInt(KEY_JPEG_QUALITY, quality).apply()
        Log.d(TAG, "JPEG quality set to: $quality%")
    }

    fun getJpegQuality(): Int {
        return prefs.getInt(KEY_JPEG_QUALITY, 95)
    }

    fun setTargetResolution(width: Int, height: Int) {
        prefs.edit()
            .putInt(KEY_TARGET_RESOLUTION_WIDTH, width)
            .putInt(KEY_TARGET_RESOLUTION_HEIGHT, height)
            .apply()
        Log.d(TAG, "Target resolution set to: ${width}x$height")
    }

    fun getTargetResolution(): android.util.Size? {
        val width = prefs.getInt(KEY_TARGET_RESOLUTION_WIDTH, 0)
        val height = prefs.getInt(KEY_TARGET_RESOLUTION_HEIGHT, 0)
        return if (width > 0 && height > 0) {
            android.util.Size(width, height)
        } else {
            null
        }
    }

    // Convenience methods for getting camera configuration
    fun getCameraConfiguration(): CameraConfiguration {
        val imageQuality = getImageQuality()
        return CameraConfiguration(
            captureMode = getCaptureMode(),
            targetResolution = when (imageQuality) {
                ImageQuality.HIGH -> android.util.Size(1920, 1080)
                ImageQuality.MEDIUM -> android.util.Size(1280, 720)
                ImageQuality.LOW -> android.util.Size(640, 480)
            },
            jpegQuality = getJpegQuality()
        )
    }

    fun applyCameraConfiguration(config: CameraConfiguration) {
        setCaptureMode(config.captureMode)
        setJpegQuality(config.jpegQuality)
        config.targetResolution?.let {
            setTargetResolution(it.width, it.height)
        }
        Log.d(TAG, "Camera configuration applied: $config")
    }

    // Speech recognition configuration
    fun getSpeechConfiguration(): SpeechConfiguration {
        return SpeechConfiguration(
            confidenceThreshold = getConfidenceThreshold(),
            autoStart = shouldAutoStartTranslation()
        )
    }

    // Settings management
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Settings reset to defaults")
    }

    fun exportSettings(): Map<String, Any?> {
        return mapOf(
            "cameraOrientation" to getCameraOrientation(),
            "imageQuality" to getImageQuality(),
            "autoStartTranslation" to shouldAutoStartTranslation(),
            "confidenceThreshold" to getConfidenceThreshold(),
            "captureMode" to getCaptureMode(),
            "jpegQuality" to getJpegQuality(),
            "targetResolution" to (getTargetResolution()?.let { "${it.width}x${it.height}" } ?: null)
        )
    }

    fun importSettings(settings: Map<String, Any?>) {
        settings.forEach { (key, value) ->
            when (key) {
                KEY_CAMERA_ORIENTATION -> {
                    if (value is Int) {
                        setCameraOrientation(CameraOrientation.values()[value])
                    }
                }
                KEY_IMAGE_QUALITY -> {
                    if (value is Int) {
                        setImageQuality(ImageQuality.values()[value])
                    }
                }
                KEY_AUTO_START_TRANSLATION -> {
                    if (value is Boolean) {
                        setAutoStartTranslation(value)
                    }
                }
                KEY_CONFIDENCE_THRESHOLD -> {
                    if (value is Int) {
                        setConfidenceThreshold(value)
                    }
                }
                KEY_CAPTURE_MODE -> {
                    if (value is Int) {
                        setCaptureMode(value)
                    }
                }
                KEY_JPEG_QUALITY -> {
                    if (value is Int) {
                        setJpegQuality(value)
                    }
                }
            }
        }
        Log.d(TAG, "Settings imported: $settings")
    }

    // Enums and data classes
    enum class CameraOrientation {
        PORTRAIT, LANDSCAPE, AUTO
    }

    enum class ImageQuality {
        HIGH, MEDIUM, LOW
    }

    data class CameraConfiguration(
        val captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
        val targetResolution: android.util.Size? = null,
        val jpegQuality: Int = 95
    )

    data class SpeechConfiguration(
        val confidenceThreshold: Int = 70,
        val autoStart: Boolean = false
    )

    // Settings info
    fun getSettingsInfo(): String {
        return buildString {
            appendLine("Settings:")
            appendLine("  Camera Orientation: ${getCameraOrientation()}")
            appendLine("  Image Quality: ${getImageQuality()}")
            appendLine("  Auto-start Translation: ${shouldAutoStartTranslation()}")
            appendLine("  Confidence Threshold: ${getConfidenceThreshold()}%")
            appendLine("  Capture Mode: ${getCaptureMode()}")
            appendLine("  JPEG Quality: ${getJpegQuality()}%")
            appendLine("  Target Resolution: ${getTargetResolution()}")
        }
    }
}