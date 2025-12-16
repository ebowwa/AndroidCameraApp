package com.camerapp.audio

import android.content.Context
import android.util.Log
// Firebase imports commented out until Firebase is properly configured
// import com.google.firebase.Firebase
// import com.google.firebase.aiGenerativeBackend
// import com.google.firebase.ai.generateContent
import com.camerapp.api.ApiConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiTranscriptionService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiTranscriptionService"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Firebase AI model (commented out until Firebase is properly configured)
    // private var firebaseModel: com.google.firebase.vertexai.type.GenerativeModel? = null

    enum class TranscriptionMethod {
        // FIREBASE_AI,   // Preferred: Uses Firebase AI Logic - disabled for now
        DIRECT_API     // Direct REST API calls
    }

    private var currentMethod: TranscriptionMethod = TranscriptionMethod.DIRECT_API

    init {
        // initializeFirebaseModel() - commented out until Firebase is configured
        Log.d(TAG, "GeminiTranscriptionService initialized with Direct API method")
    }

    /*
    private fun initializeFirebaseModel() {
        try {
            firebaseModel = Firebase.ai(
                backend = GenerativeBackend.googleAI()
            ).generativeModel("gemini-2.5-flash")
            Log.d(TAG, "Firebase AI model initialized successfully")
            currentMethod = TranscriptionMethod.FIREBASE_AI
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI model", e)
            Log.w(TAG, "Falling back to direct API calls")
            currentMethod = TranscriptionMethod.DIRECT_API
        }
    }
    */

    /**
     * Transcribe audio data using Gemini API
     * @param audioBase64 Base64-encoded audio data
     * @param audioFormat Format description (e.g., "PCM 16-bit mono 16kHz")
     * @return Transcription result or error message
     */
    suspend fun transcribeAudio(
        audioBase64: String,
        audioFormat: String = "PCM 16-bit mono 16kHz"
    ): TranscriptionResult = withContext(Dispatchers.IO) {

        // Firebase AI method disabled until configured
        transcribeWithDirectAPI(audioBase64, audioFormat)

        /*
        when (currentMethod) {
            TranscriptionMethod.FIREBASE_AI -> {
                transcribeWithFirebaseAI(audioBase64, audioFormat)
            }
            TranscriptionMethod.DIRECT_API -> {
                transcribeWithDirectAPI(audioBase64, audioFormat)
            }
        }
        */
    }

    /*
    private suspend fun transcribeWithFirebaseAI(
        audioBase64: String,
        audioFormat: String
    ): TranscriptionResult {
        return try {
            val model = firebaseModel ?: throw IllegalStateException("Firebase model not initialized")

            val prompt = buildString {
                appendLine("You are a transcription AI. Please transcribe the following audio data.")
                appendLine("Audio format: $audioFormat")
                appendLine("The audio is base64 encoded: $audioBase64")
                appendLine("\nPlease provide the transcription. If the audio is unclear or contains no speech, indicate that.")
                appendLine("Respond with only the transcription text, no additional commentary.")
            }

            val response = model.generateContent(prompt)
            val transcription = response.text?.trim() ?: ""

            TranscriptionResult(
                success = true,
                text = transcription,
                confidence = if (transcription.isNotEmpty()) 0.8f else 0.0f,
                method = "Firebase AI",
                processingTime = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI transcription failed", e)
            // Fall back to direct API
            transcribeWithDirectAPI(audioBase64, audioFormat)
        }
    }
    */

    private suspend fun transcribeWithDirectAPI(
        audioBase64: String,
        audioFormat: String
    ): TranscriptionResult {
        return try {
            val apiKey = ApiConfig.getGeminiApiKey(context)
            if (apiKey == null) {
                return TranscriptionResult(
                    success = false,
                    error = "Gemini API key not configured. Please set API key in settings.",
                    method = "Direct API"
                )
            }

            val requestBody = buildRequestBody(audioBase64, audioFormat)

            val request = Request.Builder()
                .url("$GEMINI_API_URL?key=$apiKey")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val processingTime = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            parseDirectAPIResponse(responseBody, processingTime)

        } catch (e: IOException) {
            Log.e(TAG, "Direct API transcription failed", e)
            TranscriptionResult(
                success = false,
                error = "Network error: ${e.message}",
                method = "Direct API"
            )
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse API response", e)
            TranscriptionResult(
                success = false,
                error = "Invalid response format: ${e.message}",
                method = "Direct API"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during transcription", e)
            TranscriptionResult(
                success = false,
                error = "Unexpected error: ${e.message}",
                method = "Direct API"
            )
        }
    }

    private fun buildRequestBody(audioBase64: String, audioFormat: String): RequestBody {
        val prompt = buildString {
            appendLine("You are a transcription AI. Please transcribe the following audio data.")
            appendLine("Audio format: $audioFormat")
            appendLine("The audio is base64 encoded: $audioBase64")
            appendLine("\nPlease provide the transcription. If the audio is unclear or contains no speech, indicate that.")
            appendLine("Respond with only the transcription text, no additional commentary.")
        }

        val jsonRequest = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,
                "topK" to 32,
                "topP" to 0.95,
                "maxOutputTokens" to 1024
            )
        )

        val jsonString = gson.toJson(jsonRequest)
        return jsonString.toRequestBody("application/json".toMediaType())
    }

    private fun parseDirectAPIResponse(responseBody: String, processingTime: Long): TranscriptionResult {
        return try {
            val response = gson.fromJson(responseBody, GeminiAPIResponse::class.java)

            if (response.candidates?.isNotEmpty() == true) {
                val text = response.candidates[0].content?.parts?.get(0)?.text?.trim() ?: ""

                TranscriptionResult(
                    success = true,
                    text = text,
                    confidence = if (text.isNotEmpty()) 0.8f else 0.0f,
                    method = "Direct API",
                    processingTime = processingTime
                )
            } else {
                TranscriptionResult(
                    success = false,
                    error = "No transcription candidates in response",
                    method = "Direct API"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse direct API response: $responseBody", e)
            TranscriptionResult(
                success = false,
                error = "Failed to parse response: ${e.message}",
                method = "Direct API"
            )
        }
    }

    /**
     * Set the Gemini API key for direct API calls
     */
    fun setApiKey(apiKey: String) {
        ApiConfig.setGeminiApiKey(context, apiKey)
        Log.d(TAG, "API key updated securely")
    }

    /**
     * Get current transcription method
     */
    fun getCurrentMethod(): TranscriptionMethod = currentMethod

    /**
     * Check if service is ready for transcription
     */
    fun isReady(): Boolean {
        return when (currentMethod) {
            // TranscriptionMethod.FIREBASE_AI -> firebaseModel != null
            TranscriptionMethod.DIRECT_API -> ApiConfig.isGeminiApiKeySet(context)
        }
    }

    fun cleanup() {
        // Cleanup resources if needed
        Log.d(TAG, "GeminiTranscriptionService cleaned up")
    }
}

// Data classes for API response parsing
data class TranscriptionResult(
    val success: Boolean,
    val text: String = "",
    val confidence: Float = 0.0f,
    val method: String = "",
    val processingTime: Long = 0,
    val error: String? = null
)

data class GeminiAPIResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?,
    val finishReason: String?
)

data class Content(
    val parts: List<Part>?
)

data class Part(
    val text: String?
)