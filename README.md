# INMO IMA301 Camera App

A modern Android camera application built specifically for the INMO IMA301 smart glasses, featuring CameraX integration and Material Design 3 UI.

## 🎯 Features

- **Real-time Camera Preview** using CameraX API
- **Photo Capture** with automatic gallery saving
- **Camera Switching** (front/back camera)
- **Flash Control** (on/off/auto modes)
- **Runtime Permissions** handling
- **Material Design 3** UI optimized for glasses display
- **ViewBinding** for type-safe view references

## 📱 Device Compatibility

- **Target Device**: INMO IMA301 Smart Glasses
- **Android Version**: API 24+ (Android 7.0+)
- **Target SDK**: 33 (Android 13)
- **Architecture**: ARM64

## 🔧 Technical Stack

- **Language**: Kotlin + Java
- **Framework**: AndroidX CameraX 1.3.1
- **Build Tool**: Gradle 8.0
- **JDK**: Java 17.0.17 (LTS)
- **UI**: Material Design 3
- **Architecture**: Fragment-based with ViewBinding

## 🚀 Installation

### Prerequisites
- Android SDK Build-Tools 30.0.3+
- Android SDK Platform 33
- Java 17 JDK
- Gradle 8.0+

### Build from Source
```bash
# Clone the repository
git clone https://github.com/ebowwa/AndroidCameraApp.git
cd AndroidCameraApp

# Build the APK
./gradlew assembleDebug

# Install to device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### ADB Installation to INMO IMA301
```bash
# Connect to glasses and install
adb -s YM00FCE8100706 install -r app-debug/outputs/apk/debug/app-debug.apk

# Launch the app
adb -s YM00FCE8100706 shell am start -n com.camerapp/.MainActivity
```

## 🐛 Troubleshooting & Version Compatibility Issues

### Major Issues Encountered During Development

#### 1. Java/Gradle Version Compatibility Matrix
- ✅ **Working Configuration**: Java 17.0.17 + Gradle 8.0
- ❌ **Failed Attempts**:
  - Gradle 9.2.1 + Java 17: `org.gradle.api.internal.HasConvention` errors
  - Gradle 8.0 + Java 11: CameraX dependency conflicts
  - Multiple Gradle versions caused daemon conflicts

**Solution**: Use Gradle 8.0 with Java 17.0.17 LTS for maximum stability.

#### 2. Android Gradle Plugin Compatibility
- **Issue**: AGP 8.0.2 only supports up to compileSdk 33
- **Error**: `Android Gradle plugin (8.0.2) was tested up to compileSdk = 33`
- **Fix**: Downgraded from compileSdk 34 to 33 and added `android.suppressUnsupportedCompileSdk=34` to gradle.properties

#### 3. Missing Resource Files
- **Error**: `resource xml/data_extraction_rules not found`, `resource mipmap/ic_launcher not found`
- **Root Cause**: Incomplete project structure from scratch build
- **Solution**:
  - Created all required XML rule files (`data_extraction_rules.xml`, `backup_rules.xml`)
  - Generated launcher icons for all density levels (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
  - Used ImageMagick to create PNG icons: `convert -size 48x48 xc:lightblue -fill darkblue -draw "circle 24,24 24,12"`

#### 4. AndroidX Dependencies
- **Error**: `Configuration contains AndroidX dependencies but android.useAndroidX not enabled`
- **Fix**: Added `android.useAndroidX=true` to `gradle.properties`

#### 5. SDK Build Tools Installation
- **Issue**: Missing Android SDK Build-Tools 30.0.3
- **Error**: Multiple package.xml parsing warnings
- **Resolution**: Let Android SDK manager auto-install required build tools during first build

#### 6. Gradle Daemon Conflicts
- **Problem**: Multiple Gradle versions running simultaneously
- **Symptoms**: Build caching issues, version conflicts
- **Solution**: Clean gradle daemon between major version changes: `./gradlew --stop`

### Build Performance Notes
- **Initial Build**: ~7 minutes (including dependency downloads)
- **Incremental Builds**: ~4 seconds
- **APK Size**: ~2.3MB debug APK
- **Final Success**: `BUILD SUCCESSFUL in 4s` with 36 tasks (6 executed, 30 up-to-date)

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/camerapp/
│   │   ├── MainActivity.kt          # Permission handling & fragment management
│   │   └── CameraFragment.kt        # Core camera implementation
│   ├── res/
│   │   ├── drawable/                # UI icons and backgrounds
│   │   ├── layout/                  # XML layouts
│   │   ├── mipmap-*/                # Launcher icons (all densities)
│   │   ├── values/                  # Strings, themes, colors
│   │   └── xml/                     # File providers & backup rules
│   └── AndroidManifest.xml          # App configuration & permissions
├── build.gradle                     # App-level build configuration
└── gradle.properties               # Global gradle settings
```

## 🔐 Permissions

- `CAMERA` - Required for camera access
- `WRITE_EXTERNAL_STORAGE` - For saving photos (runtime)
- `READ_EXTERNAL_STORAGE` - For gallery access (runtime)

## 🎨 UI Components

- **PreviewView**: CameraX preview surface
- **ImageButton**: Capture button with circular background
- **MaterialButton**: Camera switching and flash controls
- **ProgressBar**: Loading indicator during camera initialization

## 📸 Camera Integration

### CameraX Features Used
- **Preview**: Real-time camera preview
- **ImageCapture**: Photo capture functionality
- **CameraSelector**: Front/back camera switching
- **FlashMode**: Flash control (auto, on, off)

### Image Processing
- Automatic image rotation based on device orientation
- JPEG compression for optimal file size
- Metadata preservation (EXIF data)
- Gallery integration with MediaStore

## 🚀 Deployment

The app has been successfully deployed and tested on:
- **Device**: INMO IMA301 Smart Glasses
- **Device ID**: YM00FCE8100706
- **Package**: `com.camerapp`
- **Status**: ✅ Working with live camera preview

## 📝 Development Notes

### Key Architecture Decisions
1. **Fragment-based**: Better separation of concerns and lifecycle management
2. **ViewBinding**: Type-safe view references, no more findViewById()
3. **CameraX**: Modern camera API with better compatibility and less boilerplate
4. **Material Design 3**: Latest design system with improved accessibility
5. **Runtime Permissions**: Proper Android 6.0+ permission handling

### Performance Optimizations
- Camera lifecycle tied to fragment lifecycle
- Memory-efficient image capture with in-memory processing
- Background thread for image file operations
- Proper camera resource cleanup
