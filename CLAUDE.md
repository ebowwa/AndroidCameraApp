Camera App for INMO IMA301 Glasses

Main focus is building the UI layer first for smart glasses. We're doing headless camera operation since you see directly through the glasses - no preview display needed.

Current UI work:
Camera controls and settings interface working
Speech recognition UI that shows proper error states instead of being disabled upfront
Settings screen for camera orientation and image quality configuration
Background service UI integration for external app camera triggers

Camera functionality status:
Basic photo capture working in headless mode
Speech recognition shows "service unavailable" errors when accessed (intentional)
No flash functionality removed (glasses don't have flash)
No front camera switching (glasses only have back camera)

Build and install:
./gradlew assembleDebug
adb connect 172.20.10.2:5555
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.camerapp/.MainActivity

Quick commands:
Trigger photo capture from another app:
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

Check if app is running:
adb shell ps | grep camerapp

Project structure:
CameraFragment.kt - Main camera logic using headless CameraX
SettingsFragment.kt - Camera settings UI
CameraTriggerService.kt - Background camera service
docs/HEADLESS_CAMERA_MODE.md - Detailed headless camera implementation notes