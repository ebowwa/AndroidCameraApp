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

Advanced UI Design Principles (Learned from Apple Vision Pro):

Apple Vision Pro represents a fundamental shift from traditional 2D interfaces to spatial computing where digital content exists as three-dimensional objects in physical space. The UI isn't just displayed on screens—it's positioned in the user's actual environment.

Transparency and Materials System:
Glassmorphism evolution featuring Liquid Glass - an advanced material system that goes beyond simple blur effects with real-time lensing and distortion creating depth perception. Semi-transparent panes distort background content rather than just blurring it, with dynamic transparency adapting to lighting conditions and content.

Material Types:
- Light Glass Materials: Nearly transparent, for primary interactive elements
- Dark Glass Materials: Frosted appearance, for secondary content and backgrounds
- Volumetric Materials: 3D objects with depth and internal structure

Depth and Spatial Hierarchy:
Multi-layer approach uses foreground (interactive elements with high contrast), mid-ground (primary content windows with glass materials), and background (physical environment visible through transparent elements). Depth perception comes from parallax effects where content moves at different rates based on depth, realistic shadow casting, focus planes at different focal distances, and proper occlusion where closer objects block distant ones.

Transparency Implementation:
Background blur with variable intensity based on content importance, edge distortion simulating real glass physics, color shifting and light refraction through transparent materials, and adaptive opacity responding to ambient lighting.

Spatial Layout:
Windows float in physical space at comfortable viewing distances, maintaining consistent positioning relative to user perspective. Content can be positioned at different depths for visual hierarchy, with apps anchoring to real-world surfaces or floating freely in space.

Technical Implementation Details:
SwiftUI Materials Framework provides pre-built glass materials with consistent visual properties, real-time blur calculations based on content behind transparent elements, performance-optimized rendering for smooth 90fps refresh rates, and accessibility support for users with visual impairments.

Depth Alignment System:
Automatic positioning of UI elements at optimal distances, eye-tracking integration for natural focal point selection, gesture recognition for manipulating spatial content, and multi-user support with individual viewport optimization.

Design Patterns and Principles:
Visual hierarchy uses material clarity where important content appears clearer with less distortion, secondary information appears more frosted and blurred, interactive elements have higher contrast and sharper edges, and non-essential UI elements can be nearly transparent.

Motion and transitions feature fluid animations between different spatial states, smooth depth transitions when content moves closer or farther, morphing effects when UI elements change context, and physics-based movement that feels natural in 3D space.

User Experience Considerations:
Comfort and ergonomics position UI elements at natural eye-level and arm's reach, reducing eye strain through careful depth management. The system minimizes required head movement for common interactions and adapts to user posture and environment.

Accessibility features include adjustable transparency levels for visual comfort, high-contrast modes for users with low vision, alternative input methods beyond eye-tracking and gestures, and customizable text sizing and spacing in 3D space.

Industry Impact:
The Vision Pro's approach has influenced broader UI design trends with widespread adoption of glassmorphism effects in traditional 2D interfaces, greater emphasis on depth and layering in flat designs, increased use of transparency and blur effects in mobile apps, and focus on materials-based design rather than flat colors.

This represents a fundamental shift in how we think about user interfaces—not as screens to be looked at, but as environments to be inhabited and interacted with naturally in three-dimensional space.

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