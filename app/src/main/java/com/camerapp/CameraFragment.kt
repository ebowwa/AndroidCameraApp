package com.camerapp

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.camerapp.databinding.FragmentCameraBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled = false

    // Vosk Translation Service
    private var voskService: VoskService? = null
    private var isVoskBound = false
    private var isTranslationActive = false

    private val voskConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoskService.VoskBinder
            voskService = binder.getService()
            isVoskBound = true

            // Set up translation callback
            voskService?.setTranscriptionCallback(object : VoskService.TranscriptionCallback {
                override fun onTranscriptionResult(text: String, confidence: Float) {
                    requireActivity().runOnUiThread {
                        updateTranslationText(text)
                    }
                }

                override fun onError(error: String) {
                    requireActivity().runOnUiThread {
                        showTranslationError(error)
                    }
                }

                override fun onRecordingStateChanged(isRecording: Boolean) {
                    requireActivity().runOnUiThread {
                        updateTranslationStatus(isRecording)
                    }
                }

                override fun onModelLoadingProgress(progress: Float) {
                    requireActivity().runOnUiThread {
                        updateModelProgress(progress)
                    }
                }

                override fun onModelLoaded(success: Boolean) {
                    requireActivity().runOnUiThread {
                        updateModelStatus(success)
                    }
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isVoskBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.captureButton.setOnClickListener { takePhoto() }
        binding.flashButton.setOnClickListener { toggleFlash() }
        binding.translationButton.setOnClickListener { toggleTranslation() }
        // Switch camera button removed - glasses use back camera only

        // Bind to Vosk service
        bindVoskService()

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // HEADLESS CAMERA MODE - No Preview Configuration
            // Camera operates without any visual preview display
            // Perfect for smart glasses where you see through the device naturally
            // Image capture use case - CAMERA CAPTURE SIZE CONFIGURATION
            // Current: CAPTURE_MODE_MINIMIZE_LATENCY prioritizes speed over image quality
            // Default resolution: 640x480 (CameraX default when no target resolution set)
            // JPEG quality: ~95% (minimize latency mode reduces quality slightly)
            //
            // Available configuration options for image capture size:
            // - setTargetResolution(Size(width, height)): Set specific resolution (e.g., 1920x1080 for Full HD)
            // - setTargetAspectRatio(AspectRatio.RATIO_16_9): Set aspect ratio (4:3, 16:9, etc.)
            // - setJpegQuality(quality): Set JPEG quality 1-100 (100 = highest quality)
            // - setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_LATENCY): Speed priority (current)
            // - setCaptureMode(ImageCapture.CAPTURE_MODE_QUALITY): Quality priority
            // - setMaxResolution(Size): Set maximum allowed resolution
            //
            // Common resolution options for glasses:
            // - Size(1920, 1080) - Full HD (best quality, larger file sizes)
            // - Size(1280, 720) - HD (good balance)
            // - Size(640, 480) - Default (current, smaller files)
            // - Size(3840, 2160) - 4K (if hardware supports)
            //
            // File size impact:
            // - 640x480 @ ~95% quality: ~400-500KB
            // - 1280x720 @ ~95% quality: ~800-1200KB
            // - 1920x1080 @ ~95% quality: ~1.5-2.5MB
            // - 1920x1080 @ 100% quality: ~2.5-4MB

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // Add resolution/quality configuration here if needed:
                // .setTargetResolution(Size(1280, 720))  // Example: HD resolution
                // .setJpegQuality(100)  // Example: Highest quality
                .build()

            // HEADLESS CAMERA MODE - No Preview Display
            // For smart glasses, no preview is needed as users see through the device naturally
            // CameraX operates purely in background without any visual preview
            // This frees up the entire screen for AR overlays, controls, or information display
            //
            // CameraX Use Cases Used:
            // - ImageCapture: Photo capture functionality (active)
            // - Preview: REMOVED - not needed for headless operation
            // - ImageAnalysis: REMOVED - optional for future smart features

            try {
                cameraProvider?.unbindAll()
                // HEADLESS CAMERA BINDING - Only ImageCapture, no preview or analysis
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, imageCapture  // ImageCapture only - headless operation
                )

                // Set flash button based on camera capabilities
                camera?.cameraInfo?.let { cameraInfo ->
                    val hasFlash = cameraInfo.hasFlashUnit()
                    binding.flashButton.visibility = if (hasFlash) View.VISIBLE else View.GONE
                }

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        // CAPTURE SIZE IS DETERMINED BY THE ImageCapture.Builder() CONFIGURATION
        // See lines 101-106 above for available size/quality options
        val imageCapture = imageCapture ?: return

        val photoFile = createImageFile(requireContext())

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return

                    // Add to gallery
                    addImageToGallery(photoFile, requireContext())

                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Photo saved: $savedUri", Toast.LENGTH_SHORT).show()

                        // Show captured image preview briefly
                        showCapturedImagePreview(photoFile)
                    }
                }
            }
        )
    }

    // switchCamera() function removed - glasses use back camera only
    // No camera switching functionality needed

    private fun toggleFlash() {
        val camera = camera ?: return

        try {
            if (camera.cameraInfo.hasFlashUnit()) {
                isFlashEnabled = !isFlashEnabled
                camera.cameraControl.enableTorch(isFlashEnabled)

                // Update flash button icon
                binding.flashButton.setImageResource(
                    if (isFlashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                )
            }
        } catch (e: Exception) {
            Log.e("CameraX", "Failed to toggle flash", e)
        }
    }

    private fun showCapturedImagePreview(photoFile: File) {
        lifecycleScope.launch {
            binding.previewImage.visibility = View.VISIBLE
            binding.previewImage.postDelayed({
                binding.previewImage.visibility = View.GONE
            }, 2000) // Show preview for 2 seconds
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()

        // Unbind from Vosk service
        if (isVoskBound) {
            requireContext().unbindService(voskConnection)
            isVoskBound = false
        }

        _binding = null
    }

    // Vosk Translation Methods
    private fun bindVoskService() {
        Intent(requireContext(), VoskService::class.java).also { intent ->
            requireContext().bindService(intent, voskConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun toggleTranslation() {
        if (isTranslationActive) {
            stopTranslation()
        } else {
            startTranslation()
        }
    }

    private fun startTranslation() {
        if (!isVoskBound) {
            Toast.makeText(requireContext(), "Translation service not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), VoskService::class.java).apply {
            action = "START_TRANSCRIPTION"
        }
        requireContext().startService(intent)

        // Show translation UI
        binding.translationContainer.visibility = View.VISIBLE
        binding.translationStatusText.text = "🎤 STARTING TRANSLATION..."
        isTranslationActive = true

        Toast.makeText(requireContext(), "Translation started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTranslation() {
        val intent = Intent(requireContext(), VoskService::class.java).apply {
            action = "STOP_TRANSCRIPTION"
        }
        requireContext().startService(intent)

        binding.translationContainer.visibility = View.GONE
        isTranslationActive = false

        Toast.makeText(requireContext(), "Translation stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateTranslationText(text: String) {
        binding.translationText.text = text
    }

    private fun showTranslationError(error: String) {
        binding.translationText.text = "❌ Error: $error"
        binding.translationStatusText.text = "🔴 TRANSLATION ERROR"
        Toast.makeText(requireContext(), "Translation error: $error", Toast.LENGTH_LONG).show()
    }

    private fun updateTranslationStatus(isRecording: Boolean) {
        if (isRecording) {
            binding.translationStatusText.text = "🎤 LISTENING..."
            binding.translationButton.setImageResource(R.drawable.ic_mic_on)
        } else {
            binding.translationStatusText.text = "⏸️ PAUSED"
            binding.translationButton.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun updateModelProgress(progress: Float) {
        val percentage = (progress * 100).toInt()
        binding.modelStatusText.text = "Model: Loading $percentage%"
    }

    private fun updateModelStatus(success: Boolean) {
        if (success) {
            binding.modelStatusText.text = "Model: Ready ✅"
            binding.translationStatusText.text = "🎤 READY TO TRANSLATE"
        } else {
            binding.modelStatusText.text = "Model: Failed ❌"
            binding.translationStatusText.text = "❌ MODEL LOAD FAILED"
        }
    }

    companion object {
        private fun createImageFile(context: Context): File {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(context.getExternalFilesDir(null), "Pictures")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            return File(storageDir, "IMG_${timeStamp}.jpg")
        }

        private fun addImageToGallery(photoFile: File, context: Context) {
            val contentValues = ContentValues().apply {
                val relativePath = "Pictures/CameraApp"
                put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    // Pre-Android 10 requires writing to a concrete path
                    put(MediaStore.Images.Media.DATA, photoFile.absolutePath)
                }
            }

            val imagesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(
                imagesUri,
                contentValues
            )

            uri?.let {
                try {
                    val outputStream = context.contentResolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        photoFile.inputStream().use { input ->
                            input.copyTo(stream)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(it, contentValues, null, null)
                    }
                } catch (e: IOException) {
                    Log.e("CameraApp", "Failed to save image to gallery", e)
                }

                Unit
            }
        }
    }
}
