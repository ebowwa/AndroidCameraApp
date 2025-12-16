# Gemini API Integration Setup

This document explains how to set up and use the Gemini API integration for speech transcription in the Camera App.

## Current Implementation

The app now includes Gemini API transcription capabilities with the following components:

### New Files Created
- `GeminiTranscriptionService.kt` - Handles Gemini API communication
- `AudioProcessor.kt` - Audio preprocessing and format conversion
- Updated `VoiceActivityDetector.kt` - Integrated with transcription service

### Features
- **Automatic Speech Detection**: Listens for speech and automatically triggers transcription
- **Audio Preprocessing**: Noise reduction, silence filtering, and normalization
- **Dual API Support**: Firebase AI (disabled for now) and direct REST API calls
- **Real-time Feedback**: UI updates during listening, processing, and transcription

## Setup Instructions

### 1. Get Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com/)
2. Create a new API key or use an existing one
3. Copy the API key

### 2. Configure API Key in App

**Option A: Runtime Configuration (Recommended)**
```kotlin
// In your Activity or Fragment
val voiceDetector = VoiceActivityDetector(context)
voiceDetector.setGeminiApiKey("YOUR_API_KEY_HERE")
```

**Option B: Hardcoded (Development Only)**
Edit `GeminiTranscriptionService.kt`:
```kotlin
private const val GEMINI_API_KEY = "YOUR_ACTUAL_API_KEY_HERE"
```

### 3. Firebase AI Setup (Optional Future Enhancement)

To enable Firebase AI (currently disabled):

1. Create Firebase project at [console.firebase.google.com](https://console.firebase.google.com/)
2. Add Android app with package name `com.camerapp`
3. Enable Firebase AI Logic
4. Download `google-services.json` and place in `app/` folder
5. Uncomment Firebase dependencies in `build.gradle`

## Usage

### Basic Usage
```kotlin
class YourFragment : Fragment(), VoiceActivityDetector.VoiceActivityDetectorCallback {
    private lateinit var voiceDetector: VoiceActivityDetector

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        voiceDetector = VoiceActivityDetector(requireContext())
        voiceDetector.setCallback(this)
        voiceDetector.setGeminiApiKey("YOUR_API_KEY")
    }

    fun startListening() {
        if (voiceDetector.checkPermissions()) {
            voiceDetector.startVoiceDetection()
        }
    }

    override fun onTranscriptionCompleted(result: TranscriptionResult) {
        if (result.success) {
            Log.d("TAG", "Transcription: ${result.text}")
            // Handle transcription result
        }
    }

    // Implement other required callback methods...
}
```

### Monitoring Transcription Status
```kotlin
// Check if transcription service is ready
val isReady = voiceDetector.isTranscriptionReady()

// Get current method (should be "Direct API" initially)
val method = voiceDetector.getTranscriptionMethod()

// Get detailed status
val info = voiceDetector.getTranscriptionInfo()

// Observe transcription state
voiceDetector.transcriptionInProgress.observe { inProgress ->
    // Update UI
}

voiceDetector.transcriptionResult.observe { result ->
    // Handle transcription result
}
```

## How It Works

### 1. Audio Capture
- Continuously records audio at 16kHz, 16-bit, mono
- Maintains a circular buffer of up to 30 seconds of audio
- Real-time voice activity detection

### 2. Speech Detection
- Uses simple energy thresholding to detect speech
- Triggers transcription after 5 seconds of silence following speech

### 3. Audio Processing
- Converts raw PCM to base64 for API transmission
- Optional preprocessing: noise reduction, silence filtering, normalization

### 4. Transcription
- Sends audio to Gemini API via REST calls
- Uses prompt engineering to request transcription
- Handles errors and retries

### 5. Results
- Returns transcribed text with confidence scores
- Provides processing time and method information
- Updates UI with real-time status

## Limitations

### Current Constraints
- **No Firebase AI**: Disabled until Firebase is properly configured
- **Manual API Key**: Requires manual API key configuration
- **No Persistent Storage**: Transcriptions are not saved
- **No Offline Support**: Requires internet connection
- **Basic Speech Detection**: Simple thresholding, may miss quiet speech

### API Limitations
- Gemini API has rate limits and quotas
- Large audio files may hit size limits
- Network latency affects response time

## Troubleshooting

### Build Issues
- Ensure all dependencies are properly resolved
- Check Kotlin version compatibility (currently 1.7.20)

### Runtime Issues
- **"API key not configured"**: Set the Gemini API key using `setGeminiApiKey()`
- **Network errors**: Check internet connection and API key validity
- **Permission denied**: Ensure RECORD_AUDIO permission is granted

### Transcription Issues
- **Poor accuracy**: Try adjusting audio preprocessing settings
- **No transcription**: Check if speech is being detected (audio levels)
- **Slow response**: May be network latency or API rate limiting

## Future Enhancements

1. **Firebase AI Integration**: Enable Firebase AI Logic for better performance
2. **Offline Support**: Implement on-device speech recognition
3. **Persistent Storage**: Save transcriptions to local database
4. **Advanced Audio Processing**: Better noise reduction and speech enhancement
5. **Streaming Transcription**: Real-time streaming transcription
6. **Language Detection**: Automatic language identification
7. **Custom Commands**: Voice command recognition

## API Reference

### VoiceActivityDetector
- `startVoiceDetection()`: Start listening for speech
- `stopVoiceDetection()`: Stop listening
- `setGeminiApiKey(key: String)`: Configure API key
- `isTranscriptionReady()`: Check if service is ready
- `getTranscriptionMethod()`: Get current API method
- `cancelCurrentTranscription()`: Cancel ongoing transcription

### TranscriptionResult
- `success: Boolean`: Whether transcription succeeded
- `text: String`: Transcribed text
- `confidence: Float`: Confidence score (0.0-1.0)
- `method: String`: API method used
- `processingTime: Long`: Time in milliseconds
- `error: String?`: Error message if failed

### StateFlow Observables
- `transcriptionInProgress`: Boolean indicating if transcribing
- `transcriptionResult`: Latest transcription result
- `recognizedText`: Current recognized text
- `speechDetected`: Boolean indicating if speech detected