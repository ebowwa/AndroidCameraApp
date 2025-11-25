# Camera App for INMO IMA301 Glasses

A camera app built for the INMO IMA301 smart glasses. It runs in headless mode (no preview) since you see directly through the glasses.

## What's Working

- ✅ Camera captures photos without preview display
- ✅ Settings screen for camera orientation and image quality
- ✅ Speech recognition UI (shows errors when used, not disabled upfront)
- ✅ Background service for external app camera triggers
- ✅ API integration via broadcast receivers

## What's Not Working

- ❌ Speech recognition - UI exists but shows "service unavailable" errors
- ❌ No flash functionality (glasses don't have flash)
- ❌ No front camera switching (glasses only have back camera)

## Build & Install

```bash
# Build
./gradlew assembleDebug

# Install to glasses (connected via WiFi ADB)
adb connect 172.20.10.2:5555
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.camerapp/.MainActivity
```

## Quick Commands

```bash
# Trigger photo capture from another app
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

# Check if app is running
adb shell ps | grep camerapp
```

## Project Structure

- `CameraFragment.kt` - Main camera logic (headless CameraX)
- `SettingsFragment.kt` - Camera settings UI
- `CameraTriggerService.kt` - Background camera service
- `docs/HEADLESS_CAMERA_MODE.md` - Detailed headless camera implementation notes