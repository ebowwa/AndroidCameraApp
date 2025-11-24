package com.camerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.lifecycle.LifecycleOwner
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraTriggerService : Service() {

    companion object {
        private const val CHANNEL_ID = "camera_trigger_channel"
        private const val NOTIFICATION_ID = 1001

        fun createImageFile(context: Context): File {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = File(context.getExternalFilesDir(null), "Pictures")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            return File(storageDir, "API_${timeStamp}.jpg")
        }
    }

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var autoCaptureJob: Job? = null
    private var handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CAPTURE_PHOTO" -> {
                capturePhoto()
            }
            "START_AUTO_CAPTURE" -> {
                val interval = intent.getLongExtra("interval", 30000)
                startAutoCapture(interval)
            }
            "STOP_AUTO_CAPTURE" -> {
                stopAutoCapture()
            }
            "SET_CAMERA_CONFIG" -> {
                updateCameraConfig(
                    intent.getStringExtra("resolution"),
                    intent.getIntExtra("quality", 95),
                    intent.getStringExtra("capture_mode")
                )
            }
        }
        return START_STICKY
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(1920, 1080))
                .setJpegQuality(95)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, imageCapture
                )
                Log.d("CameraTrigger", "Camera initialized successfully in service")
            } catch (exc: Exception) {
                Log.e("CameraTrigger", "Camera binding failed in service", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = CameraTriggerService.createImageFile(this)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraTrigger", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraTrigger", "Photo saved via API: ${output.savedUri}")
                    // Optionally add to gallery here
                }
            }
        )
    }

    private fun startAutoCapture(intervalMs: Long) {
        stopAutoCapture() // Stop any existing auto-capture

        autoCaptureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                withContext(Dispatchers.Main) {
                    capturePhoto()
                }
                delay(intervalMs)
            }
        }

        Log.d("CameraTrigger", "Auto-capture started with interval: ${intervalMs}ms")
        updateNotification("Auto-capture active (${intervalMs / 1000}s interval)")
    }

    private fun stopAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        Log.d("CameraTrigger", "Auto-capture stopped")
        updateNotification("Camera trigger service active")
    }

    private fun updateCameraConfig(resolution: String?, quality: Int, captureMode: String?) {
        // Reinitialize camera with new config
        initializeCamera()

        Log.d("CameraTrigger", "Camera config updated - Resolution: $resolution, Quality: $quality, Mode: $captureMode")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Camera Trigger Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background camera trigger service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Trigger Service")
            .setContentText("Camera API integration active")
            .setSmallIcon(R.drawable.ic_camera)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Trigger Service")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_camera)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAutoCapture()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}