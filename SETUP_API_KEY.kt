import android.content.Context
import com.camerapp.api.ApiConfig

/**
 * Simple utility to set the Gemini API key programmatically
 *
 * USAGE: Replace the API key below with your actual key and run this once
 * to initialize the secure storage.
 */

fun setupGeminiApiKey(context: Context) {
    val apiKey = "YOUR_GEMINI_API_KEY_HERE" // Replace with your actual API key

    // Validate and store the API key
    if (ApiConfig.isValidApiKeyFormat(apiKey)) {
        ApiConfig.setGeminiApiKey(context, apiKey)
        println("✅ Gemini API key has been successfully configured!")
        println("The key is now stored securely and will be used by the transcription service.")
    } else {
        println("❌ Invalid API key format")
    }
}

/**
 * Check if the API key is already configured
 */
fun checkApiKeyStatus(context: Context) {
    val isConfigured = ApiConfig.isGeminiApiKeySet(context)
    val status = if (isConfigured) {
        "✅ Gemini API key is configured and ready to use"
    } else {
        "❌ Gemini API key is not configured. Run setupGeminiApiKey() first."
    }
    println(status)
}

/**
 * Example usage:
 *
 * // In your MainActivity or Application class:
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *
 *     // Setup the API key (run this once)
 *     setupGeminiApiKey(this)
 *
 *     // Check status
 *     checkApiKeyStatus(this)
 * }
 */