package com.camerapp.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Environment
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
    }

    init {
        initializeCamera()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun capturePhoto(): Boolean {
        return try {
            if (imageCapture == null) {
                setupCamera()
            }

            val photoFile = createImageFile()
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture?.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "Photo capture succeeded: ${output.savedUri}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }
                }
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo", e)
            false
        }
    }

    private fun setupCamera() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera provider not available")

        try {
            // Unbind any existing use cases
            cameraProvider.unbindAll()

            // Create preview use case (hidden for headless operation)
            val preview = Preview.Builder().build()

            // Create image capture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select back camera (glasses only have back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup camera", e)
            throw e
        }
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
        val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CameraApp")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "${timestamp}$PHOTO_EXTENSION")
    }

    fun release() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}