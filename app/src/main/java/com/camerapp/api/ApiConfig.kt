package com.camerapp.api

import android.content.Context
import android.util.Log

/**
 * API configuration management for the Camera App
 *
 * This class handles secure storage and retrieval of API keys and other configuration.
 * In a production app, you should use Android Keystore or encrypted SharedPreferences.
 */
object ApiConfig {

    private const val TAG = "ApiConfig"
    private const val PREFS_NAME = "api_config"
    private const val GEMINI_API_KEY_PREF = "gemini_api_key"

    /**
     * Store the Gemini API key securely
     * Note: In production, use Android Keystore for better security
     */
    fun setGeminiApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(GEMINI_API_KEY_PREF, apiKey)
            .apply()
        Log.d(TAG, "Gemini API key stored successfully")
    }

    /**
     * Get the stored Gemini API key
     */
    fun getGeminiApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(GEMINI_API_KEY_PREF, null)
    }

    /**
     * Check if Gemini API key is configured
     */
    fun isGeminiApiKeySet(context: Context): Boolean {
        return !getGeminiApiKey(context).isNullOrEmpty()
    }

    /**
     * Clear the stored API key
     */
    fun clearGeminiApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(GEMINI_API_KEY_PREF)
            .apply()
        Log.d(TAG, "Gemini API key cleared")
    }

    /**
     * Validate API key format (basic validation)
     */
    fun isValidApiKeyFormat(apiKey: String): Boolean {
        return apiKey.startsWith("AIza") && apiKey.length >= 39
    }
}