package com.camerapp.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    // Camera components
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // StreamPack Streamer (Future Integration)
    // private var streamer: io.github.thibaultbee.streampack.streamers.CameraStreamer? = null

    // Configuration
    private var captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    private var targetResolution: android.util.Size? = null
    private var jpegQuality: Int = 95

    interface CameraCaptureCallback {
        fun onCaptureSuccess(photoFile: File, previewBitmap: android.graphics.Bitmap?)
        fun onCaptureError(error: String)
        fun onCameraInitialized()
        fun onCameraError(error: String)
    }

    private var callback: CameraCaptureCallback? = null

    fun setCallback(callback: CameraCaptureCallback) {
        this.callback = callback
    }

    suspend fun initializeCamera() = withContext(Dispatchers.Main) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()

            // Configure image capture with headless mode
            // TODO: Initialize StreamPack Streamer here
            // streamer = CameraStreamer(context)
            // streamer?.configure(videoConfig, audioConfig)
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(captureMode)
                .apply {
                    targetResolution?.let { setTargetResolution(it) }
                    setJpegQuality(jpegQuality)
                }
                .build()

            // Headless binding - no preview, only image capture
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture  // Headless: ImageCapture only
            )

            callback?.onCameraInitialized()
            Log.d(TAG, "Camera initialized successfully")

        } catch (exc: Exception) {
            Log.e(TAG, "Camera initialization failed", exc)
            callback?.onCameraError("Failed to initialize camera: ${exc.message}")
        }
    }

    suspend fun capturePhoto() = withContext(Dispatchers.IO) {
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture not initialized")
            withContext(Dispatchers.Main) {
                callback?.onCaptureError("Camera not ready")
            }
            return@withContext
        }

        try {
            val photoFile = createImageFile()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        callback?.onCaptureError("Photo capture failed: ${exc.message}")
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: return

                        // Add to gallery
                        addImageToGallery(photoFile)

                        // Load preview bitmap
                        val previewBitmap = loadPreviewBitmap(photoFile)

                        callback?.onCaptureSuccess(photoFile, previewBitmap)
                        Log.d(TAG, "Photo saved successfully: $savedUri")
                    }
                }
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Failed to capture photo", exc)
            withContext(Dispatchers.Main) {
                callback?.onCaptureError("Failed to capture photo: ${exc.message}")
            }
        }
    }

    fun configureCamera(
        captureMode: Int = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
        targetResolution: android.util.Size? = null,
        jpegQuality: Int = 95
    ) {
        this.captureMode = captureMode
        this.targetResolution = targetResolution
        this.jpegQuality = jpegQuality
        Log.d(TAG, "Camera configuration updated - Mode: $captureMode, Quality: $jpegQuality%")
    }

    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageCapture = null
            cameraProvider = null
            Log.d(TAG, "Camera resources cleaned up")
        } catch (exc: Exception) {
            Log.e(TAG, "Error during camera cleanup", exc)
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "IMG_${timeStamp}.jpg")
    }

    private fun addImageToGallery(photoFile: File) {
        val contentValues = ContentValues().apply {
            val relativePath = "Pictures/CameraApp"
            put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                put(MediaStore.Images.Media.DATA, photoFile.absolutePath)
            }
        }

        val imagesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(imagesUri, contentValues)

        uri?.let { uriValue ->
            try {
                val outputStream = context.contentResolver.openOutputStream(uriValue)
                outputStream?.use { stream ->
                    photoFile.inputStream().use { input ->
                        input.copyTo(stream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uriValue, contentValues, null, null)
                } else {
                    // No action needed for pre-Q Android versions
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save image to gallery", e)
            }
        }
    }

    private fun loadPreviewBitmap(photoFile: File): android.graphics.Bitmap? {
        return try {
            BitmapFactory.decodeFile(photoFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preview bitmap", e)
            null
        }
    }

    // Camera info methods
    fun isCameraInitialized(): Boolean {
        return imageCapture != null && camera != null
    }

    fun getCameraInfo(): String {
        return if (isCameraInitialized()) {
            "Camera: Back camera initialized"
        } else {
            "Camera: Not initialized"
        }
    }

    // StreamPack Methods (Future Integration)
    /*
    suspend fun startStreaming(endpoint: String) {
        // streamer?.startStream(endpoint)
    }

    fun stopStreaming() {
        // streamer?.stopStream()
    }
    */
}