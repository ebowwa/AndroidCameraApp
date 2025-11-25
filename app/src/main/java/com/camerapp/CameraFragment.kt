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

class CameraFragment : Fragment(), SettingsFragment.ModelManagementListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled = false

    // Speech Recognition Service - Removed transcription model (will be replaced later)
    // private var transcriptionService: TranscriptionService? = null
    // private var isTranscriptionBound = false
    // private var isTranslationActive = false
    private var currentSettingsFragment: SettingsFragment? = null

    // Speech Recognition Connection - Removed transcription model (will be replaced later)
    /*
    private val transcriptionConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TranscriptionService.TranscriptionBinder
            transcriptionService = binder.getService()
            isTranscriptionBound = true

            // Set up transcription callback
            transcriptionService?.setTranscriptionCallback(object : TranscriptionService.TranscriptionCallback {
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
            isTranscriptionBound = false
        }
    }
    */

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
        binding.translationButton.setOnClickListener {
            // Show UI state change then error
            binding.translationContainer.visibility = View.VISIBLE
            binding.translationStatusText.text = "🔴 STARTING..."
            binding.translationStatusText.setTextColor(android.graphics.Color.YELLOW)
            binding.translationText.text = ""

            // Simulate starting then show error
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                binding.translationStatusText.text = "🔴 TRANSLATION ERROR"
                binding.translationStatusText.setTextColor(android.graphics.Color.RED)
                binding.translationText.text = "❌ Speech recognition service unavailable"

                Toast.makeText(requireContext(), "Failed to start: Service unavailable", Toast.LENGTH_LONG).show()

                // Hide after delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    binding.translationContainer.visibility = View.GONE
                }, 4000)
            }, 2000)
        }
        binding.settingsButton.setOnClickListener { openSettings() }
        // Switch camera button removed - glasses use back camera only

        // Speech Recognition Service - Removed transcription model (will be replaced later)
        // bindTranscriptionService()

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
                    // Check if binding is still valid (fragment not destroyed)
                    _binding?.let { binding ->
                        binding.flashButton.visibility = if (hasFlash) View.VISIBLE else View.GONE
                    }
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

        // Clear settings fragment reference
        currentSettingsFragment = null

        // Speech Recognition Service - Removed transcription model (will be replaced later)
        /*
        if (isTranscriptionBound) {
            requireContext().unbindService(transcriptionConnection)
            isTranscriptionBound = false
        }
        */

        _binding = null
    }

    // Speech Recognition Methods - Removed transcription model (will be replaced later)
    /*
    private fun bindTranscriptionService() {
        Intent(requireContext(), TranscriptionService::class.java).also { intent ->
            requireContext().bindService(intent, transcriptionConnection, Context.BIND_AUTO_CREATE)
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
        if (!isTranscriptionBound) {
            Toast.makeText(requireContext(), "Translation service not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), TranscriptionService::class.java).apply {
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
        val intent = Intent(requireContext(), TranscriptionService::class.java).apply {
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
        Log.d("CameraFragment", "Model download progress: $percentage%")

        // Pass progress to settings fragment if it's active
        currentSettingsFragment?.let { settings ->
            try {
                requireActivity().runOnUiThread {
                    settings.updateDownloadProgress(percentage)
                }
            } catch (e: Exception) {
                Log.e("CameraFragment", "Failed to update settings progress", e)
            }
        }
    }

    private fun updateModelStatus(success: Boolean) {
        // Model status is handled by SettingsFragment
        // This method is kept for compatibility but doesn't update UI
        if (success) {
            Log.d("CameraFragment", "Model loaded successfully")
        } else {
            Log.d("CameraFragment", "Model loading failed")
        }
    }
    */

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

    // Settings Fragment Interface Implementation
    private fun openSettings() {
        val settingsFragment = SettingsFragment.newInstance()
        currentSettingsFragment = settingsFragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.cameraContainer, settingsFragment)
            .addToBackStack("settings")
            .commit()
    }

    // Model Management Interface - Removed transcription model (will be replaced later)
    override fun downloadModel() {
        Log.d("CameraFragment", "downloadModel() called - speech recognition disabled")
        Toast.makeText(requireContext(), "Speech recognition temporarily disabled", Toast.LENGTH_SHORT).show()
        /*
        Log.d("CameraFragment", "downloadModel() called - starting TranscriptionService")
        val intent = Intent(requireContext(), TranscriptionService::class.java).apply {
            action = "DOWNLOAD_MODEL"
        }

        try {
            requireContext().startService(intent)
            Log.d("CameraFragment", "TranscriptionService started successfully")
        } catch (e: Exception) {
            Log.e("CameraFragment", "Failed to start TranscriptionService: ${e.message}", e)
            Toast.makeText(requireContext(), "Failed to start model download", Toast.LENGTH_LONG).show()
        }
        */
    }

    override fun deleteModel() {
        Toast.makeText(requireContext(), "Speech recognition temporarily disabled", Toast.LENGTH_SHORT).show()
        /*
        val modelPath = File(requireContext().filesDir, "transcription-model")
        val deleted = modelPath.deleteRecursively()

        if (deleted) {
            Toast.makeText(requireContext(), "Model deleted successfully", Toast.LENGTH_SHORT).show()
            // Stop the service to clear any loaded model
            val intent = Intent(requireContext(), TranscriptionService::class.java).apply {
                action = "STOP_TRANSCRIPTION"
            }
            requireContext().startService(intent)
        } else {
            Toast.makeText(requireContext(), "Failed to delete model", Toast.LENGTH_SHORT).show()
        }
        */
    }

    override fun isModelDownloaded(): Boolean {
        // Always return false since speech recognition is disabled
        return false
        /*
        val modelPath = File(requireContext().filesDir, "transcription-model")
        if (!modelPath.exists()) return false

        // Check for required model files
        val requiredFiles = listOf("model.bin", "conf")
        return requiredFiles.all { File(modelPath, it).exists() }
        */
    }

    override fun getModelSize(): String {
        // Return placeholder since speech recognition is disabled
        return "Speech recognition disabled"
        /*
        val modelPath = File(requireContext().filesDir, "transcription-model")
        if (!modelPath.exists()) return "Speech recognition disabled"

        return try {
            val size = modelPath.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            "${size / (1024 * 1024)}MB"
        } catch (e: Exception) {
            "Speech recognition disabled"
        }
        */
    }
    }
}
