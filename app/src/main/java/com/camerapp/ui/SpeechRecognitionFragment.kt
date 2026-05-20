package com.camerapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.camerapp.R
import com.camerapp.audio.SpeechRecognitionManager
import kotlinx.coroutines.launch
import android.widget.Button
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ImageView

class SpeechRecognitionFragment : Fragment() {

    internal lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var recognizedTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusIcon: ImageView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_speech_recognition, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speechRecognitionManager = SpeechRecognitionManager(requireContext())

        setupViews(view)
        setupObservers()
        setupClickListeners()
    }

    private fun setupViews(view: View) {
        recognizedTextView = view.findViewById(R.id.recognizedTextView)
        statusTextView = view.findViewById(R.id.statusTextView)
        progressBar = view.findViewById(R.id.progressBar)
        statusIcon = view.findViewById(R.id.statusIcon)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            speechRecognitionManager.isListening.collect { isListening ->
                updateUIForListeningState(isListening)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            speechRecognitionManager.recognizedText.collect { text ->
                recognizedTextView.text = text
                if (text.isNotEmpty()) {
                    statusTextView.text = "Speech recognized successfully"
                    statusIcon.setImageResource(R.drawable.ic_mic_success)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            speechRecognitionManager.error.collect { error ->
                if (error != null) {
                    showErrorState(error)
                } else {
                    clearErrorState()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            speechRecognitionManager.isAvailable.collect { isAvailable ->
                if (!isAvailable) {
                    showUnavailableState()
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Voice button is handled by MainActivity
    }

    private fun requestOrStartSpeechRecognition() {
        when {
            !speechRecognitionManager.hasPermission() -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            !speechRecognitionManager.isAvailable.value -> {
                Toast.makeText(
                    requireContext(),
                    "Speech recognition is not available on this device",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                startSpeechRecognition()
            }
        }
    }

    private fun startSpeechRecognition() {
        if (speechRecognitionManager.hasPermission()) {
            speechRecognitionManager.startListening()
        }
    }

    private fun updateUIForListeningState(isListening: Boolean) {
        if (isListening) {
            progressBar.visibility = View.VISIBLE
            statusTextView.text = "Listening... Speak now"
            statusIcon.setImageResource(R.drawable.ic_mic_listening)
            recognizedTextView.text = ""
        } else {
            progressBar.visibility = View.GONE
            if (speechRecognitionManager.recognizedText.value.isEmpty()) {
                statusTextView.text = "Tap the microphone to start"
                statusIcon.setImageResource(R.drawable.ic_mic_idle)
            }
        }
    }

    private fun showErrorState(error: String) {
        statusTextView.text = error
        statusIcon.setImageResource(R.drawable.ic_mic_error)
        progressBar.visibility = View.GONE

        // Don't disable functionality - show retry capability
        Toast.makeText(requireContext(), "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
    }

    private fun clearErrorState() {
        // Clear error state when speech recognition starts working again
        statusIcon.setImageResource(R.drawable.ic_mic_idle)
    }

    private fun showUnavailableState() {
        statusTextView.text = "Speech recognition not available on this device"
        statusIcon.setImageResource(R.drawable.ic_mic_unavailable)
        progressBar.visibility = View.GONE
    }

    private fun showPermissionDeniedMessage() {
        statusTextView.text = "Microphone permission is required for voice input"
        statusIcon.setImageResource(R.drawable.ic_mic_permission_denied)
        Toast.makeText(
            requireContext(),
            "Microphone permission is required for speech recognition",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognitionManager.destroy()
    }

    fun clearText() {
        speechRecognitionManager.clearText()
        recognizedTextView.text = ""
        statusTextView.text = "Tap the microphone to start"
        statusIcon.setImageResource(R.drawable.ic_mic_idle)
    }

    fun toggleSpeechRecognition() {
        when {
            !speechRecognitionManager.hasPermission() -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            !speechRecognitionManager.isAvailable.value -> {
                Toast.makeText(
                    requireContext(),
                    "Speech recognition is not available on this device",
                    Toast.LENGTH_LONG
                ).show()
            }
            speechRecognitionManager.isListening.value -> {
                speechRecognitionManager.stopListening()
            }
            else -> {
                startSpeechRecognition()
            }
        }
    }
}