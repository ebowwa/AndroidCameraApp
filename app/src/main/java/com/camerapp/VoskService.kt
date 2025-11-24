package com.camerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Vosk Service for Live Speech-to-Text Translation
 *
 * Provides real-time offline speech recognition using Vosk API
 * Optimized for INMO IMA301 smart glasses with local processing
 */
class VoskService : Service() {

    companion object {
        private const val TAG = "VoskService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "vosk_service_channel"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096

        // Model download URL - English small model (40MB)
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val MODEL_SIZE = 40 * 1024 * 1024 // 40MB
    }

    private val binder = VoskBinder()
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var modelLoadJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Transcription callback interface
    interface TranscriptionCallback {
        fun onTranscriptionResult(text: String, confidence: Float)
        fun onError(error: String)
        fun onRecordingStateChanged(isRecording: Boolean)
        fun onModelLoadingProgress(progress: Float)
        fun onModelLoaded(success: Boolean)
    }

    private var transcriptionCallback: TranscriptionCallback? = null

    inner class VoskBinder : Binder() {
        fun getService(): VoskService = this@VoskService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            "START_TRANSCRIPTION" -> {
                startTranscription()
                transcriptionCallback?.onRecordingStateChanged(true)
            }
            "STOP_TRANSCRIPTION" -> {
                stopTranscription()
                transcriptionCallback?.onRecordingStateChanged(false)
            }
            "DOWNLOAD_MODEL" -> {
                downloadModel()
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        LibVosk.setLogLevel(LogLevel.INFO)
        initializeModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vosk Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live speech-to-text translation service"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel, null)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Translation Active")
            .setContentText("Vosk offline speech recognition running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun initializeModel() {
        val modelPath = File(filesDir, MODEL_NAME)

        if (modelPath.exists()) {
            loadLocalModel(modelPath)
        } else {
            transcriptionCallback?.onModelLoadingProgress(0f)
            downloadModel()
        }
    }

    private fun downloadModel() {
        modelLoadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                transcriptionCallback?.onModelLoadingProgress(0.1f)

                // For demo purposes, simulate model download
                // In a real implementation, download and extract the Vosk model
                val modelPath = File(filesDir, MODEL_NAME)
                modelPath.mkdirs()

                // Simulate download progress
                for (i in 1..100) {
                    delay(50) // Simulate download time
                    val progress = i / 100f
                    transcriptionCallback?.onModelLoadingProgress(0.1f + progress * 0.8f)
                }

                transcriptionCallback?.onModelLoadingProgress(0.9f)

                // Note: In a real implementation, you would:
                // 1. Download the actual model file from MODEL_URL
                // 2. Extract the ZIP file
                // 3. Load the model from the extracted files

                withContext(Dispatchers.Main) {
                    transcriptionCallback?.onError("Demo mode: Model download simulated")
                    transcriptionCallback?.onModelLoaded(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model", e)
                withContext(Dispatchers.Main) {
                    transcriptionCallback?.onError("Failed to download model: ${e.message}")
                    transcriptionCallback?.onModelLoaded(false)
                }
            }
        }
    }

    private fun loadLocalModel(modelPath: File) {
        try {
            model = Model(modelPath.absolutePath)
            Log.i(TAG, "Vosk model loaded successfully from ${modelPath.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Vosk model", e)
            transcriptionCallback?.onError("Failed to load model: ${e.message}")
        }
    }

    // Note: Real model download and extraction would go here
  // For this demo, we're simulating the process

    private fun startTranscription() {
        val model = this.model ?: run {
            transcriptionCallback?.onError("Model not loaded. Please wait for model download.")
            return
        }

        try {
            // Create speech recognizer with 16kHz mono audio
            val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            // Create speech service for continuous recognition
            speechService = SpeechService(recognizer, SAMPLE_RATE.toFloat())

            // Set up recognition callback
            speechService?.setListener(object : SpeechService.RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    // Handle partial results (real-time feedback)
                    hypothesis?.let { text ->
                        if (text.isNotBlank()) {
                            transcriptionCallback?.onTranscriptionResult(text, 0.5f)
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    // Handle final results
                    hypothesis?.let { text ->
                        if (text.isNotBlank()) {
                            transcriptionCallback?.onTranscriptionResult(text, 1.0f)
                        }
                    }
                }

                override fun onError(e: Exception?) {
                    Log.e(TAG, "Speech recognition error", e)
                    transcriptionCallback?.onError("Recognition error: ${e?.message}")
                }

                override fun onTimeout() {
                    Log.w(TAG, "Speech recognition timeout")
                }
            })

            // Start recognizing
            speechService?.startRecognizing()
            Log.i(TAG, "Vosk speech recognition started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            transcriptionCallback?.onError("Failed to start recognition: ${e.message}")
        }
    }

    private fun stopTranscription() {
        speechService?.let { service ->
            try {
                service.stopRecognizing()
                service.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition", e)
            } finally {
                speechService = null
            }
        }
        Log.i(TAG, "Vosk speech recognition stopped")
    }

    private fun cleanup() {
        stopTranscription()
        modelLoadJob?.cancel()
        model?.close()
        serviceScope.cancel()
        Log.i(TAG, "Vosk service cleaned up")
    }

    // Public API
    fun setTranscriptionCallback(callback: TranscriptionCallback) {
        transcriptionCallback = callback
    }

    fun isRecording(): Boolean {
        return speechService != null
    }

    fun isModelLoaded(): Boolean {
        return model != null
    }

    fun getModelName(): String {
        return MODEL_NAME
    }

    fun getModelPath(): String? {
        return model?.let { File(filesDir, MODEL_NAME).absolutePath }
    }
}