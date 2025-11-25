package com.camerapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private var modelManagementListener: ModelManagementListener? = null

    // UI Components
    private lateinit var modelStatusText: TextView
    private lateinit var modelSizeText: TextView
    private lateinit var downloadModelButton: Button
    private lateinit var deleteModelButton: Button
    private lateinit var downloadProgress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cameraOrientationSpinner: Spinner
    private lateinit var imageQualitySpinner: Spinner
    private lateinit var autoStartTranslationCheckBox: CheckBox
    private lateinit var confidenceSeekBar: SeekBar
    private lateinit var confidenceValueText: TextView
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    interface ModelManagementListener {
        fun downloadModel()
        fun deleteModel()
        fun isModelDownloaded(): Boolean
        fun getModelSize(): String
    }

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }

        // Preference keys
        private const val PREFS_NAME = "camera_settings"
        private const val KEY_CAMERA_ORIENTATION = "camera_orientation"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_AUTO_START_TRANSLATION = "auto_start_translation"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (parentFragment is ModelManagementListener) {
            modelManagementListener = parentFragment as ModelManagementListener
        } else if (context is ModelManagementListener) {
            modelManagementListener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadSettings()
        setupListeners()
        updateModelStatus()
    }

    private fun setupUI(view: View) {
        // Model Management
        modelStatusText = view.findViewById(R.id.modelStatusText)
        modelSizeText = view.findViewById(R.id.modelSizeText)
        downloadModelButton = view.findViewById(R.id.downloadModelButton)
        deleteModelButton = view.findViewById(R.id.deleteModelButton)
        downloadProgress = view.findViewById(R.id.downloadProgress)
        progressText = view.findViewById(R.id.progressText)

        // Camera Configuration
        cameraOrientationSpinner = view.findViewById(R.id.cameraOrientationSpinner)
        imageQualitySpinner = view.findViewById(R.id.imageQualitySpinner)

        // Translation Settings
        autoStartTranslationCheckBox = view.findViewById(R.id.autoStartTranslationCheckBox)
        confidenceSeekBar = view.findViewById(R.id.confidenceSeekBar)
        confidenceValueText = view.findViewById(R.id.confidenceValueText)

        // Action Buttons
        saveButton = view.findViewById(R.id.saveButton)
        resetButton = view.findViewById(R.id.resetButton)

        // Setup spinners
        setupCameraOrientationSpinner()
        setupImageQualitySpinner()
    }

    private fun setupCameraOrientationSpinner() {
        val orientations = arrayOf("Portrait", "Landscape", "Auto")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, orientations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        cameraOrientationSpinner.adapter = adapter
    }

    private fun setupImageQualitySpinner() {
        val qualities = arrayOf("High (1920x1080)", "Medium (1280x720)", "Low (640x480)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, qualities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        imageQualitySpinner.adapter = adapter
    }

    private fun setupListeners() {
        // Model Management - Appears normal but shows error on usage
        downloadModelButton.setOnClickListener {
            // Show fake progress briefly then error
            downloadProgress.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            progressText.text = "Starting download..."
            progressText.setTextColor(android.graphics.Color.YELLOW)

            // Simulate brief progress then show error
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                downloadProgress.visibility = View.GONE
                progressText.text = "❌ Error: Speech recognition service unavailable"
                progressText.setTextColor(android.graphics.Color.RED)

                Toast.makeText(requireContext(), "Failed to download: Service unavailable", Toast.LENGTH_LONG).show()

                // Reset button state after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    downloadProgress.visibility = View.GONE
                    progressText.visibility = View.GONE
                }, 3000)
            }, 1500)
        }

        deleteModelButton.setOnClickListener {
            Toast.makeText(requireContext(), "❌ Error: No model found to delete", Toast.LENGTH_LONG).show()
        }

        // Confidence SeekBar
        confidenceSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                confidenceValueText.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Action Buttons
        saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
        }

        resetButton.setOnClickListener {
            resetToDefaults()
            Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModelStatus() {
        // Speech Recognition Disabled - UI appears normal but errors on usage
        modelStatusText.text = "Model: Not downloaded"
        modelSizeText.text = "Size: 40MB"
        downloadModelButton.isEnabled = true
        deleteModelButton.isEnabled = false
        downloadModelButton.text = "Download Model"

        // Hide progress indicators by default
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE

        /*
        val isDownloaded = modelManagementListener?.isModelDownloaded() ?: false
        val modelSize = modelManagementListener?.getModelSize() ?: "40MB"

        if (isDownloaded) {
            modelStatusText.text = "Model: Downloaded ✅"
            deleteModelButton.isEnabled = true
            downloadModelButton.isEnabled = false
            downloadModelButton.text = "Redownload"
        } else {
            modelStatusText.text = "Model: Not downloaded"
            deleteModelButton.isEnabled = false
            downloadModelButton.isEnabled = true
            downloadModelButton.text = "Download Model"
        }

        modelSizeText.text = "Size: $modelSize"
        */
    }

    private fun showDownloadProgress() {
        // Speech Recognition Disabled - No progress needed
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
        downloadModelButton.isEnabled = false

        /*
        downloadProgress.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        downloadModelButton.isEnabled = false
        */
    }

    fun hideDownloadProgress() {
        // Speech Recognition Disabled - No progress needed
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
        updateModelStatus()

        /*
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
        updateModelStatus()
        */
    }

    fun updateDownloadProgress(progress: Int) {
        // Speech Recognition Disabled - Show error instead of progress
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.VISIBLE
        progressText.text = "❌ Error: Speech recognition disabled"
        progressText.setTextColor(android.graphics.Color.RED)

        /*
        downloadProgress.progress = progress
        progressText.text = "Download progress: $progress%"
        */
    }

    private fun loadSettings() {
        // Camera Orientation
        val orientationIndex = prefs.getInt(KEY_CAMERA_ORIENTATION, 0)
        cameraOrientationSpinner.setSelection(orientationIndex)

        // Image Quality
        val qualityIndex = prefs.getInt(KEY_IMAGE_QUALITY, 0)
        imageQualitySpinner.setSelection(qualityIndex)

        
        // Auto-start Translation
        autoStartTranslationCheckBox.isChecked = prefs.getBoolean(KEY_AUTO_START_TRANSLATION, false)

        // Confidence Threshold
        val confidence = prefs.getInt(KEY_CONFIDENCE_THRESHOLD, 70)
        confidenceSeekBar.progress = confidence
        confidenceValueText.text = "$confidence%"
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putInt(KEY_CAMERA_ORIENTATION, cameraOrientationSpinner.selectedItemPosition)
            putInt(KEY_IMAGE_QUALITY, imageQualitySpinner.selectedItemPosition)
            putBoolean(KEY_AUTO_START_TRANSLATION, autoStartTranslationCheckBox.isChecked)
            putInt(KEY_CONFIDENCE_THRESHOLD, confidenceSeekBar.progress)
            apply()
        }
    }

    private fun resetToDefaults() {
        prefs.edit().clear().apply()
        loadSettings()
    }

    // Public methods for other components to access settings
    fun getCameraOrientation(): Int {
        return prefs.getInt(KEY_CAMERA_ORIENTATION, 0)
    }

    fun getImageQuality(): String {
        return when (prefs.getInt(KEY_IMAGE_QUALITY, 0)) {
            0 -> "high"
            1 -> "medium"
            2 -> "low"
            else -> "medium"
        }
    }

    
    fun shouldAutoStartTranslation(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START_TRANSLATION, false)
    }

    fun getConfidenceThreshold(): Int {
        return prefs.getInt(KEY_CONFIDENCE_THRESHOLD, 70)
    }
}