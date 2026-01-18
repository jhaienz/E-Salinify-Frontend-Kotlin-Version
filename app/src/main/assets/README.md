# E-Salinify ML Models

This folder contains the machine learning models used for sign language recognition.

## Models

### 1. hand_landmarker.task (7.5 MB)
- **Source**: Google MediaPipe
- **Purpose**: Hand detection and landmark extraction
- **Download URL**: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
- **License**: Apache 2.0
- **Description**: Detects hands in camera frames and extracts 21 landmark points per hand

### 2. model.tflite (1.1 MB)
- **Source**: Custom trained model (converted from smnist.h5)
- **Purpose**: Sign language letter classification
- **Input**: 28x28 grayscale image
- **Output**: 24 classes (A-Y, excluding J and Z)
- **Description**: Classifies preprocessed hand region images into sign language letters

## Usage

These models are automatically loaded by the app:
- `HandDetector` loads `hand_landmarker.task`
- `SignLanguageClassifier` loads `model.tflite`

## Model Pipeline

1. Camera captures frame
2. `hand_landmarker.task` detects hand and returns bounding box
3. Hand region is cropped and preprocessed (grayscale, resize to 28x28, normalize)
4. `model.tflite` classifies the preprocessed image into a letter (A-Y)
5. Stability filter confirms letter after 10 consistent predictions at >85% confidence

## Updating Models

To update the MediaPipe hand landmarker model:
```bash
curl -L -o app/src/main/assets/hand_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
```

To update the sign language classification model:
1. Train your model (e.g., using TensorFlow/Keras)
2. Convert to TensorFlow Lite format
3. Replace `model.tflite` in this folder
4. Ensure input shape is [1, 28, 28, 1] and output is [1, 24]
