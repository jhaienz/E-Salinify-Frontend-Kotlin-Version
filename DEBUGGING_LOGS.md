# E-Salinify Camera Debugging Guide

## How to View Logs

Run this command in your terminal to filter for camera-related logs:

```bash
adb logcat | grep -E "CameraViewModel|SignLanguageClassifier|HandDetector|CameraPreview"
```

Or for a cleaner output with colors (if supported):

```bash
adb logcat | grep -E "CameraViewModel|SignLanguageClassifier|HandDetector|CameraPreview" --color=always
```

To see ALL logs (verbose):

```bash
adb logcat *:V | grep -E "CameraViewModel|SignLanguageClassifier|HandDetector|CameraPreview"
```

---

## Expected Log Flow (When Working Correctly)

### 1. App Launch & Model Initialization

```
CameraViewModel: ======================================
CameraViewModel: Initializing ML Models...
CameraViewModel: ======================================
CameraViewModel: Step 1/2: Creating SignLanguageClassifier...
SignLanguageClassifier: Loading TensorFlow Lite model from assets/model.tflite...
SignLanguageClassifier: Model file size: 1061564 bytes
SignLanguageClassifier: ✓ TensorFlow Lite model loaded successfully
SignLanguageClassifier:   Input shape: [1, 28, 28, 1]
SignLanguageClassifier:   Output shape: [1, 24]
SignLanguageClassifier:   Labels: 24 letters (A-Y, excluding J and Z)
CameraViewModel: Step 2/2: Creating HandDetector...
HandDetector: Initializing HandLandmarker with model from assets...
HandDetector: BaseOptions configured with model: hand_landmarker.task
HandDetector: Creating HandLandmarker from options...
HandDetector: ✓✓✓ HandLandmarker initialized successfully! ✓✓✓
CameraViewModel: ✓✓✓ ALL MODELS INITIALIZED SUCCESSFULLY ✓✓✓
CameraViewModel: Ready to process camera frames...
```

### 2. Camera Setup

```
CameraPreview: Setting up CameraX...
CameraPreview: CameraProvider obtained
CameraPreview: Preview use case created
CameraPreview: ImageAnalysis use case created
CameraPreview: ✓ Camera initialized successfully - frames will be analyzed
CameraPreview: ✓ First frame received! Starting frame processing...
```

### 3. Frame Processing (Every 30 frames)

```
CameraViewModel: Processing frame #30 (1920x1080) @ 123456789ms
```

### 4. Hand Detection (When hand is visible)

```
HandDetector: ✓ Hand detected with 21 landmarks
CameraViewModel: → Hand detected! Bounding box: Rect(100, 200 - 500, 600)
CameraViewModel:   Cropped region: 425x425
CameraViewModel:   Preprocessed: 28x28
```

### 5. Sign Language Recognition

```
SignLanguageClassifier: ✓ Prediction: A (95%) - Top 3: A:95% B:3% C:1%
CameraViewModel:   Inference result: A @ 95%
CameraViewModel:   Stability filter: A @ 95% (threshold: 85%)
CameraViewModel:     Added to list. Current: A (1/10)
```

### 6. Letter Confirmation (After 10 stable frames)

```
CameraViewModel:     Added to list. Current: AAAAAAAAAA (10/10)
CameraViewModel:     Full buffer! Unique predictions: [A]
CameraViewModel:     ✓✓✓ LETTER CONFIRMED: 'A' ✓✓✓
CameraViewModel:     Current text: 'A'
```

---

## Common Issues & What to Look For

### Issue 1: Model Not Loading

**Look for:**
```
SignLanguageClassifier: ❌ CRITICAL: Error loading TensorFlow Lite model
```

**Solution:** Check that `model.tflite` exists in `/app/src/main/assets/` folder

### Issue 2: Hand Detection Not Working

**Look for:**
```
HandDetector: ❌ CRITICAL: Failed to initialize HandLandmarker
```

**Or constant:**
```
CameraViewModel:   No hand - cleared prediction list
```

**Solution:** MediaPipe initialization issue. Check dependency version.

### Issue 3: Camera Not Sending Frames

**Missing log:**
```
CameraPreview: ✓ First frame received! Starting frame processing...
```

**Solution:** Camera permission not granted or CameraX initialization failed.

### Issue 4: Low Confidence Predictions

**Look for:**
```
SignLanguageClassifier: ✓ Prediction: X (45%) - Top 3: X:45% Y:43% Z:12%
CameraViewModel:     Low confidence - clearing predictions
```

**Solution:**
- Poor lighting conditions
- Hand too far from camera
- Unclear sign gesture
- Model may need retraining

### Issue 5: Predictions Not Stabilizing

**Look for:**
```
CameraViewModel:     Predictions not stable (3 different)
CameraViewModel:     Added to list. Current: ABCABCABCA (10/10)
```

**Solution:** Hand movement too quick or inconsistent sign

---

## Log Legend

- ✓ = Success
- ❌ = Critical Error
- ⚠ = Warning
- → = Processing step

---

## Log Levels

- **I (Info)**: Major milestones (model loaded, camera ready, letter confirmed)
- **D (Debug)**: Detailed process info (hand detected, prediction results)
- **V (Verbose)**: Every detail (frame-by-frame processing, stability tracking)
- **E (Error)**: Something went wrong

---

## Quick Diagnostic Commands

### Check if model file exists:
```bash
adb shell ls -lh /data/data/com.esalinify/files/
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep model.tflite
```

### Clear app data and restart:
```bash
adb shell pm clear com.esalinify
adb shell am start -n com.esalinify/.MainActivity
```

### Monitor specific log level:
```bash
# Only errors
adb logcat *:E | grep -E "CameraViewModel|SignLanguageClassifier|HandDetector"

# Only info and above
adb logcat *:I | grep -E "CameraViewModel|SignLanguageClassifier|HandDetector"
```

---

## Performance Monitoring

Watch for frame processing rate:
```bash
adb logcat | grep "Processed.*frames total"
```

Should show approximately:
- Frame #60 after ~2 seconds (30 FPS)
- Frame #120 after ~4 seconds (30 FPS)

If much slower, there's a performance bottleneck.

---

## Success Indicators

✅ Model loads with correct size (1061564 bytes)
✅ Input/output shapes are [1, 28, 28, 1] and [1, 24]
✅ Camera starts and sends frames
✅ Hand detection finds 21 landmarks
✅ Predictions have >85% confidence
✅ Letters get confirmed after 10 stable frames
✅ Translated text updates in UI

---

## Troubleshooting Steps

1. **No logs at all?**
   - Check device connection: `adb devices`
   - Check package name: `adb shell pm list packages | grep esalinify`

2. **Model errors?**
   - Verify `model.tflite` in assets folder
   - Check file size: `ls -lh app/src/main/assets/model.tflite`

3. **Hand detection errors?**
   - Try lowering confidence thresholds (already set to 0.3)
   - Check MediaPipe version compatibility

4. **Camera permission denied?**
   - Check manifest has camera permission
   - Grant manually: Settings → Apps → E-Salinify → Permissions

5. **Everything runs but no letters confirmed?**
   - Check stability filter logs
   - Verify confidence > 85%
   - Ensure 10 consecutive identical predictions
