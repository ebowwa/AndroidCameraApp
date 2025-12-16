# 🎤 Gemini Transcription Testing Guide

Your Gemini API has been successfully integrated into the Camera App! Here's how to test it.

## ✅ What's Been Implemented

1. **Secure API Key Storage**: Your Gemini API key is stored securely in SharedPreferences
2. **Automatic Speech Detection**: The app listens continuously and detects when you start/stop speaking
3. **Real-time Transcription**: Speech is automatically transcribed using Gemini API
4. **Audio Preprocessing**: Noise reduction and audio enhancement for better accuracy
5. **UI Integration**: Real-time feedback shows "Listening...", "Processing...", and transcription results

## 🚀 How to Test

### 1. Launch the App
```bash
adb shell am start -n com.camerapp/.MainActivity
```

### 2. Grant Permissions
The app will request:
- **Camera Access**: Required (even though we're doing headless operation)
- **Microphone Access**: Required for speech detection

### 3. Test Speech Detection
1. The app will start listening automatically when you grant permissions
2. Say something clearly: *"Hello, this is a test of the transcription service"*
3. Stop talking and wait ~5 seconds for silence detection
4. The app will show "Processing speech..." then display the transcription result

### 4. Expected UI Behavior
- **Initial State**: Shows camera view with audio status
- **When Speaking**: Shows "Listening..."
- **After Silence**: Shows "Processing speech..."
- **Transcription Result**: Shows the transcribed text as a Toast message
- **Error Handling**: Shows error messages if API calls fail

## 🔍 Testing Scenarios

### Basic Speech Recognition
```
Speak: "The weather is nice today"
Expected: Text appears with "The weather is nice today"
```

### Short Commands
```
Speak: "Take photo"
Expected: Text appears with "Take photo" (may also trigger camera)
```

### Longer Sentences
```
Speak: "This is a longer sentence to test how well the transcription service handles continuous speech"
Expected: Full sentence transcription
```

### Quiet Speech
```
Speak softly: "Testing quiet speech"
Expected: May have reduced accuracy, but should still transcribe
```

## 📱 UI Elements to Watch

### Status Indicators
- **Audio Level**: Visual indicator of microphone input
- **Speech Detection**: Shows when speech is detected vs silence
- **Transcription Progress**: "Processing..." during API calls

### Result Display
- **Toast Messages**: Appear with transcribed text
- **Logs**: Check Android logs for detailed status

## 🐛 Troubleshooting

### No Transcription Happens
```bash
# Check if API key is configured
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

# Check logs
adb logcat | grep -i "transcription\|gemini\|voice"
```

### Permission Issues
```bash
# Grant permissions manually
adb shell pm grant com.camerapp android.permission.RECORD_AUDIO
adb shell pm grant com.camerapp android.permission.CAMERA
```

### Network Issues
- Ensure device has internet connection
- Check if Gemini API is accessible
- Verify API key validity

### Audio Issues
- Check microphone permissions
- Ensure device microphone isn't muted
- Try speaking louder/clearer

## 📊 Monitoring

### Real-time Logs
```bash
# Monitor transcription-specific logs
adb logcat -s VoiceActivityDetector:D GeminiTranscriptionService:D AudioProcessor:D
```

### API Call Debugging
```bash
# Monitor network and API calls
adb logcat -s "Transcription"
```

## 🎯 Success Indicators

✅ **Working Properly When:**
- App starts and requests permissions
- Audio level indicator shows microphone input
- "Listening..." appears when you speak
- "Processing..." appears after silence
- Transcribed text appears in Toast messages
- No error messages in logs

❌ **Issues to Watch For:**
- "Transcription failed: API key not configured"
- "Network error: Failed to connect"
- "No speech detected" when speaking clearly
- Empty transcription results
- Permission denied errors

## 🔧 Advanced Configuration

### Adjust Speech Detection Threshold
```kotlin
voiceDetector.configureSpeechDetection(threshold = 1500) // Higher = less sensitive
```

### Cancel Current Transcription
```kotlin
voiceDetector.cancelCurrentTranscription()
```

### Check Service Status
```kotlin
val isReady = voiceDetector.isTranscriptionReady()
val method = voiceDetector.getTranscriptionMethod()
val info = voiceDetector.getTranscriptionInfo()
```

## 📈 Performance Tips

1. **Speak Clearly**: Better audio quality = better transcription
2. **Wait for Silence**: The system needs ~5 seconds of silence to trigger transcription
3. **Good Internet**: Faster network = quicker transcription results
4. **Moderate Length**: Works best with sentences under 30 seconds

## 🎉 Next Steps

Once you confirm basic transcription works, you can:

1. **Enable Firebase AI**: Set up Firebase for better performance
2. **Add Commands**: Implement voice commands for camera control
3. **Save Transcriptions**: Store results in local database
4. **Offline Mode**: Add on-device speech recognition
5. **Multi-language**: Support different languages

## 🔐 Security Notes

- API key is stored in SharedPreferences (basic security)
- In production, consider using Android Keystore
- API key is transmitted over HTTPS
- Audio data is sent to Google servers for processing

---

**Happy Testing! 🎤**

Your Gemini API integration should now provide real-time speech transcription directly on your INMO IMA301 glasses!