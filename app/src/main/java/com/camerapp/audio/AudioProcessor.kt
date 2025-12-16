package com.camerapp.audio

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioProcessor {

    companion object {
        private const val TAG = "AudioProcessor"

        // Audio format constants
        const val SAMPLE_RATE = 16000
        const val CHANNEL_COUNT = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    }

    /**
     * Convert raw PCM audio data to base64 string for API transmission
     */
    suspend fun convertToBase64(audioData: ByteArray): String = withContext(Dispatchers.Default) {
        try {
            // For now, we'll send the raw PCM data as base64
            // In a production app, you might want to convert to WAV or another format
            Base64.encodeToString(audioData, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert audio to base64", e)
            ""
        }
    }

    /**
     * Convert raw PCM byte array to WAV format byte array
     */
    suspend fun convertToWav(audioData: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray = withContext(Dispatchers.Default) {
        try {
            val outputStream = ByteArrayOutputStream()
            val totalLength = 44 + audioData.size // WAV header is 44 bytes

            // WAV Header
            outputStream.write("RIFF".toByteArray())                           // ChunkID
            outputStream.write(intToByteArray(totalLength - 8))                 // ChunkSize
            outputStream.write("WAVE".toByteArray())                           // Format

            // Subchunk1 (fmt)
            outputStream.write("fmt ".toByteArray())                           // Subchunk1ID
            outputStream.write(intToByteArray(16))                              // Subchunk1Size (16 for PCM)
            outputStream.write(shortToByteArray(1))                            // AudioFormat (1 for PCM)
            outputStream.write(shortToByteArray(CHANNEL_COUNT))                 // NumChannels
            outputStream.write(intToByteArray(sampleRate))                      // SampleRate
            outputStream.write(intToByteArray(sampleRate * CHANNEL_COUNT * BYTES_PER_SAMPLE)) // ByteRate
            outputStream.write(shortToByteArray(CHANNEL_COUNT * BYTES_PER_SAMPLE)) // BlockAlign
            outputStream.write(shortToByteArray(BITS_PER_SAMPLE))              // BitsPerSample

            // Subchunk2 (data)
            outputStream.write("data".toByteArray())                           // Subchunk2ID
            outputStream.write(intToByteArray(audioData.size))                  // Subchunk2Size
            outputStream.write(audioData)                                       // Actual audio data

            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert audio to WAV", e)
            audioData // Return original data on failure
        }
    }

    /**
     * Convert 16-bit PCM byte array to FloatArray for audio processing
     */
    suspend fun convertToFloatArray(audioData: ByteArray): FloatArray = withContext(Dispatchers.Default) {
        val samples = audioData.size / BYTES_PER_SAMPLE
        val floatArray = FloatArray(samples)
        val byteBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        repeat(samples) { i ->
            floatArray[i] = byteBuffer.short.toInt() / 32768.0f
        }

        floatArray
    }

    /**
     * Apply simple voice activity detection to filter out silence
     */
    suspend fun filterSilence(audioData: FloatArray, threshold: Float = 0.02f): FloatArray = withContext(Dispatchers.Default) {
        val speechSegments = mutableListOf<Float>()
        val windowSize = 1024
        val hopSize = 512

        var i = 0
        while (i + windowSize < audioData.size) {
            val window = audioData.sliceArray(i until i + windowSize)
            val energy = calculateEnergy(window)

            if (energy > threshold) {
                speechSegments.addAll(window.asList())
            }

            i += hopSize
        }

        // Handle remaining samples
        if (i < audioData.size) {
            val remainingWindow = audioData.sliceArray(i until audioData.size)
            val energy = calculateEnergy(remainingWindow)
            if (energy > threshold) {
                speechSegments.addAll(remainingWindow.asList())
            }
        }

        speechSegments.toFloatArray()
    }

    /**
     * Calculate energy (RMS) of audio samples
     */
    private fun calculateEnergy(samples: FloatArray): Float {
        var sum = 0.0f
        for (sample in samples) {
            sum += sample * sample
        }
        return kotlin.math.sqrt(sum / samples.size)
    }

    /**
     * Apply simple noise reduction
     */
    suspend fun reduceNoise(audioData: FloatArray, noiseThreshold: Float = 0.01f): FloatArray = withContext(Dispatchers.Default) {
        audioData.map { sample ->
            if (kotlin.math.abs(sample) < noiseThreshold) {
                0.0f
            } else {
                sample
            }
        }.toFloatArray()
    }

    /**
     * Normalize audio volume
     */
    suspend fun normalizeAudio(audioData: FloatArray, targetLevel: Float = 0.8f): FloatArray = withContext(Dispatchers.Default) {
        val maxAmplitude = audioData.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        if (maxAmplitude == 0f) return@withContext audioData

        val normalizationFactor = targetLevel / maxAmplitude
        audioData.map { it * normalizationFactor }.toFloatArray()
    }

    /**
     * Convert FloatArray back to 16-bit PCM byte array
     */
    suspend fun convertToByteArray(floatArray: FloatArray): ByteArray = withContext(Dispatchers.Default) {
        val byteBuffer = ByteBuffer.allocate(floatArray.size * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)

        for (sample in floatArray) {
            val clampedSample = sample.coerceIn(-1.0f, 1.0f)
            val shortSample = (clampedSample * 32767).toInt().toShort()
            byteBuffer.putShort(shortSample)
        }

        byteBuffer.array()
    }

    /**
     * Process audio data for transcription - combines several preprocessing steps
     */
    suspend fun processAudioForTranscription(
        audioData: ByteArray,
        applyNoiseReduction: Boolean = true,
        applyNormalization: Boolean = true,
        applySilenceFiltering: Boolean = true
    ): ByteArray = withContext(Dispatchers.Default) {
        try {
            var processedAudio = audioData

            if (applyNoiseReduction || applyNormalization || applySilenceFiltering) {
                // Convert to float for processing
                val floatArray = convertToFloatArray(processedAudio)
                var processedFloats = floatArray

                // Apply noise reduction
                if (applyNoiseReduction) {
                    processedFloats = reduceNoise(processedFloats)
                }

                // Apply silence filtering
                if (applySilenceFiltering) {
                    processedFloats = filterSilence(processedFloats)
                }

                // Apply normalization
                if (applyNormalization && processedFloats.isNotEmpty()) {
                    processedFloats = normalizeAudio(processedFloats)
                }

                // Convert back to byte array
                processedAudio = convertToByteArray(processedFloats)
            }

            processedAudio
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process audio", e)
            audioData // Return original audio on failure
        }
    }

    // Helper methods for WAV format conversion
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Get audio format description string
     */
    fun getAudioFormatDescription(): String {
        return "PCM ${BITS_PER_SAMPLE}-bit mono ${SAMPLE_RATE}Hz"
    }
}