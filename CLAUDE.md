Camera App for INMO IMA301 Glasses

This is a camera app built specifically for the INMO IMA301 smart glasses. Since you can see directly through the glasses, the app runs in headless mode - no camera preview needed.

Working features:
Camera captures photos without any preview display
Settings screen for camera orientation and image quality
Speech recognition UI that shows errors when used instead of being disabled
Background service for other apps to trigger camera captures
API integration through broadcast receivers

What's not working:
Speech recognition - the UI exists but shows "service unavailable" errors
No flash functionality (the glasses don't have flash)
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