# Headless Camera Mode for INMO IMA301 Smart Glasses

Complete guide for implementing headless camera functionality in your custom camera app, optimized for smart glasses usage.

## 📋 Overview

Headless camera mode allows capturing photos without displaying a camera preview, making it perfect for smart glasses where you see directly through the device.

## 🎯 Benefits for Smart Glasses

### ✅ Perfect Fit
- **No preview needed**: You see directly through glasses
- **Minimal interface**: Just trigger capture programmatically
- **Battery efficient**: No preview rendering overhead
- **Instant capture**: No UI lag from preview updates
- **Reduced memory usage**: No preview surface required

### ✅ Use Cases
- **Automatic photo capture**: Based on triggers (voice, gesture, timer)
- **Continuous recording**: Time-lapse photography
- **Background monitoring**: Security or documentation
- **API integration**: Trigger from other apps/services
- **Voice command photography**: Hands-free operation

## 🔧 Implementation Approaches

### 1. CameraX Headless Mode (Recommended)

Remove the PreviewView completely and bind only the ImageCapture use case:

```kotlin
// Remove PreviewView entirely - headless camera
imageCapture = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .setTargetResolution(Size(1920, 1080))
    .setJpegQuality(95)
    .build()

// No preview surface needed!
camera = cameraProvider?.bindToLifecycle(
    this, cameraSelector, imageCapture  // Only imageCapture, no preview
)
```

### 2. Background Service Headless

Create a service that runs camera in background:

```kotlin
class HeadlessCameraService : Service() {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCameraHeadless()
        return START_STICKY
    }

    private fun startCameraHeadless() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            camera = cameraProvider?.bindToLifecycle(
                LifecycleService(), cameraSelector, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }
}
```

### 3. Camera2 API Direct Headless

Lower-level API for more control:

```kotlin
val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)

private val cameraStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
        // Create capture session without preview
        createCameraCaptureSession(camera)
    }
}

private fun createCameraCaptureSession(camera: CameraDevice) {
    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
    camera.createCaptureSession(
        listOf(imageReader.surface),
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                // Ready to capture headlessly
            }
        },
        backgroundHandler
    )
}
```

### 4. Termux Command Line Headless

Simplest approach using existing tools:

```bash
# Using Termux camera tools without GUI
termux-camera-photo -c 0 /sdcard/photo.jpg
termux-camera-photo -c 0 --jpeg-quality 100 /sdcard/high_quality.jpg
```

## 🚀 Implementation Steps for Your App

### Step 1: Modify CameraFragment.kt

Remove PreviewView and related code:

```kotlin
// REMOVE this section completely:
val preview = Preview.Builder()
    .build()
    .also {
        it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
}

// KEEP only this:
imageCapture = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    .setTargetResolution(Size(1920, 1080))  // High quality
    .setJpegQuality(95)
    .build()

// MODIFY binding to use only ImageCapture:
camera = cameraProvider?.bindToLifecycle(
    this, cameraSelector, imageCapture  // No preview, only capture
)
```

### Step 2: Update Layout

Remove PreviewView from `fragment_camera.xml`:

```xml
<!-- REMOVE this entirely -->
<androidx.camera.view.PreviewView
    android:id="@+id/viewFinder"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent" />

<!-- KEEP only controls -->
<LinearLayout
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_marginBottom="48dp"
    android:gravity="center"
    android:orientation="horizontal"
    android:paddingHorizontal="32dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent">

    <!-- Capture Button -->
    <ImageButton
        android:id="@+id/captureButton"
        android:layout_width="96dp"
        android:layout_height="96dp"
        android:background="@drawable/capture_button_background"
        android:contentDescription="Capture photo"
        android:src="@drawable/ic_camera"
        android:tint="@color/white" />

</LinearLayout>
```

### Step 3: Simplified Camera Binding

```kotlin
try {
    cameraProvider?.unbindAll()
    camera = cameraProvider?.bindToLifecycle(
        this, cameraSelector, imageCapture  // Headless: no preview
    )
} catch (exc: Exception) {
    Log.e("CameraX", "Headless camera binding failed", exc)
}
```

## 🎮 Trigger Methods

### Voice Commands

**Android Intent Integration:**
```kotlin
private fun setupVoiceTrigger() {
    // Listen for voice commands
    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches?.contains("take picture") == true) {
                capturePhotoHeadless()
            }
        }
    })
    speechRecognizer.startListening(intent)
}
```

**Google Assistant Integration:**
```xml
<!-- AndroidManifest.xml -->
<intent-filter>
    <action android:name="android.intent.action.ASSIST" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

### Button/Gesture Triggers

**Volume Button:**
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            capturePhotoHeadless()
            return true
        }
        KeyEvent.KEYCODE_CAMERA -> {
            capturePhotoHeadless()
            return true
        }
    }
    return super.onKeyDown(keyCode, event)
}
```

**Custom Gesture:**
```kotlin
private fun setupGestureDetector() {
    val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            capturePhotoHeadless()
            return true
        }
    })

    binding.root.setOnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
    }
}
```

### Timer-Based Auto-Capture

```kotlin
private fun startAutoCapture(intervalMs: Long) {
    val handler = Handler(Looper.getMainLooper())
    val captureRunnable = object : Runnable {
        override fun run() {
            capturePhotoHeadless()
            handler.postDelayed(this, intervalMs)
        }
    }
    handler.postDelayed(captureRunnable, intervalMs)
}

// Usage:
startAutoCapture(30000) // Auto-capture every 30 seconds
```

### API Integration

**Broadcast Receiver:**
```kotlin
class CameraTrigger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.camerapp.CAPTURE_PHOTO" -> {
                capturePhotoHeadless()
            }
            "com.camerapp.START_AUTO_CAPTURE" -> {
                val interval = intent.getLongExtra("interval", 30000)
                startAutoCapture(interval)
            }
        }
    }
}

// Register in manifest:
<receiver android:name=".CameraTrigger">
    <intent-filter>
        <action android:name="com.camerapp.CAPTURE_PHOTO" />
    </intent-filter>
</receiver>
```

**Intent Service:**
```kotlin
// Other apps can trigger camera:
val intent = Intent("com.camerapp.CAPTURE_PHOTO")
sendBroadcast(intent)

// With configuration:
val intent = Intent("com.camerapp.START_AUTO_CAPTURE")
intent.putExtra("interval", 15000)  // 15 second interval
sendBroadcast(intent)
```

## 📁 Configuration Options

### Resolution Settings

```kotlin
// Common resolutions for glasses:
.setTargetResolution(Size(3840, 2160))  // 4K Ultra HD
.setTargetResolution(Size(1920, 1080))  // Full HD
.setTargetResolution(Size(1280, 720))    // HD
.setTargetResolution(Size(640, 480))     // Default
```

### Quality Settings

```kotlin
.setJpegQuality(100)  // Highest quality
.setJpegQuality(95)   // High quality (default)
.setJpegQuality(85)   // Medium quality
.setJpegQuality(70)   // Low quality (smaller files)
```

### Capture Mode

```kotlin
.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)  // Speed priority
.setCaptureMode(ImageCapture.CAPTURE_MODE_QUALITY)           // Quality priority
```

### Aspect Ratio

```kotlin
.setTargetAspectRatio(AspectRatio.RATIO_16_9)  // Widescreen
.setTargetAspectRatio(AspectRatio.RATIO_4_3)   // Standard
.setTargetAspectRatio(AspectRatio.RATIO_1_1)   // Square
```

## 📱 File Size Impact

| Resolution | Quality | File Size | Storage Impact |
|------------|--------|------------|----------------|
| 640x480    | 95%    | ~400KB     | Minimal |
| 1280x720   | 95%    | ~1MB       | Low |
| 1920x1080  | 95%    | ~2MB       | Medium |
| 1920x1080  | 100%   | ~3MB       | High |
| 3840x2160  | 95%    | ~8MB       | Very High |

## 🔧 Service Implementation (Advanced)

### Foreground Service with Notification

```kotlin
class HeadlessCameraService : Service() {
    private val CHANNEL_ID = "headless_camera_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, createNotification())
        startCameraHeadless()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Headless Camera",
            NotificationManager.IMPORTANCE_DEFAULT,
            false
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel, null)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Headless Camera Active")
            .setContentText("Camera running in background")
            .setSmallIcon(R.drawable.ic_camera)
            .setContentIntent(createPendingIntent())
            .build()
    }
}
```

## 🎯 Use Case Examples

### 1. Time-Lapse Photography

```kotlin
class TimeLapseCamera {
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())

    fun startTimeLapse(intervalMs: Long) {
        isRunning = true
        captureInterval(intervalMs)
    }

    private fun captureInterval(intervalMs: Long) {
        if (isRunning) {
            capturePhotoHeadless()
            handler.postDelayed({
                captureInterval(intervalMs)
            }, intervalMs)
        }
    }
}
```

### 2. Security Monitoring

```kotlin
class SecurityCamera : BroadcastReceiver() {
    private val motionDetector = MotionDetector()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                capturePhotoHeadless()  // Photo when user unlocks
            }
            "android.intent.action.SCREEN_ON" -> {
                capturePhotoHeadless()  // Photo when screen turns on
            }
        }
    }
}
```

### 3. Voice Trigger System

```kotlin
class VoiceTriggerCamera {
    private val triggerPhrases = arrayOf(
        "take picture", "capture", "photo", "snap", "shot"
    )

    fun setupVoiceRecognition() {
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { match ->
                    if (triggerPhrases.any { match.lowercase().contains(it) }) {
                        capturePhotoHeadless()
                        speak("Photo captured")
                    }
                }
            }
        })

        speechRecognizer.startListening(recognizerIntent)
    }

    private fun speak(text: String) {
        val tts = TextToSpeech(this, TextToSpeech.OnInitListener {
        })
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }
}
```

## 🚀 Quick Start Implementation

### Step 1: Minimal Headless Setup

1. **Remove PreviewView** from `fragment_camera.xml`
2. **Modify CameraFragment.kt** to use only ImageCapture
3. **Add trigger button** (voice, gesture, timer)
4. **Test headless capture**

### Step 2: Add Voice Trigger

1. **Add voice recognition** dependencies
2. **Implement speech recognition**
3. **Add voice command** to trigger capture
4. **Test voice triggers**

### Step 3: Add Auto-Capture

1. **Implement timer system**
2. **Add interval configuration**
3. **Create notification** for status
4. **Test auto-capture** functionality

## 🔍 Testing Headless Mode

### Manual Testing
```bash
# Test basic functionality
adb shell am start -n com.camerapp/.MainActivity

# Test voice trigger
adb shell input tap 500 900  # Tap capture button

# Verify photo capture
adb shell ls /sdcard/Pictures/
```

### Automated Testing
```kotlin
@Test
fun testHeadlessCapture() {
    // Start activity
    ActivityScenario.launch(MainActivity::class.java)

    // Verify no preview surface exists
    onView(withId(R.id.viewFinder)).check(doesNotExist())

    // Test capture button
    onView(withId(R.id.captureButton)).perform(click())

    // Verify photo created
    val photoFiles = File("/sdcard/Pictures/").listFiles()
    assertTrue(photoFiles.isNotEmpty())
}
```

### Performance Monitoring
```kotlin
private fun monitorHeadlessPerformance() {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()

    Log.d("HeadlessCamera", "Memory used: ${usedMemory / 1024 / 1024}MB")
    Log.d("HeadlessCamera", "Available: ${runtime.freeMemory() / 1024 / 1024}MB")
}
```

## 📝 Troubleshooting

### Common Issues

**Camera Not Working in Headless Mode:**
```kotlin
// Make sure you're not trying to bind preview surface
camera = cameraProvider?.bindToLifecycle(
    this, cameraSelector, imageCapture  // No preview
)

// Not this (requires preview):
camera = cameraProvider?.bindToLifecycle(
    this, cameraSelector, preview, imageCapture  // Requires preview
)
```

**Permission Issues:**
```xml
<!-- Make sure you have camera permission -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

**Background Restrictions:**
```kotlin
// Use foreground service for long-running background camera
class HeadlessCameraService : Service() {
    override fun onStartCommand(...) {
        startForeground(NOTIFICATION_ID, createNotification())
        // Camera code here
    }
}
```

### Debug Logging

```kotlin
private fun debugHeadlessSetup() {
    Log.d("HeadlessCamera", "Preview surface: REMOVED")
    Log.d("HeadlessCamera", "ImageCapture: ACTIVE")
    Log.d("HeadlessCamera", "Camera selector: ${cameraSelector}")
    Log.d("HeadlessCamera", "Camera: $camera")
}
```

## 🎯 Implementation Priority

### **High Priority** (Easy Implementation)
1. Remove PreviewView from layout ✅
2. Modify CameraX binding ✅
3. Add simple trigger button ✅
4. Test basic headless capture ✅

### **Medium Priority** (Enhanced Features)
1. Add voice command triggers
2. Implement auto-capture timer
3. Add background service support
4. Create API endpoints

### **Low Priority** (Advanced Features)
1. Custom Camera2 implementation
2. Advanced image processing
3. Cloud storage integration
4. AI-powered photo analysis

---

This documentation provides everything needed to implement headless camera mode for your INMO IMA301 smart glasses, from basic setup to advanced automation features.