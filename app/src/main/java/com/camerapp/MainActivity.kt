package com.camerapp

import android.Manifest
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import android.os.Bundle
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.camerapp.camera.CameraManager
import com.camerapp.transcription.SpeechTranscriptionManager
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var transcriptionManager: SpeechTranscriptionManager
    private lateinit var statusText: TextView
    private lateinit var transcriptionText: TextView
    private val TRANSCRIPTION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeManagers()
        setupUI()
        handleIntentActions()
    }

    private fun initializeManagers() {
        cameraManager = CameraManager(this)
        transcriptionManager = SpeechTranscriptionManager(this)
        setupTranscriptionObservers()
    }

    private fun setupTranscriptionObservers() {
        lifecycleScope.launch {
            transcriptionManager.isTranscribing.collect { isTranscribing ->
                updateTranscriptionUI(isTranscribing)
            }
        }

        lifecycleScope.launch {
            transcriptionManager.transcribedText.collect { text ->
                transcriptionText.text = text
                if (text.isNotEmpty()) {
                    handleTranscribedText(text)
                } else {
                    transcriptionText.text = "Tap microphone to start transcription"
                }
            }
        }
    }

    private fun setupUI() {
        // Setup UI components with proper IDs
    }

    private fun handleIntentActions() {
        // Handle any app-specific intent actions
    }

    private fun handleTranscribedText(text: String) {
        val processedText = text.lowercase().trim()

        when {
            processedText.contains("capture") || processedText.contains("photo") || processedText.contains("picture") || processedText.contains("take picture") -> {
                lifecycleScope.launch {
                    capturePhoto()
                }
            }
            processedText.contains("clear") || processedText.contains("reset") -> {
                transcriptionManager.clearTranscription()
                transcriptionText.text = "Transcription cleared"
            }
            else -> {
                // Show the transcribed text for other commands
                Toast.makeText(this, "Heard: $text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun capturePhoto() {
        // CameraX photo capture implementation
        Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show()
    }

    private fun handleQRCodeScanned(qrCodeData: String) {
        // Copy to clipboard immediately
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("QR Code: $qrCodeData")
        clipboard.setPrimaryClip(clip)

        // Save to internal storage with timestamp
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val logEntry = "[$timestamp] QR Code: $qrCodeData\n"

            openFileOutput("qr_codes.txt", MODE_APPEND).use { output ->
                output.write(logEntry.toByteArray())
            }

            // Show success feedback
            Toast.makeText(this, "QR Code scanned! Data saved.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SimpleQRScanner", "Failed to save QR code data: ${e.message}")
            Toast.makeText(this, "Error saving QR code", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            TRANSCRIPTION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                    toggleTranscription()
                } else {
                    Toast.makeText(this, "Microphone permission required for transcription", Toast.LENGTH_LONG).show()
                    transcriptionText.text = "Microphone permission required"
                }
            }
        }
    }

    private fun toggleTranscription() {
        lifecycleScope.launch {
            if (transcriptionManager.isTranscribing.value) {
                transcriptionManager.stopTranscription()
                transcriptionText.text = "Transcription stopped"
            } else {
                transcriptionManager.startTranscription()
                transcriptionText.text = "Transcription started"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transcriptionManager.destroy()
    }
    companion object {
        private const val TAG = "MainActivity"
    }
}