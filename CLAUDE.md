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

Advanced UI Design Principles:

Spatial computing represents a fundamental shift from traditional 2D interfaces to three-dimensional digital content that exists in physical space. The UI isn't displayed on screens—it's positioned in the user's actual environment as floating objects.

Transparency and Materials System:
Liquid glass materials go beyond simple blur effects, featuring real-time lensing and distortion that creates genuine depth perception. These semi-transparent panes actively distort background content rather than just blurring it, with dynamic transparency that adapts to lighting conditions and content importance.

Two primary material types create visual hierarchy:
- Light glass materials that are nearly transparent for primary interactive elements
- Dark glass materials with frosted appearance for secondary content and backgrounds

Depth and Spatial Hierarchy:
Multi-layer approach uses foreground, mid-ground, and background planes with different focus distances. Depth perception comes from parallax effects, realistic shadow casting, proper occlusion where closer objects block distant ones, and focus planes at different distances for visual comfort.

Spatial Layout Patterns:
Windows float in physical space at comfortable viewing distances, maintaining consistent positioning relative to user perspective. Content can be positioned at different depths for natural hierarchy, with apps anchoring to real-world surfaces or floating freely in space.

Design Implementation:
Visual hierarchy uses material clarity - important content appears clearer with less distortion, while secondary information appears more frosted. Interactive elements have higher contrast and sharper edges, with non-essential UI elements becoming nearly transparent.

Motion and transitions feature fluid animations between spatial states, smooth depth transitions when content moves closer or farther, and physics-based movement that feels natural in 3D space.

User Experience Considerations:
Comfort-focused design positions UI elements at natural eye-level and arm's reach, reducing eye strain through careful depth management. The system minimizes required head movement for common interactions and adapts to user posture and environment.

Accessibility features include adjustable transparency levels for visual comfort, high-contrast modes for low vision users, and alternative input methods beyond primary interaction methods.

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