package com.camerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.camerapp.api.ApiConfig
import com.camerapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Permission launcher for camera permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true) {
            // Both permissions granted
            binding.cameraContainer.post {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.cameraContainer, CameraFragment())
                    .commitNow()
            }
        } else {
            // Permission denied
            Toast.makeText(
                this,
                "Camera and microphone permissions are required",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize API key if not already set
        initializeApiKey()

        checkPermissionsAndStartCamera()
    }

    private fun checkPermissionsAndStartCamera() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraPermission && audioPermission) {
            // Permissions already granted, start camera
            supportFragmentManager.beginTransaction()
                .replace(R.id.cameraContainer, CameraFragment())
                .commitNow()
        } else {
            // Request permissions
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    /**
     * Initialize the Gemini API key if not already configured
     */
    private fun initializeApiKey() {
        if (!ApiConfig.isGeminiApiKeySet(this)) {
            // Set the API key programmatically (for development)
            val apiKey = "AIzaSyDYdfgc0aFlowv1ankW5mRw_2fhvqPJK-I"

            if (ApiConfig.isValidApiKeyFormat(apiKey)) {
                ApiConfig.setGeminiApiKey(this, apiKey)
                Toast.makeText(this, "Gemini API key configured successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid API key format", Toast.LENGTH_SHORT).show()
            }
        } else {
            // API key already configured
            Log.d("MainActivity", "Gemini API key already configured")
        }
    }
}