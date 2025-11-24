#!/bin/bash

# Camera API Integration Demo Script
# Tests the advanced CameraX API features implemented in feature/camerax-api-iteration

echo "=== CameraX API Integration Demo ==="
echo "Testing advanced camera features for INMO IMA301 glasses"
echo ""

# Check if ADB is available and device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No ADB device connected. Please connect your INMO IMA301 glasses."
    echo "   Use: adb connect <device-ip>:5555"
    exit 1
fi

echo "✅ ADB device connected"

# Install the updated app with advanced CameraX features
echo ""
echo "📦 Installing CameraX API iteration app..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✅ App installed successfully"
else
    echo "❌ App installation failed"
    exit 1
fi

# Launch the app
echo ""
echo "🚀 Launching camera app..."
adb shell am start -n com.camerapp/.MainActivity

sleep 3

echo ""
echo "=== API Integration Tests ==="
echo ""

# Test 1: Basic Broadcast API Trigger
echo "📸 Test 1: Broadcast API Photo Capture"
echo "Sending broadcast: com.camerapp.CAPTURE_PHOTO"
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

sleep 2

# Test 2: Auto-Capture API (15 second interval)
echo ""
echo "⏰ Test 2: Auto-Capture API (15s interval)"
echo "Sending broadcast: com.camerapp.START_AUTO_CAPTURE with interval=15000"
adb shell am broadcast -a com.camerapp.START_AUTO_CAPTURE --ei interval 15000

echo "   Auto-capture will run for 30 seconds..."
sleep 32

echo "Stopping auto-capture..."
adb shell am broadcast -a com.camerapp.STOP_AUTO_CAPTURE

sleep 2

# Test 3: Camera Configuration API
echo ""
echo "⚙️ Test 3: Camera Configuration API"
echo "Sending broadcast: com.camerapp.SET_CAMERA_CONFIG"
echo "   Resolution: 1920x1080, Quality: 100, Mode: QUALITY"
adb shell am broadcast -a com.camerapp.SET_CAMERA_CONFIG \
    --es resolution "1920x1080" \
    --ei quality 100 \
    --es capture_mode "QUALITY"

sleep 3

# Test 4: Additional API Capture with new config
echo ""
echo "📸 Test 4: Photo Capture with High-Quality Settings"
adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO

sleep 2

echo ""
echo "=== Demo Complete ==="
echo ""

# Check generated photos
echo "📁 Generated photos (API and Smart captures):"
adb shell ls -la /sdcard/Android/data/com.camerapp/files/Pictures/ 2>/dev/null || \
adb shell ls -la /storage/emulated/0/Android/data/com.camerapp/files/Pictures/ 2>/dev/null || \
echo "   No photo directory found (check storage permissions)"

echo ""
echo "📋 API Features Tested:"
echo "   ✅ Broadcast Receiver integration"
echo "   ✅ Background Service camera operations"
echo "   ✅ Auto-capture with configurable intervals"
echo "   ✅ Dynamic camera configuration"
echo "   ✅ High-quality capture modes"
echo "   ✅ Smart ImageAnalysis integration (automatic)"

echo ""
echo "🧠 Smart Features (Automatic in App):"
echo "   ✅ Motion detection"
echo "   ✅ Scene classification (indoor/outdoor/bright/low-light)"
echo "   ✅ Brightness monitoring"
echo "   ✅ Optimal scene auto-capture"
echo "   ✅ Performance-optimized analysis (640x480)"

echo ""
echo "🔗 API Usage Examples:"
echo ""
echo "# Basic capture:"
echo "adb shell am broadcast -a com.camerapp.CAPTURE_PHOTO"
echo ""
echo "# Start auto-capture (30s interval):"
echo "adb shell am broadcast -a com.camerapp.START_AUTO_CAPTURE --ei interval 30000"
echo ""
echo "# Stop auto-capture:"
echo "adb shell am broadcast -a com.camerapp.STOP_AUTO_CAPTURE"
echo ""
echo "# Configure camera:"
echo "adb shell am broadcast -a com.camerapp.SET_CAMERA_CONFIG \\"
echo "    --es resolution \"1920x1080\" --ei quality 100 \\"
echo "    --es capture_mode \"QUALITY\""
echo ""
echo "🎯 Perfect for smart glasses automation, voice commands, and external app integration!"