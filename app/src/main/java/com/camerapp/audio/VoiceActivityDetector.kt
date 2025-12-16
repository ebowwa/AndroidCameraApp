package com.camerapp.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceActivityDetector(private val context: Context) {
    companion object {
        private const val TAG = "VoiceActivityDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_THRESHOLD = 5000 // 5 seconds
    }

    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    // Audio configuration
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
    private val speechDetector = SimpleSpeechDetector()

    // Transcription components
    private var transcriptionService: GeminiTranscriptionService? = null
    private var audioProcessor: AudioProcessor? = null
    private val audioBuffer = mutableListOf<Byte>()
    private var transcriptionJob: Job? = null

    // State flows for UI observation
    private val _isRecordingState = MutableStateFlow(false)
    val isRecordingState: StateFlow<Boolean> = _isRecordingState

    private val _audioLevel = MutableStateFlow(0)
    val audioLevel: StateFlow<Int> = _audioLevel

    private val _speechDetected = MutableStateFlow(false)
    val speechDetected: StateFlow<Boolean> = _speechDetected

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _transcriptionInProgress = MutableStateFlow(false)
    val transcriptionInProgress: StateFlow<Boolean> = _transcriptionInProgress

    private val _transcriptionResult = MutableStateFlow<TranscriptionResult?>(null)
    val transcriptionResult: StateFlow<TranscriptionResult?> = _transcriptionResult

    interface VoiceActivityDetectorCallback {
        fun onPermissionError(error: String)
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun onSpeechDetected(text: String)
        fun onError(error: String)
        fun onTranscriptionCompleted(result: TranscriptionResult)
    }

    private var callback: VoiceActivityDetectorCallback? = null

    fun setCallback(callback: VoiceActivityDetectorCallback) {
        this.callback = callback
    }

    init {
        initializeTranscriptionServices()
    }

    private fun initializeTranscriptionServices() {
        try {
            transcriptionService = GeminiTranscriptionService(context)
            audioProcessor = AudioProcessor()
            Log.d(TAG, "Transcription services initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize transcription services", e)
        }
    }

    fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startVoiceDetection() {
        if (!checkPermissions()) {
            callback?.onPermissionError("Microphone permission not granted")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Voice detection already in progress")
            return
        }

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                initializeAudioRecording()
                startAudioCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Voice detection failed", e)
                withContext(Dispatchers.Main) {
                    callback?.onError("Failed to start voice detection: ${e.message}")
                }
                stopVoiceDetection()
            }
        }
    }

    fun stopVoiceDetection() {
        if (!isRecording) {
            return
        }

        isRecording = false
        _isRecordingState.value = false

        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { audioRecord ->
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.stop()
                audioRecord.release()
            }
        }
        audioRecord = null

        CoroutineScope(Dispatchers.Main).launch {
            callback?.onRecordingStopped()
        }

        Log.d(TAG, "Voice detection stopped")
    }

    private suspend fun initializeAudioRecording() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        withContext(Dispatchers.Main) {
            callback?.onRecordingStarted()
        }

        audioRecord?.startRecording()
        isRecording = true
        _isRecordingState.value = true

        Log.d(TAG, "Voice detection initialized successfully")
    }

    private suspend fun startAudioCapture() {
        val tempAudioBuffer = ByteArray(bufferSize)
        var speechDetectedLocal = false
        var lastUpdateTime = System.currentTimeMillis()

        while (isRecording) {
            val bytesRead = audioRecord?.read(tempAudioBuffer, 0, tempAudioBuffer.size) ?: 0

            if (bytesRead > 0) {
                // Add audio data to buffer for transcription
                synchronized(audioBuffer) {
                    for (i in 0 until bytesRead) {
                        audioBuffer.add(tempAudioBuffer[i])
                    }
                    // Keep buffer size manageable (max 30 seconds of audio)
                    val maxBufferSize = SAMPLE_RATE * AudioProcessor.BYTES_PER_SAMPLE * 30
                    if (audioBuffer.size > maxBufferSize) {
                        val removeCount = audioBuffer.size - maxBufferSize.toInt()
                        repeat(removeCount) {
                            audioBuffer.removeAt(0)
                        }
                    }
                }

                // Process audio data
                val audioLevelValue = calculateAudioLevel(tempAudioBuffer, bytesRead)
                _audioLevel.value = audioLevelValue

                // Simple speech detection
                val isSpeech = speechDetector.processAudio(audioLevelValue)

                if (isSpeech && !speechDetectedLocal) {
                    speechDetectedLocal = true
                    lastUpdateTime = System.currentTimeMillis()
                    _speechDetected.value = true
                    _recognizedText.value = "Listening..."

                    withContext(Dispatchers.Main) {
                        callback?.onSpeechDetected("Listening...")
                    }
                } else if (isSpeech) {
                    lastUpdateTime = System.currentTimeMillis()
                } else if (speechDetectedLocal && (System.currentTimeMillis() - lastUpdateTime > SILENCE_THRESHOLD)) {
                    // Speech ended - trigger transcription
                    speechDetectedLocal = false
                    _speechDetected.value = false
                    _recognizedText.value = "Processing speech..."

                    withContext(Dispatchers.Main) {
                        callback?.onSpeechDetected("Processing speech...")
                    }

                    // Start transcription
                    startTranscription()
                }
            }

            // Small delay to prevent CPU overload
            delay(16) // ~60Hz update rate
        }
    }

    private fun startTranscription() {
        transcriptionJob?.cancel()
        transcriptionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _transcriptionInProgress.value = true

                // Get audio data from buffer
                val audioData: ByteArray
                synchronized(audioBuffer) {
                    audioData = audioBuffer.toByteArray()
                    audioBuffer.clear()
                }

                if (audioData.isEmpty()) {
                    Log.w(TAG, "No audio data to transcribe")
                    return@launch
                }

                // Process audio for transcription
                val processedAudio = audioProcessor?.processAudioForTranscription(audioData) ?: audioData

                // Convert to base64 for API
                val audioBase64 = audioProcessor?.convertToBase64(processedAudio) ?: ""

                if (audioBase64.isNotEmpty()) {
                    // Get transcription from Gemini
                    val result = transcriptionService?.transcribeAudio(
                        audioBase64 = audioBase64,
                        audioFormat = audioProcessor?.getAudioFormatDescription() ?: "PCM 16-bit mono 16kHz"
                    )

                    result?.let { transcriptionResult ->
                        _transcriptionResult.value = transcriptionResult

                        if (transcriptionResult.success && transcriptionResult.text.isNotEmpty()) {
                            _recognizedText.value = transcriptionResult.text

                            withContext(Dispatchers.Main) {
                                callback?.onSpeechDetected(transcriptionResult.text)
                                callback?.onTranscriptionCompleted(transcriptionResult)
                            }

                            Log.d(TAG, "Transcription successful: ${transcriptionResult.text}")
                        } else {
                            val errorMsg = transcriptionResult.error ?: "Unknown error"
                            _recognizedText.value = "Transcription failed: $errorMsg"
                            Log.e(TAG, "Transcription failed: $errorMsg")
                        }
                    }
                } else {
                    _recognizedText.value = "Failed to process audio"
                    Log.e(TAG, "Failed to convert audio to base64")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _recognizedText.value = "Transcription error: ${e.message}"
            } finally {
                _transcriptionInProgress.value = false
            }
        }
    }

    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Int {
        if (length <= 0) return 0

        var sum = 0.0
        val samples = length / 2 // 16-bit audio = 2 bytes per sample

        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += sample * sample
            }
        }

        return if (samples > 0) {
            kotlin.math.sqrt(sum / samples).toInt()
        } else {
            0
        }
    }

    fun cleanup() {
        stopVoiceDetection()
        transcriptionJob?.cancel()
        transcriptionService?.cleanup()
        synchronized(audioBuffer) {
            audioBuffer.clear()
        }
        Log.d(TAG, "VoiceActivityDetector cleaned up")
    }

    // Simple voice activity detector
    private class SimpleSpeechDetector {
        private var threshold = 1000
        private var consecutiveSilence = 0
        private val maxSilenceCount = 10 // Allow some silence before ending speech

        fun processAudio(audioLevel: Int): Boolean {
            return if (audioLevel > threshold) {
                consecutiveSilence = 0
                true
            } else {
                consecutiveSilence++
                consecutiveSilence < maxSilenceCount
            }
        }

        fun reset() {
            consecutiveSilence = 0
        }

        fun setThreshold(newThreshold: Int) {
            threshold = newThreshold
        }
    }

    // Audio info methods
    fun isCurrentlyRecording(): Boolean {
        return isRecording
    }

    fun getAudioInfo(): String {
        return if (isRecording) {
            "Audio: Recording - Level: ${_audioLevel.value} - Speech: ${if (_speechDetected.value) "Detected" else "None"}"
        } else {
            "Audio: Not recording"
        }
    }

    fun configureSpeechDetection(threshold: Int = 1000) {
        speechDetector.setThreshold(threshold)
        Log.d(TAG, "Speech detection threshold set to: $threshold")
    }

    // Transcription-related methods
    fun setGeminiApiKey(apiKey: String) {
        transcriptionService?.setApiKey(apiKey)
        Log.d(TAG, "Gemini API key updated")
    }

    /**
     * Initialize API key from secure storage if available
     */
    fun initializeApiKey() {
        transcriptionService?.let { service ->
            // The service will automatically load from secure storage
            if (service.isReady()) {
                Log.d(TAG, "API key loaded from secure storage")
            } else {
                Log.w(TAG, "No API key found in secure storage")
            }
        }
    }

    fun isTranscriptionReady(): Boolean {
        return transcriptionService?.isReady() ?: false
    }

    fun getTranscriptionMethod(): String {
        return when (transcriptionService?.getCurrentMethod()) {
            // GeminiTranscriptionService.TranscriptionMethod.FIREBASE_AI -> "Firebase AI"
            GeminiTranscriptionService.TranscriptionMethod.DIRECT_API -> "Direct API"
            else -> "Unknown"
        }
    }

    fun getTranscriptionInfo(): String {
        val method = getTranscriptionMethod()
        val ready = if (isTranscriptionReady()) "Ready" else "Not Ready"
        val inProgress = if (_transcriptionInProgress.value) " (In Progress)" else ""
        return "Transcription: $method - $ready$inProgress"
    }

    fun cancelCurrentTranscription() {
        transcriptionJob?.cancel()
        _transcriptionInProgress.value = false
        Log.d(TAG, "Transcription cancelled")
    }
}