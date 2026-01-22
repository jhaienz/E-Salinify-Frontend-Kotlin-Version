package com.esalinify.ui.screens.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esalinify.data.CameraUiState
import com.esalinify.data.PredictionResult
import com.esalinify.util.HandDetector
import com.esalinify.util.LandmarkClassifier
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

    private var landmarkClassifier: LandmarkClassifier? = null
    private var handDetector: HandDetector? = null

    // Stability filter for letter recognition
    private val recentPredictions = mutableListOf<String>()
    private var lastConfirmedLetter = ""
    private var lastLetterTime = 0L

    companion object {
        private const val TAG = "CameraViewModel"
        private const val CONFIDENCE_THRESHOLD = 0.75f
        private const val STABILITY_FRAMES = 8
        private const val SAME_LETTER_COOLDOWN_MS = 1500L // Cooldown before same letter can be added again
        private const val WORD_SEPARATOR_DELAY_MS = 3000L // 3 seconds no detection = add space
    }

    fun initializeModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "======================================")
                Log.i(TAG, "Initializing ASL Letter Recognition...")
                Log.i(TAG, "======================================")
                _uiState.update { it.copy(isProcessing = true) }

                Log.d(TAG, "Step 1/2: Creating HandDetector...")
                handDetector = HandDetector(context)

                Log.d(TAG, "Step 2/2: Creating LandmarkClassifier (A-Z)...")
                landmarkClassifier = LandmarkClassifier(context)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = null
                    )
                }
                Log.i(TAG, "✓✓✓ MODELS INITIALIZED - Ready to recognize A-Z ✓✓✓")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing models", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to initialize: ${e.message}"
                    )
                }
            }
        }
    }

    private var frameCount = 0
    private var lastHandDetectedTime = 0L

    /**
     * Processes a camera frame using landmark-based ASL recognition
     */
    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                frameCount++
                val currentTime = System.currentTimeMillis()

                // Detect hand and get landmarks
                val handResult = handDetector?.detectHandWithLandmarks(bitmap, timestampMs)

                if (handResult != null) {
                    lastHandDetectedTime = currentTime

                    // Classify landmarks
                    val prediction = landmarkClassifier?.classify(handResult.normalizedLandmarks)

                    if (prediction != null) {
                        // Update UI with current prediction
                        _uiState.update {
                            it.copy(
                                currentPrediction = prediction.copy(boundingBox = handResult.boundingBox)
                            )
                        }

                        // Apply stability filter
                        if (prediction.confidence > CONFIDENCE_THRESHOLD) {
                            recentPredictions.add(prediction.predictedChar)

                            if (recentPredictions.size > STABILITY_FRAMES) {
                                recentPredictions.removeAt(0)
                            }

                            // Check for stable prediction
                            if (recentPredictions.size == STABILITY_FRAMES &&
                                recentPredictions.toSet().size == 1
                            ) {
                                val confirmedLetter = recentPredictions[0]
                                val timeSinceLastLetter = currentTime - lastLetterTime
                                val isDifferent = confirmedLetter != lastConfirmedLetter
                                val cooldownPassed = timeSinceLastLetter > SAME_LETTER_COOLDOWN_MS

                                if (isDifferent || cooldownPassed) {
                                    addLetterToText(confirmedLetter)
                                    lastConfirmedLetter = confirmedLetter
                                    lastLetterTime = currentTime
                                    Log.i(TAG, "✓✓✓ LETTER CONFIRMED: '$confirmedLetter'")
                                }
                            }
                        } else {
                            recentPredictions.clear()
                        }
                    }
                } else {
                    // No hand detected
                    _uiState.update { it.copy(currentPrediction = null) }
                    recentPredictions.clear()

                    // Check if we should add a space (word separator)
                    val timeSinceLastHand = currentTime - lastHandDetectedTime
                    if (lastHandDetectedTime > 0 &&
                        timeSinceLastHand > WORD_SEPARATOR_DELAY_MS &&
                        _uiState.value.translatedText.isNotEmpty() &&
                        !_uiState.value.translatedText.endsWith(" ")
                    ) {
                        addSpaceToText()
                        lastHandDetectedTime = 0L // Reset to prevent multiple spaces
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            }
        }
    }

    private fun addLetterToText(letter: String) {
        _uiState.update {
            val newText = it.translatedText + letter
            Log.d(TAG, "Text: '$newText'")
            it.copy(translatedText = newText)
        }
    }

    private fun addSpaceToText() {
        _uiState.update {
            val newText = it.translatedText + " "
            Log.d(TAG, "Added space - Text: '$newText'")
            it.copy(translatedText = newText)
        }
        lastConfirmedLetter = ""
    }

    fun clearText() {
        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Clear button pressed")
            _uiState.value = CameraUiState()
            lastConfirmedLetter = ""
            lastLetterTime = 0L
            lastHandDetectedTime = 0L
            recentPredictions.clear()
        }
    }

    fun deleteLastLetter() {
        viewModelScope.launch(Dispatchers.Main) {
            val currentText = _uiState.value.translatedText
            Log.d(TAG, "Delete button pressed - current: '$currentText'")

            if (currentText.isNotEmpty()) {
                val newText = currentText.dropLast(1)
                _uiState.value = _uiState.value.copy(translatedText = newText)
                Log.d(TAG, "Deleted last char - new: '$newText'")
            }

            lastConfirmedLetter = ""
            recentPredictions.clear()
        }
    }

    override fun onCleared() {
        super.onCleared()
        landmarkClassifier?.close()
        handDetector?.close()
        Log.d(TAG, "ViewModel cleared")
    }
}
