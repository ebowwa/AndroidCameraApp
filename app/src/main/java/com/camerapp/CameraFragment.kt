package com.camerapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.camerapp.audio.VoiceActivityDetector
import com.camerapp.camera.CameraManager
import com.camerapp.databinding.FragmentCameraBinding
import com.camerapp.network.NetworkMonitor
import com.camerapp.settings.AppPreferences
import com.camerapp.ui.SettingsScreen
import kotlinx.coroutines.launch
import java.io.File
import com.camerapp.R

class CameraFragment : Fragment(),
    CameraManager.CameraCaptureCallback,
    VoiceActivityDetector.VoiceActivityDetectorCallback,
    NetworkMonitor.NetworkMonitorCallback,
    SettingsScreen.ModelManagementListener {

    companion object {
        private const val TAG = "CameraFragment"
        fun newInstance(): CameraFragment {
            return CameraFragment()
        }
    }

    // View binding
    private var _binding: FragmentCameraBinding? = null
    private val binding: FragmentCameraBinding
        get() = _binding ?: throw IllegalStateException("Fragment view not initialized")

    // Safe binding access
    private fun safeBinding(action: (FragmentCameraBinding) -> Unit) {
        _binding?.let(action)
    }

    // Managers
    private lateinit var cameraManager: CameraManager
    private lateinit var voiceActivityDetector: VoiceActivityDetector
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var appPreferences: AppPreferences

    // UI state
    private var currentSettingsFragment: SettingsScreen? = null

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

        initializeManagers()
        setupUI()
        initializeComponents()
    }

    private fun initializeManagers() {
        try {
            cameraManager = CameraManager(requireContext(), this)
            cameraManager.setCallback(this)

            voiceActivityDetector = VoiceActivityDetector(requireContext())
            voiceActivityDetector.setCallback(this)

            networkMonitor = NetworkMonitor(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                lifecycleScope = lifecycleScope
            )
            networkMonitor.setCallback(this)

            appPreferences = AppPreferences(requireContext())

            Log.d(TAG, "All managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize managers", e)
            Toast.makeText(requireContext(), "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.translationButton.setOnClickListener {
            toggleTranslation()
        }

        binding.settingsButton.setOnClickListener {
            openSettings()
        }

        // TODO: Add listener for Start Streaming button
        // binding.startStreamButton.setOnClickListener {
        //     cameraManager.startStreaming("rtmp://...")
        // }

        // Observe voice activity detector state
        lifecycleScope.launch {
            voiceActivityDetector.isRecordingState.collect { isRecording ->
                updateTranslationButtonState(isRecording)
            }
        }

        lifecycleScope.launch {
            voiceActivityDetector.recognizedText.collect { text ->
                updateTranslationText(text)
            }
        }

        lifecycleScope.launch {
            voiceActivityDetector.audioLevel.collect { level ->
                updateAudioLevel(level)
            }
        }

        Log.d(TAG, "UI setup completed")
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                // Start camera with settings configuration
                val cameraConfig = appPreferences.getCameraConfiguration()
                cameraManager.configureCamera(
                    captureMode = cameraConfig.captureMode,
                    targetResolution = cameraConfig.targetResolution,
                    jpegQuality = cameraConfig.jpegQuality
                )
                cameraManager.initializeCamera()

                // Start network monitoring
                networkMonitor.startMonitoring()

                // Configure voice activity detector
                val speechConfig = appPreferences.getSpeechConfiguration()
                voiceActivityDetector.configureSpeechDetection(speechConfig.confidenceThreshold)

                // Auto-start translation if enabled
                if (speechConfig.autoStart) {
                    startTranslation()
                }

                Log.d(TAG, "Components initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize components", e)
                Toast.makeText(requireContext(), "Failed to initialize: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun capturePhoto() {
        if (!cameraManager.isCameraInitialized()) {
            Toast.makeText(requireContext(), "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                cameraManager.capturePhoto()
                Log.d(TAG, "Photo capture initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture photo", e)
                Toast.makeText(requireContext(), "Failed to capture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleTranslation() {
        if (voiceActivityDetector.isCurrentlyRecording()) {
            stopTranslation()
        } else {
            startTranslation()
        }
    }

    private fun startTranslation() {
        if (!::voiceActivityDetector.isInitialized || !voiceActivityDetector.checkPermissions()) {
            Toast.makeText(requireContext(), "Microphone permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        // Show translation UI
        safeBinding { binding ->
            binding.translationContainer.visibility = View.VISIBLE
            binding.translationStatusText.text = "🎤 LISTENING..."
            binding.translationStatusText.setTextColor(android.graphics.Color.GREEN)
            binding.translationText.text = ""
        }

        voiceActivityDetector.startVoiceDetection()
        Log.d(TAG, "Translation started")
    }

    private fun stopTranslation() {
        if (!::voiceActivityDetector.isInitialized) return

        voiceActivityDetector.stopVoiceDetection()
        updateTranslationButtonState(false)

        // Hide container after delay if no meaningful content
        safeBinding { binding ->
            if (binding.translationText.text.isEmpty() || binding.translationText.text.contains("Speaking detected")) {
                binding.root.postDelayed({
                    if (!voiceActivityDetector.isCurrentlyRecording()) {
                        safeBinding { bindingInner ->
                            bindingInner.translationContainer.visibility = View.GONE
                            bindingInner.translationText.text = ""
                        }
                    }
                }, 2000)
            }
        }

        Log.d(TAG, "Translation stopped")
    }

    private fun openSettings() {
        val settingsScreen = SettingsScreen.newInstance()
        currentSettingsFragment = settingsScreen
        parentFragmentManager.beginTransaction()
            .replace(R.id.cameraContainer, settingsScreen)
            .addToBackStack("settings")
            .commit()
    }

    // UI update methods
    private fun updateTranslationButtonState(isRecording: Boolean) {
        binding.translationButton.setImageResource(
            if (isRecording) R.drawable.ic_mic_on else R.drawable.ic_mic
        )
    }

    private fun updateTranslationText(text: String) {
        safeBinding { binding ->
            binding.translationText.text = text
            binding.translationText.setTextColor(
                if (text.contains("Speaking detected") || text.contains("detected")) {
                    android.graphics.Color.parseColor("#AAAAAA")
                } else {
                    android.graphics.Color.WHITE
                }
            )
        }
    }

    private fun updateAudioLevel(level: Int) {
        safeBinding { binding ->
            val status = if (level > 0) {
                "🎤 RECORDING"
            } else {
                "⏸️ READY"
            }
            val color = if (level > 0) {
                android.graphics.Color.GREEN
            } else {
                android.graphics.Color.YELLOW
            }

            binding.translationStatusText.text = status
            binding.translationStatusText.setTextColor(color)
        }
    }

    private fun showCapturedImagePreview(photoFile: File) {
        lifecycleScope.launch {
            safeBinding { binding ->
                binding.previewImage.visibility = View.VISIBLE
                binding.root.postDelayed({
                    safeBinding { bindingInner ->
                        bindingInner.previewImage.visibility = View.GONE
                    }
                }, 2000) // Show preview for 2 seconds
            }
        }
    }

    // CameraManager.CameraCaptureCallback implementation
    override fun onCaptureSuccess(photoFile: File, previewBitmap: android.graphics.Bitmap?) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
            showCapturedImagePreview(photoFile)
        }
        Log.d(TAG, "Photo capture successful: ${photoFile.name}")
    }

    override fun onCaptureError(error: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Photo capture failed: $error", Toast.LENGTH_SHORT).show()
        }
        Log.e(TAG, "Photo capture error: $error")
    }

    override fun onCameraInitialized() {
        Log.d(TAG, "Camera initialized successfully")
    }

    override fun onCameraError(error: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Camera error: $error", Toast.LENGTH_LONG).show()
        }
        Log.e(TAG, "Camera error: $error")
    }

    // VoiceActivityDetector.VoiceActivityDetectorCallback implementation
    override fun onPermissionError(error: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Permission error: $error", Toast.LENGTH_LONG).show()
        }
        Log.e(TAG, "Audio permission error: $error")
    }

    override fun onRecordingStarted() {
        Log.d(TAG, "Audio recording started")
    }

    override fun onRecordingStopped() {
        Log.d(TAG, "Audio recording stopped")
    }

    override fun onSpeechDetected(text: String) {
        Log.d(TAG, "Speech detected: $text")
    }

    override fun onError(error: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Audio error: $error", Toast.LENGTH_SHORT).show()
        }
        Log.e(TAG, "Audio error: $error")
    }

    override fun onTranscriptionCompleted(result: com.camerapp.audio.TranscriptionResult) {
        Log.d(TAG, "Transcription completed: ${result.text}")
        if (result.success) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Transcribed: ${result.text}", Toast.LENGTH_LONG).show()
            }
        } else {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Transcription failed: ${result.error}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // NetworkMonitor.NetworkMonitorCallback implementation
    override fun onConnectionRestored() {
        Log.d(TAG, "Network connection restored")
    }

    override fun onConnectionLost() {
        Log.d(TAG, "Network connection lost")
    }

    // SettingsScreen.ModelManagementListener implementation
    override fun downloadModel() {
        Toast.makeText(requireContext(), "Regional model download not yet enabled", Toast.LENGTH_SHORT).show()
    }

    override fun deleteModel() {
        Toast.makeText(requireContext(), "No regional model installed", Toast.LENGTH_SHORT).show()
    }

    override fun isModelDownloaded(): Boolean {
        return false
    }

    override fun getModelSize(): String {
        return "No regional model installed"
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Cleanup managers safely
        if (::cameraManager.isInitialized) {
            cameraManager.cleanup()
        }
        
        // TODO: Release StreamPack resources
        // cameraManager.stopStreaming()

        if (::voiceActivityDetector.isInitialized) {
            voiceActivityDetector.cleanup()
        }
        if (::networkMonitor.isInitialized) {
            networkMonitor.cleanup()
        }

        // Clear references
        currentSettingsFragment = null
        _binding = null

        Log.d(TAG, "CameraFragment destroyed and cleaned up")
    }
}