package com.camerapp.transcription

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class SpeechTranscriptionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    companion object {
        private const val TAG = "SpeechTranscription"
        private const val TRANSCRIPTION_REQUEST_CODE = 1001
    }

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                setupRecognitionListener()
                _isAvailable.value = true
                Log.d(TAG, "Speech recognizer available")
            } else {
                _isAvailable.value = false
                _error.value = "Speech recognition not available on this device"
                Log.w(TAG, "Speech recognizer not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer: ${e.message}")
            _isAvailable.value = false
            _error.value = "Speech recognition initialization failed"
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isTranscribing.value = true
                _error.value = null
                handler.post {
                    _transcribedText.value = "Listening..."
                }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isTranscribing.value = false
                handler.post {
                    _transcribedText.value = _transcribedText.value.replace("Listening...", "")
                }
            }

            override fun onError(errorCode: Int) {
                _isTranscribing.value = false
                val errorMessage = when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error occurred"
                }
                _error.value = errorMessage
                Log.e(TAG, "Speech recognition error: $errorMessage")
            }

            override fun onResults(results: Bundle?) {
                _isTranscribing.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches.joinToString(" ")
                    handler.post {
                        _transcribedText.value = recognizedText
                        Log.d(TAG, "Recognized: $recognizedText")
                    }
                }
                _error.value = null
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches.joinToString(" ")
                    handler.post {
                        _transcribedText.value = partialText
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startTranscription() {
        if (!hasPermission()) {
            _error.value = "Microphone permission required for transcription"
            return
        }

        if (!_isAvailable.value) {
            _error.value = "Speech recognition not available"
            return
        }

        if (isListening) {
            return // Already listening
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            isListening = true
            _isTranscribing.value = true
            _error.value = null
            _transcribedText.value = ""

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started speech transcription")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting transcription: ${e.message}")
            _error.value = "Failed to start transcription: ${e.message}"
            _isTranscribing.value = false
        }
    }

    fun stopTranscription() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
                isListening = false
                _isTranscribing.value = false
                Log.d(TAG, "Stopped speech transcription")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping transcription: ${e.message}")
            }
        }
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            requestCode
        )
    }

    fun clearTranscription() {
        _transcribedText.value = ""
        _error.value = null
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
        Log.d(TAG, "Speech transcription destroyed")
    }
}