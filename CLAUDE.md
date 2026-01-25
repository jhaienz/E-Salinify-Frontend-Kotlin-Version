# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

E-Salinify is an Android app for American Sign Language (ASL) recognition using real-time camera input. It supports two recognition modes:
- **Letter Mode**: Recognizes individual letters (A-Z) using hand landmark detection
- **Phrase Mode**: Recognizes 250 complete sign phrases using holistic detection (face + pose + hands)

## Build Commands

**Prerequisites**: Java 17 is required (`jvmTarget = "17"`)

```bash
# Build the app
./gradlew build

# Build specific variant
./gradlew assembleDebug
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# View connected devices
adb devices

# View app logs (filter by package name)
adb logcat | grep com.esalinify

# Clear app data (useful for testing first-run experience)
adb shell pm clear com.esalinify
```

## Architecture

### Tech Stack
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with StateFlow
- **Dependency Injection**: Hilt/Dagger
- **Navigation**: Jetpack Navigation Compose
- **ML Framework**: TensorFlow Lite + MediaPipe Tasks Vision
- **Camera**: CameraX
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35

### Package Structure
```
com.esalinify/
├── data/           # Data models and state management
├── di/             # Hilt dependency injection modules
├── navigation/     # Navigation graph and screen routes
├── ui/
│   ├── components/ # Reusable Compose components
│   ├── screens/    # Screen composables
│   │   └── viewmodel/ # ViewModels for each screen
│   └── theme/      # Material 3 theming
└── util/           # ML utilities and detectors
```

### Dual Recognition System

The app uses **two separate detection pipelines** that switch based on `RecognitionMode`:

#### Letter Mode (LETTER)
- **Detector**: `HandDetector` (MediaPipe Hand Landmarker)
- **Input**: 21 hand landmarks (x, y, z coordinates)
- **Classifier**: `LandmarkClassifier`
- **Model**: `keypoint_classifier.tflite`
- **Output**: A-Z letters (excludes J and Z)
- **Confidence**: 55% threshold
- **Stability**: Requires 0.5 seconds of continuous stable detection

#### Phrase Mode (PHRASE)
- **Detector**: `HolisticDetector` (MediaPipe Face + Pose + Hand combined)
- **Input**: 543 landmarks total
  - 468 face landmarks
  - 33 pose landmarks
  - 21 left hand landmarks
  - 21 right hand landmarks
- **Classifier**: `SignLanguageClassifier`
- **Model**: `phrase_model.tflite` (from Kaggle Isolated Sign Language Recognition)
- **Output**: 250 sign phrases
- **Confidence**: 50% threshold
- **Requirement**: Hands MUST be detected (face/pose alone insufficient)

### State Management

`CameraViewModel` manages all recognition state:
- `RecognitionMode`: Switches between LETTER/PHRASE
- `CameraFacing`: BACK or FRONT camera
- `translatedText`: Accumulated recognized text
- `currentWord`: Word being built (letters only)
- `currentPrediction`: Current frame prediction with confidence

State updates use Kotlin `StateFlow` and are scoped to `viewModelScope`.

### MediaPipe Timestamp Requirements

**CRITICAL**: MediaPipe video mode processors require **monotonically increasing timestamps**. The `HolisticDetector` uses synchronized detection with staggered timestamps:

```kotlin
@Synchronized
fun detectHolistic(bitmap: Bitmap, timestampMs: Long): HolisticDetectionResult? {
    lastFaceTimestamp = maxOf(timestampMs, lastFaceTimestamp + 10)
    lastPoseTimestamp = maxOf(timestampMs + 5, lastPoseTimestamp + 10)
    lastHandTimestamp = maxOf(timestampMs + 10, lastHandTimestamp + 10)
    // ... process with these timestamps
}
```

**Never** pass the same timestamp twice or a timestamp lower than previously used.

### ML Model Files

All models must be placed in `app/src/main/assets/`:

**Letter Recognition:**
- `hand_landmarker.task` (7.5 MB) - MediaPipe hand detection
- `keypoint_classifier.tflite` (25 KB) - Letter classification

**Phrase Recognition:**
- `face_landmarker.task` (3.6 MB) - MediaPipe face detection
- `pose_landmarker.task` (30 MB) - MediaPipe pose detection
- `phrase_model.tflite` (38 MB) - Phrase classification

**Legacy models** (may still be in assets):
- `model.tflite` - Old letter classifier (28x28 image input)
- `hand_sign_model.tflite` - Unused

**Training reference:**
- `tflite-isolated-sign-language-recognition.ipynb` - Jupyter notebook showing the Kaggle model training approach for phrase recognition

**Phrase vocabulary:** The phrase model recognizes exactly 250 specific ASL phrases (primarily simple words like "cat", "dog", "hello", "thank you"). The complete list is hardcoded in [SignLanguageClassifier.kt:20-46](app/src/main/java/com/esalinify/util/SignLanguageClassifier.kt#L20-L46).

See [app/src/main/assets/README.md](app/src/main/assets/README.md) for model download links (note: this file may reference outdated models).

### Camera Lifecycle

The camera executor lifecycle is **separate** from camera binding:

```kotlin
// In CameraPreview.kt
DisposableEffect(Unit) {
    // Executor created once
    onDispose { cameraExecutor.shutdown() }
}

DisposableEffect(cameraFacing) {
    // Camera rebinds when facing changes
    // Don't shutdown executor here!
}
```

This prevents model crashes when switching between front/back cameras.

### Stability Filter

Both modes use stability filtering to prevent jitter:

**Letter Mode:**
- **Time-based stability**: Requires 0.5 seconds (500ms) of continuous detection of the same letter
- Confirms letter only if confidence >55% throughout the stable period
- If prediction changes, timer resets and starts tracking the new letter
- 0.8 second cooldown prevents duplicate letters
- 3 second no-detection adds space (word separator)
- Logs progress every 250ms during stable tracking

**Phrase Mode:**
- **Frame-based stability**: Collects last 8 predictions
- Confirms phrase only if all 8 match with >50% confidence
- 1.5 second cooldown prevents duplicate phrases
- No automatic spacing (phrases are space-separated when added)

## Important Patterns

### Hilt Injection

All ViewModels use `@HiltViewModel` with constructor injection:

```kotlin
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel()
```

The application class must be annotated with `@HiltAndroidApp`.

### Navigation

Navigation uses sealed `Screen` objects in `navigation/Screen.kt`:
- `Screen.Splash` → `Screen.Onboarding` (first run) → `Screen.Home`
- `Screen.Home` → `Screen.Camera` or `Screen.Keyboard`

Always use `popUpTo { inclusive = true }` to remove splash/onboarding from backstack.

### Preferences

`PreferencesManager` uses DataStore for persistent settings:
- `hasSeenOnboarding`: Boolean to skip onboarding on subsequent launches
- Injected via Hilt as `@Singleton`

### Permissions

The app requires camera permission:
- Uses Accompanist Permissions library for permission handling
- Permission is requested at runtime when accessing camera screen
- No special AndroidManifest.xml configuration beyond declaring CAMERA permission

### ProGuard/R8

Currently ProGuard/R8 is **disabled** in release builds (`isMinifyEnabled = false`).

If enabling in the future:
- Add TensorFlow Lite keep rules to `proguard-rules.pro`
- Keep MediaPipe classes from obfuscation
- Test thoroughly as ML models can break with aggressive optimization

## Debugging

Use Android Logcat with filters to debug specific components:

```bash
# View all app logs
adb logcat | grep com.esalinify

# Filter by specific component
adb logcat | grep CameraViewModel
adb logcat | grep HolisticDetector
adb logcat | grep SignLanguageClassifier

# Watch for MediaPipe errors
adb logcat | grep MediaPipe

# View TensorFlow Lite logs
adb logcat | grep tflite
```

Key log messages to watch for:
- `✓✓✓ LETTER CONFIRMED` / `✓✓✓ PHRASE CONFIRMED` - successful recognition
- `No hands detected - skipping holistic result` - phrase mode requires visible hands
- `packets having smaller timestamp than processed timestamp` - timestamp issue
- `Holistic detection - Face: X, Pose: X, Hands: X` - component detection status

## Common Issues

### MediaPipe Timestamp Errors
**Error**: "packets having smaller timestamp than processed timestamp"
**Solution**: Ensure all detector calls use monotonically increasing timestamps (see HolisticDetector implementation)

### Mode Toggle Reverting
**Error**: Mode switches back to LETTER immediately
**Solution**: Check `clearText()` preserves `recognitionMode` in state update

### Hands Not Detected in Phrase Mode
**Issue**: Holistic detection requires hands but only detects face/pose
**Debug**: Check logs for "No hands detected - skipping holistic result"
**Note**: This is a known limitation—ensure adequate lighting and hand visibility

### Camera Switch Crashes
**Error**: Models fail after switching front/back camera
**Solution**: Executor must outlive camera rebinds (see Camera Lifecycle above)

### Models Not Loading
**Error**: App crashes on launch or camera screen
**Debug steps**:
1. Check all 5 model files exist in `app/src/main/assets/`
2. Verify file sizes match expected values
3. Check logcat for "Error loading TensorFlow Lite model" or "Failed to initialize HolisticDetector"
4. Ensure assets folder is included in the APK (check `build/intermediates/assets/`)

### Low Phrase Recognition Accuracy
**Issue**: Phrases not being recognized or wrong predictions
**Notes**:
- The model is trained on specific 250 phrases - arbitrary signs won't work
- Model expects clear visibility of face, torso, and both hands
- Lighting conditions significantly affect detection
- Some signs may be similar and cause confusion
- Check logs for confidence percentages and top-3 predictions

## Testing Sign Recognition

Since testing alone is difficult:
- Use YouTube videos of ASL performances
- Point camera at screen showing signs
- Ensure good lighting and clear hand visibility
- For phrase mode, full body visibility required (face + torso + hands)

## App Version & Metadata

Current version (from [app/build.gradle.kts](app/build.gradle.kts#L18)):
- `versionCode`: 1
- `versionName`: "1.0.0"
- `applicationId`: "com.esalinify"

When incrementing version:
- Bump `versionCode` (integer) for every release (Google Play requirement)
- Update `versionName` (string) following semantic versioning

## Model Updates

### Replacing Phrase Recognition Model

To replace phrase recognition model:
1. Convert new model to TensorFlow Lite format
2. Ensure input shape: `[1, 1629]` (543 landmarks × 3 coordinates)
3. Ensure output shape matches number of phrases in vocabulary
4. Update label list in [SignLanguageClassifier.kt:20-46](app/src/main/java/com/esalinify/util/SignLanguageClassifier.kt#L20-L46) if vocabulary changes
5. Replace `phrase_model.tflite` in `app/src/main/assets/` folder
6. Test with known signs before deploying
7. Consider adjusting `PHRASE_CONFIDENCE_THRESHOLD` in [CameraViewModel.kt:46](app/src/main/java/com/esalinify/ui/screens/viewmodel/CameraViewModel.kt#L46) based on new model's accuracy

### Replacing Letter Recognition Model

To replace letter recognition model:
1. Convert new model to TensorFlow Lite format
2. Ensure input expects 21 hand landmarks (x, y, z) = 63 features
3. Update `LandmarkClassifier.kt` if needed
4. Replace `keypoint_classifier.tflite` in `app/src/main/assets/`
5. Consider adjusting `CONFIDENCE_THRESHOLD` (currently 0.55) and `LETTER_STABILITY_TIME_MS` (currently 500ms) in [CameraViewModel.kt:49-51](app/src/main/java/com/esalinify/ui/screens/viewmodel/CameraViewModel.kt#L49-L51) based on new model's accuracy
