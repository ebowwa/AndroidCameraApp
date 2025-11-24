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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

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
    private var recognizer: Recognizer? = null
    private var modelLoadJob: Job? = null
    private var transcriptionJob: Job? = null
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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

                // Download real Vosk model
                val modelPath = File(filesDir, MODEL_NAME)
                val zipFile = File(filesDir, "$MODEL_NAME.zip")

                if (modelPath.exists()) {
                    Log.i(TAG, "Model already exists, loading...")
                    withContext(Dispatchers.Main) {
                        loadLocalModel(modelPath)
                    }
                    return@launch
                }

                // Download model file
                transcriptionCallback?.onModelLoadingProgress(0.2f)
                val url = URL(MODEL_URL)
                url.openStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytesRead = 0L
                        val expectedSize = MODEL_SIZE.toLong()

                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val progress = (totalBytesRead.toFloat() / expectedSize).coerceIn(0f, 1f)
                            transcriptionCallback?.onModelLoadingProgress(0.2f + progress * 0.6f)
                        }
                    }
                }

                transcriptionCallback?.onModelLoadingProgress(0.8f)

                // Extract ZIP file
                extractZipFile(zipFile, modelPath)
                zipFile.delete()

                transcriptionCallback?.onModelLoadingProgress(0.9f)

                withContext(Dispatchers.Main) {
                    loadLocalModel(modelPath)
                    transcriptionCallback?.onModelLoadingProgress(1.0f)
                    transcriptionCallback?.onModelLoaded(true)
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

    private fun extractZipFile(zipFile: File, outputDir: File) {
        outputDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outputFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (zis.read(buffer).also { bytesRead = it } > 0) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun startTranscription() {
        val model = this.model ?: run {
            transcriptionCallback?.onError("Model not loaded. Please wait for model download.")
            return
        }

        try {
            // Create recognizer for continuous recognition
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            // Start real audio recording and transcription
            transcriptionJob = serviceScope.launch(Dispatchers.IO) {
                try {
                    // Initialize AudioRecord for real microphone input
                    val audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        BUFFER_SIZE
                    )

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        withContext(Dispatchers.Main) {
                            transcriptionCallback?.onError("Failed to initialize audio recording")
                        }
                        return@launch
                    }

                    audioRecord.startRecording()
                    Log.i(TAG, "Real audio recording started")

                    val audioBuffer = ByteArray(BUFFER_SIZE)

                    while (isActive) {
                        val bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                        if (bytesRead > 0) {
                            // Process audio chunk with Vosk recognizer
                            recognizer?.let { rec ->
                                if (rec.acceptWaveForm(audioBuffer, bytesRead)) {
                                    // Check for partial result
                                    val partialResult = rec.partialResult
                                    if (partialResult.isNotEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            transcriptionCallback?.onTranscriptionResult(partialResult, 0.7f)
                                        }
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in real transcription", e)
                    withContext(Dispatchers.Main) {
                        transcriptionCallback?.onError("Transcription error: ${e.message}")
                    }
                }
            }

            Log.i(TAG, "Real Vosk speech recognition started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            transcriptionCallback?.onError("Failed to start recognition: ${e.message}")
        }
    }

    private fun stopTranscription() {
        // Cancel transcription job
        transcriptionJob?.cancel()
        transcriptionJob = null

        // Clean up recognizer
        recognizer?.let { rec ->
            try {
                rec.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            } finally {
                recognizer = null
            }
        }

        Log.i(TAG, "Real Vosk speech recognition stopped")
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
        return recognizer != null && transcriptionJob?.isActive == true
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