package com.esalinify.ui.screens.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esalinify.data.CameraUiState
import com.esalinify.data.PredictionResult
import com.esalinify.util.HandDetector
import com.esalinify.util.ImageProcessor
import com.esalinify.util.SignLanguageClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var classifier: SignLanguageClassifier? = null
    private var handDetector: HandDetector? = null

    // Stability filter variables (matching Python implementation exactly)
    private val currentPredictionList = mutableListOf<String>()
    private var lastConfirmedLetter = ""

    companion object {
        private const val TAG = "CameraViewModel"
        private const val CONFIDENCE_THRESHOLD = 0.85f // Same as Python
        private const val STABILITY_FRAMES = 10 // Same as Python
    }

    /**
     * Initializes ML models (TensorFlow Lite and MediaPipe)
     */
    fun initializeModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "======================================")
                Log.i(TAG, "Initializing ML Models...")
                Log.i(TAG, "======================================")
                _uiState.update { it.copy(isProcessing = true) }

                Log.d(TAG, "Step 1/2: Creating SignLanguageClassifier...")
                classifier = SignLanguageClassifier(context)

                Log.d(TAG, "Step 2/2: Creating HandDetector...")
                handDetector = HandDetector(context)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = null
                    )
                }
                Log.i(TAG, "✓✓✓ ALL MODELS INITIALIZED SUCCESSFULLY ✓✓✓")
                Log.i(TAG, "Ready to process camera frames...")
            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL: Error initializing models", e)
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to initialize camera models: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Processes a camera frame for sign language recognition
     * Matches Python implementation:
     * 1. Detect hand → get bounding box
     * 2. Crop hand region with padding
     * 3. Preprocess: grayscale → resize to 28x28 → normalize
     * 4. Run TFLite inference
     * 5. Apply stability filter (85% confidence + 10 stable frames)
     */
    private var frameCount = 0

    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                frameCount++

                // Step 1: Detect hand
                val boundingBox = handDetector?.detectHand(bitmap, timestampMs)

                if (boundingBox != null) {
                    if (frameCount % 30 == 0) {
                        Log.d(TAG, "→ Hand detected! Frame #$frameCount, BBox: $boundingBox")
                    }

                    // Step 2: Crop hand region
                    val handRegion = ImageProcessor.cropHandRegion(bitmap, boundingBox)

                    // Step 3: Preprocess for model (grayscale + resize to 28x28)
                    val preprocessed = ImageProcessor.preprocessForModel(handRegion)

                    // Step 4: Run inference
                    val prediction = classifier?.classify(preprocessed)

                    // Clean up bitmaps
                    if (handRegion != bitmap) handRegion.recycle()
                    if (preprocessed != handRegion) preprocessed.recycle()

                    if (prediction != null) {
                        // Update UI with current prediction
                        val predictionWithBox = prediction.copy(boundingBox = boundingBox)
                        _uiState.update {
                            it.copy(currentPrediction = predictionWithBox)
                        }

                        // Step 5: Apply stability filter (matching Python exactly)
                        applyStabilityFilter(prediction)
                    }
                } else {
                    // No hand detected - clear current prediction
                    _uiState.update {
                        it.copy(currentPrediction = null)
                    }
                    // Reset prediction list when hand is not visible (matching Python)
                    if (currentPredictionList.isNotEmpty()) {
                        currentPredictionList.clear()
                        Log.v(TAG, "No hand - cleared prediction list")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing frame #$frameCount", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Applies stability filter to prevent false positives
     * Matches Python implementation exactly:
     * - Confidence > 85%
     * - Same letter predicted for 10 consecutive frames
     * - Letter is different from last confirmed letter
     */
    private fun applyStabilityFilter(prediction: PredictionResult) {
        if (prediction.confidence > CONFIDENCE_THRESHOLD) {
            // Add to prediction list
            currentPredictionList.add(prediction.predictedChar)

            // Keep only last STABILITY_FRAMES predictions (pop(0) in Python)
            if (currentPredictionList.size > STABILITY_FRAMES) {
                currentPredictionList.removeAt(0)
            }

            Log.v(TAG, "Stability: ${currentPredictionList.joinToString("")} (${currentPredictionList.size}/$STABILITY_FRAMES)")

            // Check if we have enough stable predictions
            if (currentPredictionList.size == STABILITY_FRAMES) {
                val uniquePredictions = currentPredictionList.toSet()

                // All predictions must be identical (len(set(...)) == 1 in Python)
                if (uniquePredictions.size == 1) {
                    val confirmedLetter = currentPredictionList[0]

                    // Add letter if different from last confirmed
                    if (confirmedLetter != lastConfirmedLetter) {
                        // Add to translated text
                        _uiState.update {
                            val newText = it.translatedText + confirmedLetter
                            Log.i(TAG, "✓✓✓ LETTER CONFIRMED: '$confirmedLetter' → Text: '$newText'")
                            it.copy(translatedText = newText)
                        }
                        lastConfirmedLetter = confirmedLetter
                    } else {
                        Log.v(TAG, "Same as last confirmed ('$confirmedLetter') - skipping")
                    }
                }
            }
        } else {
            // Low confidence - clear prediction list
            if (currentPredictionList.isNotEmpty()) {
                Log.v(TAG, "Low confidence (${(prediction.confidence * 100).toInt()}%) - clearing list")
                currentPredictionList.clear()
            }
        }
    }

    /**
     * Clears the translated text
     */
    fun clearText() {
        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Clear button pressed - current text: '${_uiState.value.translatedText}'")
            _uiState.value = CameraUiState() // Reset to default state
            lastConfirmedLetter = ""
            currentPredictionList.clear()
            Log.d(TAG, "Translated text cleared - new text: '${_uiState.value.translatedText}'")
        }
    }

    /**
     * Deletes the last character from translated text
     */
    fun deleteLastLetter() {
        viewModelScope.launch(Dispatchers.Main) {
            val currentText = _uiState.value.translatedText
            Log.d(TAG, "Delete button pressed - current text: '$currentText'")

            if (currentText.isNotEmpty()) {
                val newText = currentText.dropLast(1)
                _uiState.value = _uiState.value.copy(translatedText = newText)
                Log.d(TAG, "Deleted last letter - new text: '$newText'")
            }

            // Reset last confirmed letter to allow re-adding
            lastConfirmedLetter = ""
            currentPredictionList.clear()
        }
    }

    /**
     * Releases ML model resources
     */
    override fun onCleared() {
        super.onCleared()
        classifier?.close()
        handDetector?.close()
        Log.d(TAG, "ViewModel cleared, models disposed")
    }
}
