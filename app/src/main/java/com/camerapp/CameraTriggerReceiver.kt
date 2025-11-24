package com.camerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CameraTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CameraTrigger", "Received broadcast: ${intent.action}")

        when (intent.action) {
            "com.camerapp.CAPTURE_PHOTO" -> {
                // Trigger photo capture
                val captureIntent = Intent(context, CameraTriggerService::class.java).apply {
                    action = "CAPTURE_PHOTO"
                }
                context.startService(captureIntent)
            }

            "com.camerapp.START_AUTO_CAPTURE" -> {
                // Start auto-capture with interval
                val interval = intent.getLongExtra("interval", 30000) // Default 30 seconds
                val captureIntent = Intent(context, CameraTriggerService::class.java).apply {
                    action = "START_AUTO_CAPTURE"
                    putExtra("interval", interval)
                }
                context.startService(captureIntent)
            }

            "com.camerapp.STOP_AUTO_CAPTURE" -> {
                // Stop auto-capture
                val captureIntent = Intent(context, CameraTriggerService::class.java).apply {
                    action = "STOP_AUTO_CAPTURE"
                }
                context.startService(captureIntent)
            }

            "com.camerapp.SET_CAMERA_CONFIG" -> {
                // Configure camera settings
                val resolution = intent.getStringExtra("resolution")
                val quality = intent.getIntExtra("quality", 95)
                val captureMode = intent.getStringExtra("capture_mode")

                val configIntent = Intent(context, CameraTriggerService::class.java).apply {
                    action = "SET_CAMERA_CONFIG"
                    putExtra("resolution", resolution)
                    putExtra("quality", quality)
                    putExtra("capture_mode", captureMode)
                }
                context.startService(configIntent)
            }
        }
    }
}