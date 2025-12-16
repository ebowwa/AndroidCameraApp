package com.camerapp.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.camerapp.R
import com.camerapp.api.ApiConfig
import com.camerapp.audio.GeminiTranscriptionService
import com.camerapp.audio.VoiceActivityDetector

/**
 * Helper class for API configuration UI
 */
object ApiConfigScreen {

    /**
     * Show API key configuration dialog
     */
    fun showApiKeyDialog(context: Context, fragment: Fragment? = null) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Configure Gemini API")

        // Create input field
        val input = EditText(context)
        input.hint = "Enter Gemini API Key"
        input.setText(ApiConfig.getGeminiApiKey(context))
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("Save") { dialog, _ ->
            val apiKey = input.text.toString().trim()

            if (apiKey.isEmpty()) {
                showErrorMessage(context, "API key cannot be empty")
                return@setPositiveButton
            }

            if (!ApiConfig.isValidApiKeyFormat(apiKey)) {
                showErrorMessage(context, "Invalid API key format. Gemini keys start with 'AIza'")
                return@setPositiveButton
            }

            // Save the API key
            ApiConfig.setGeminiApiKey(context, apiKey)

            // Update any running VoiceActivityDetector instances
            fragment?.let { frag ->
                if (frag is VoiceActivityDetector.VoiceActivityDetectorCallback) {
                    // You might want to restart the voice detector with the new key
                    showSuccessMessage(context, "API key saved successfully!")
                }
            }

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.setNeutralButton("Test") { _, _ ->
            val apiKey = input.text.toString().trim()
            if (ApiConfig.isValidApiKeyFormat(apiKey)) {
                testApiKey(context, apiKey)
            } else {
                showErrorMessage(context, "Please enter a valid API key first")
            }
        }

        builder.show()
    }

    /**
     * Show API key status
     */
    fun showApiKeyStatus(context: Context) {
        val isConfigured = ApiConfig.isGeminiApiKeySet(context)
        val message = if (isConfigured) {
            "✅ Gemini API key is configured"
        } else {
            "❌ Gemini API key is not configured"
        }

        AlertDialog.Builder(context)
            .setTitle("API Status")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNegativeButton(if (isConfigured) "Clear Key" else "Set Key") { _, _ ->
                if (isConfigured) {
                    clearApiKey(context)
                } else {
                    showApiKeyDialog(context)
                }
            }
            .show()
    }

    /**
     * Test the API key with a simple validation
     */
    private fun testApiKey(context: Context, apiKey: String) {
        // For now, just validate the format
        // In a real implementation, you could make a test API call
        val isValid = ApiConfig.isValidApiKeyFormat(apiKey)

        val message = if (isValid) {
            "✅ API key format appears valid\n\nKey: ${apiKey.take(12)}...${apiKey.takeLast(4)}"
        } else {
            "❌ Invalid API key format"
        }

        AlertDialog.Builder(context)
            .setTitle("API Key Test")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Save Key") { _, _ ->
                ApiConfig.setGeminiApiKey(context, apiKey)
                showSuccessMessage(context, "API key saved!")
            }
            .show()
    }

    /**
     * Clear stored API key
     */
    private fun clearApiKey(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Clear API Key")
            .setMessage("Are you sure you want to remove the stored Gemini API key?")
            .setPositiveButton("Clear") { _, _ ->
                ApiConfig.clearGeminiApiKey(context)
                showSuccessMessage(context, "API key cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSuccessMessage(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showErrorMessage(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}