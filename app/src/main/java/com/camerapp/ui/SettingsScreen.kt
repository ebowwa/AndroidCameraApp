package com.camerapp.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.camerapp.R
import com.camerapp.settings.AppPreferences
import kotlinx.coroutines.launch

class SettingsScreen : Fragment() {

    private lateinit var appPreferences: AppPreferences
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
        fun newInstance(): SettingsScreen {
            return SettingsScreen()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appPreferences = AppPreferences(context)

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
        // Regional Transcription Model Settings
        downloadModelButton.setOnClickListener {
            Toast.makeText(requireContext(), "Regional model download not yet enabled", Toast.LENGTH_LONG).show()
        }

        deleteModelButton.setOnClickListener {
            Toast.makeText(requireContext(), "No regional model installed", Toast.LENGTH_SHORT).show()
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
        // Regional Model - Not yet integrated
        modelStatusText.text = "Model: Regional model not loaded"
        modelSizeText.text = "Size: ~40MB per language"
        downloadModelButton.isEnabled = true
        deleteModelButton.isEnabled = false
        downloadModelButton.text = "Download Regional Model"

        // Hide progress indicators by default
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
    }

    private fun showDownloadProgress() {
        // Speech Recognition Disabled - No progress needed
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
        downloadModelButton.isEnabled = false
    }

    fun hideDownloadProgress() {
        // Speech Recognition Disabled - No progress needed
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.GONE
        updateModelStatus()
    }

    fun updateDownloadProgress(progress: Int) {
        // Speech Recognition Disabled - Show error instead of progress
        downloadProgress.visibility = View.GONE
        progressText.visibility = View.VISIBLE
        progressText.text = "❌ Error: Speech recognition disabled"
        progressText.setTextColor(android.graphics.Color.RED)
    }

    private fun loadSettings() {
        // Camera Orientation
        cameraOrientationSpinner.setSelection(appPreferences.getCameraOrientation().ordinal)

        // Image Quality
        imageQualitySpinner.setSelection(appPreferences.getImageQuality().ordinal)

        // Auto-start Translation
        autoStartTranslationCheckBox.isChecked = appPreferences.shouldAutoStartTranslation()

        // Confidence Threshold
        val confidence = appPreferences.getConfidenceThreshold()
        confidenceSeekBar.progress = confidence
        confidenceValueText.text = "$confidence%"
    }

    private fun saveSettings() {
        appPreferences.setCameraOrientation(
            AppPreferences.CameraOrientation.values()[cameraOrientationSpinner.selectedItemPosition]
        )
        appPreferences.setImageQuality(
            AppPreferences.ImageQuality.values()[imageQualitySpinner.selectedItemPosition]
        )
        appPreferences.setAutoStartTranslation(autoStartTranslationCheckBox.isChecked)
        appPreferences.setConfidenceThreshold(confidenceSeekBar.progress)
    }

    private fun resetToDefaults() {
        appPreferences.resetToDefaults()
        loadSettings()
    }
}