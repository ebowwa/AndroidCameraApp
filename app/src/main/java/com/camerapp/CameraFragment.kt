package com.camerapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
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
        // Switch camera button removed - glasses use back camera only

        // Stream mode button - navigate to StreamFragment
        binding.streamModeButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.cameraContainer, com.camerapp.streaming.StreamFragment())
                .addToBackStack(null)
                .commit()
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview use case - CAMERA PREVIEW CONFIGURATION
            // Preview shows live camera feed in the viewFinder
            // Preview resolution is typically optimized for device display
            // Preview quality doesn't affect captured image quality
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

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

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Image analysis for focus, exposure, etc.
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
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
        _binding = null
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
