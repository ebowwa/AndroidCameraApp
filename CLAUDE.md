# Android Camera App - INMO IMA301 Smart Glasses

Optimized camera application for INMO IMA301 smart glasses with headless camera functionality and speech recognition UI.

## 🎯 Project Overview

This is a specialized Android camera app designed specifically for smart glasses (INMO IMA301). The app features:

- **Headless Camera Mode**: No preview display - perfect for glasses where you see directly through the device
- **Speech Recognition UI**: Live captioning interface with error handling
- **Minimal Interface**: Optimized for glasses form factor
- **Background Operations**: Service-based camera triggers and API integration

## 📱 Device Specific

**Target Device**: INMO IMA301 Smart Glasses
- **Camera**: Back-facing only (no front camera switching)
- **Display**: No flash functionality
- **Form Factor**: Headless operation optimized
- **Connection**: WiFi ADB debugging (172.20.10.2:5555)

## 🏗️ Architecture

### Core Components

- **CameraFragment**: Main camera logic with headless CameraX implementation
- **SettingsFragment**: Configuration UI (orientation, quality, translation settings)
- **CameraTriggerService**: Background service for API-based camera triggers
- **CameraTriggerReceiver**: Broadcast receiver for external app integration

### Key Features

1. **Headless Camera Operation**
   - No preview surface required
   - Direct image capture using CameraX
   - Optimized for glasses see-through experience

2. **Speech Recognition Interface**
   - UI elements for live captioning display
   - Error-on-usage pattern (shows errors when accessed, not disabled upfront)
   - Settings for confidence thresholds and auto-start behavior

3. **API Integration**
   - Broadcast receiver for external app triggers
   - Intent-based photo capture commands
   - Background service for continuous operation

## 🔧 Development Status

### ✅ Completed Features
- Headless camera mode implementation
- Flash functionality removal (glasses have no flash)
- Vosk/Whisper code removal with UI preservation
- Generic "transcription model" terminology
- Error-on-usage UI pattern
- Settings management system
- Basic camera triggers via broadcast receiver

### 🚧 Pending Work
- Camera permission error handling
- Photo save error handling
- Camera hardware error handling
- Settings validation error handling
- Storage error handling
- Future speech recognition implementation (WhisperKit or similar)

## 📁 Documentation

- **[docs/HEADLESS_CAMERA_MODE.md](docs/HEADLESS_CAMERA_MODE.md)**: Comprehensive guide for headless camera implementation
- **AndroidManifest.xml**: Permission and service configuration
- **Settings**: Camera orientation, image quality, translation settings

## 🔗 Quick Commands

### Build and Install
```bash
# Build APK
./gradlew assembleDebug

# Install to glasses via WiFi ADB
adb connect 172.20.10.2:5555
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.camerapp/.MainActivity
```

### Camera Triggers
```bash
# Trigger photo capture via broadcast
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

# Start auto-capture every 30 seconds
adb shell am broadcast -a com.camerapp.START_AUTO_CAPTURE --ez interval 30000
```

## 🎛️ Configuration

### Camera Settings (via SettingsFragment)
- **Orientation**: Portrait, Landscape, Auto
- **Image Quality**: High (1920x1080), Medium (1280x720), Low (640x480)
- **Speech Recognition**: Confidence threshold, auto-start toggle

### Speech Recognition Settings
- **Model Status**: Shows download/availability state
- **Error Handling**: Service unavailable errors on usage
- **Auto-start**: Option to start translation on app launch

## 🔍 Troubleshooting

### Common Issues
1. **Camera not working**: Check permissions in AndroidManifest.xml
2. **Speech recognition errors**: Expected behavior - service not implemented yet
3. **Flash button missing**: Intentionally removed for glasses
4. **Storage permission**: Required for photo saving

### Debug Commands
```bash
# Check device connection
adb devices

# View logs
adb logcat | grep "CameraX"

# Test camera trigger
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO
```

## 🚀 Future Development

1. **WhisperKit Integration**: Replace placeholder speech recognition with Apple's WhisperKit
2. **Voice Commands**: Add "take picture" voice triggers
3. **Auto-capture**: Timer-based photo capture
4. **Cloud Storage**: Auto-upload captured photos
5. **Gesture Controls**: Double-tap or gesture-based capture triggers

## 📱 App Structure

```
app/src/main/java/com/camerapp/
├── CameraFragment.kt          # Main camera logic and headless implementation
├── SettingsFragment.kt        # UI settings and configuration
├── CameraTriggerService.kt    # Background camera service
├── CameraTriggerReceiver.kt   # Broadcast receiver for API integration
└── MainActivity.kt           # App entry point

app/src/main/res/
├── layout/
│   ├── fragment_camera.xml    # Camera UI (no preview, minimal controls)
│   └── fragment_settings.xml  # Settings interface
└── drawable/                  # UI assets and icons
```

---

**Branch**: `feature/whisperkit-live-translation`
**Target**: INMO IMA301 Smart Glasses
**Status**: Camera functional, speech recognition UI with error handling