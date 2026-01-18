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

    // Stability filter variables (matching Python implementation)
    private val currentPredictionList = mutableListOf<String>()
    private var lastConfirmedLetter = ""

    // Word separation tracking
    private var lastLetterConfirmedTime = 0L
    private var currentWord = StringBuilder()

    companion object {
        private const val TAG = "CameraViewModel"
        private const val CONFIDENCE_THRESHOLD = 0.85f
        private const val STABILITY_FRAMES = 10
        private const val WORD_SEPARATOR_DELAY_MS = 3000L // 3 seconds
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
     * Matches the Python implementation logic:
     * 1. Detect hand → get bounding box
     * 2. Crop hand region with padding
     * 3. Preprocess: grayscale → resize to 28x28 → normalize
     * 4. Run TFLite inference
     * 5. Apply stability filter before adding to text
     *
     * @param bitmap The camera frame
     * @param timestampMs Frame timestamp in milliseconds
     */
    private var frameCount = 0

    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                frameCount++
                if (frameCount % 30 == 0) {  // Log every 30 frames (~1 second)
                    Log.d(TAG, "Processing frame #$frameCount (${bitmap.width}x${bitmap.height}) @ ${timestampMs}ms")
                }

                // Step 1: Detect hand
                val boundingBox = handDetector?.detectHand(bitmap, timestampMs)

                if (boundingBox != null) {
                    Log.d(TAG, "→ Hand detected! Bounding box: $boundingBox")

                    // Step 2: Crop hand region
                    val handRegion = ImageProcessor.cropHandRegion(bitmap, boundingBox)
                    Log.v(TAG, "  Cropped region: ${handRegion.width}x${handRegion.height}")

                    // Step 3: Preprocess for model (grayscale + resize to 28x28)
                    val preprocessed = ImageProcessor.preprocessForModel(handRegion)
                    Log.v(TAG, "  Preprocessed: ${preprocessed.width}x${preprocessed.height}")

                    // Step 4: Run inference
                    val prediction = classifier?.classify(preprocessed)

                    // Clean up bitmaps
                    if (handRegion != bitmap) handRegion.recycle()
                    if (preprocessed != handRegion) preprocessed.recycle()

                    if (prediction != null) {
                        Log.d(TAG, "  Inference result: ${prediction.predictedChar} @ ${(prediction.confidence * 100).toInt()}%")

                        // Update bounding box in prediction
                        val predictionWithBox = prediction.copy(boundingBox = boundingBox)

                        // Step 5: Apply stability filter
                        applyStabilityFilter(predictionWithBox)

                        // Update UI with current prediction
                        _uiState.update {
                            it.copy(currentPrediction = predictionWithBox)
                        }
                    } else {
                        Log.w(TAG, "  ⚠ Classifier returned null")
                    }
                } else {
                    // No hand detected - clear current prediction
                    _uiState.update {
                        it.copy(currentPrediction = null)
                    }
                    // Reset prediction list when hand is not visible
                    if (currentPredictionList.isNotEmpty()) {
                        currentPredictionList.clear()
                        Log.v(TAG, "  No hand - cleared prediction list")
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
     * Only adds letter if:
     * - Confidence > 85%
     * - Same letter predicted for 10 consecutive frames
     * - Letter is different from last confirmed letter
     */
    private fun applyStabilityFilter(prediction: PredictionResult) {
        Log.v(TAG, "  Stability filter: ${prediction.predictedChar} @ ${(prediction.confidence * 100).toInt()}% (threshold: ${(CONFIDENCE_THRESHOLD * 100).toInt()}%)")

        val currentTime = System.currentTimeMillis()

        // Check if we should add a space (3 seconds of inactivity)
        if (lastLetterConfirmedTime > 0 &&
            currentTime - lastLetterConfirmedTime > WORD_SEPARATOR_DELAY_MS &&
            currentWord.isNotEmpty()) {

            // Add current word to translated text with space
            _uiState.update {
                val newText = if (it.translatedText.isEmpty()) {
                    currentWord.toString()
                } else {
                    "${it.translatedText} ${currentWord}"
                }
                it.copy(
                    translatedText = newText,
                    currentWord = "" // Clear current word from UI
                )
            }
            Log.i(TAG, "    📝 WORD COMPLETED: '${currentWord}' (after ${(currentTime - lastLetterConfirmedTime)/1000}s)")
            Log.i(TAG, "    Full text: '${_uiState.value.translatedText}'")
            currentWord.clear()
            lastConfirmedLetter = "" // Reset to allow same letter to start new word
        }

        if (prediction.confidence > CONFIDENCE_THRESHOLD) {
            // Add to prediction list
            currentPredictionList.add(prediction.predictedChar)
            Log.v(TAG, "    Added to list. Current: ${currentPredictionList.joinToString("")} (${currentPredictionList.size}/$STABILITY_FRAMES)")

            // Keep only last STABILITY_FRAMES predictions
            if (currentPredictionList.size > STABILITY_FRAMES) {
                currentPredictionList.removeAt(0)
            }

            // Check if we have enough stable predictions
            if (currentPredictionList.size == STABILITY_FRAMES) {
                val uniquePredictions = currentPredictionList.toSet()
                Log.d(TAG, "    Full buffer! Unique predictions: $uniquePredictions")

                // All predictions must be identical
                if (uniquePredictions.size == 1) {
                    val confirmedLetter = currentPredictionList[0]

                    // Add letter if different from last confirmed
                    if (confirmedLetter != lastConfirmedLetter) {
                        currentWord.append(confirmedLetter)
                        lastConfirmedLetter = confirmedLetter
                        lastLetterConfirmedTime = currentTime

                        // Update UI with current word
                        _uiState.update {
                            it.copy(currentWord = currentWord.toString())
                        }

                        Log.i(TAG, "    ✓✓✓ LETTER CONFIRMED: '$confirmedLetter' ✓✓✓")
                        Log.i(TAG, "    Current word buffer: '${currentWord}'")
                    } else {
                        Log.d(TAG, "    Same as last confirmed ('$confirmedLetter') - skipping")
                    }
                } else {
                    Log.v(TAG, "    Predictions not stable (${uniquePredictions.size} different)")
                }
            }
        } else {
            // Low confidence - clear prediction list
            if (currentPredictionList.isNotEmpty()) {
                Log.v(TAG, "    Low confidence - clearing ${currentPredictionList.size} predictions")
                currentPredictionList.clear()
            }
        }
    }

    /**
     * Clears the translated text
     */
    fun clearText() {
        _uiState.update { it.copy(translatedText = "") }
        lastConfirmedLetter = ""
        currentPredictionList.clear()
        currentWord.clear()
        lastLetterConfirmedTime = 0L
        Log.d(TAG, "Translated text and word buffer cleared")
    }

    /**
     * Deletes the last character from translated text or current word buffer
     */
    fun deleteLastLetter() {
        // First try to delete from current word buffer
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
            lastConfirmedLetter = if (currentWord.isNotEmpty()) {
                currentWord.last().toString()
            } else {
                ""
            }
            Log.d(TAG, "Deleted from word buffer. Current word: '${currentWord}'")
        } else {
            // If word buffer is empty, delete from translated text
            _uiState.update {
                val newText = if (it.translatedText.isNotEmpty()) {
                    it.translatedText.dropLast(1)
                } else {
                    it.translatedText
                }
                it.copy(translatedText = newText)
            }
            Log.d(TAG, "Deleted from translated text")
        }

        // Reset last confirmed letter to allow re-adding
        currentPredictionList.clear()
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
