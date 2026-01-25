package com.esalinify.ui.screens.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esalinify.data.CameraUiState
import com.esalinify.data.PredictionResult
import com.esalinify.data.RecognitionMode
import com.esalinify.util.HandDetector
import com.esalinify.util.HolisticDetector
import com.esalinify.util.LandmarkClassifier
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

    private var landmarkClassifier: LandmarkClassifier? = null
    private var phraseClassifier: SignLanguageClassifier? = null
    private var handDetector: HandDetector? = null
    private var holisticDetector: HolisticDetector? = null

    // Stability filter for letter recognition
    private val recentPredictions = mutableListOf<String>()
    private var lastConfirmedLetter = ""
    private var lastLetterTime = 0L

    // Time-based stability tracking for letters
    private var currentStableLetter = ""
    private var stableLetterStartTime = 0L

    companion object {
        private const val TAG = "CameraViewModel"
        private const val CONFIDENCE_THRESHOLD = 0.55f // Lowered for faster detection
        private const val PHRASE_CONFIDENCE_THRESHOLD = 0.5f // Lower threshold for phrases
        private const val LETTER_STABILITY_TIME_MS = 500L // 0.5 seconds of stable detection required
        private const val STABILITY_FRAMES = 8 // For phrase mode
        private const val SAME_LETTER_COOLDOWN_MS = 800L // Cooldown before same letter can be added again
        private const val WORD_SEPARATOR_DELAY_MS = 3000L // 3 seconds no detection = add space
    }

    fun initializeModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "======================================")
                Log.i(TAG, "Initializing ASL Recognition Models...")
                Log.i(TAG, "======================================")
                _uiState.update { it.copy(isProcessing = true) }

                Log.d(TAG, "Step 1/4: Creating HandDetector (for letters)...")
                handDetector = HandDetector(context)

                Log.d(TAG, "Step 2/4: Creating HolisticDetector (for phrases)...")
                holisticDetector = HolisticDetector(context)

                Log.d(TAG, "Step 3/4: Creating LandmarkClassifier (A-Z)...")
                landmarkClassifier = LandmarkClassifier(context)

                Log.d(TAG, "Step 4/4: Creating PhraseClassifier (Phrases)...")
                phraseClassifier = SignLanguageClassifier(context)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = null
                    )
                }
                Log.i(TAG, "✓✓✓ MODELS INITIALIZED - Ready to recognize letters and phrases ✓✓✓")
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

    fun toggleRecognitionMode() {
        viewModelScope.launch(Dispatchers.Main) {
            val newMode = when (_uiState.value.recognitionMode) {
                RecognitionMode.LETTER -> RecognitionMode.PHRASE
                RecognitionMode.PHRASE -> RecognitionMode.LETTER
            }
            Log.i(TAG, "Switching mode to: $newMode")

            // Clear state and switch mode in one atomic update
            _uiState.update {
                it.copy(
                    recognitionMode = newMode,
                    translatedText = "",
                    currentPrediction = null
                )
            }

            lastConfirmedLetter = ""
            lastLetterTime = 0L
            lastHandDetectedTime = 0L
            currentStableLetter = ""
            stableLetterStartTime = 0L
            recentPredictions.clear()
        }
    }

    fun toggleCameraFacing() {
        viewModelScope.launch(Dispatchers.Main) {
            val newFacing = when (_uiState.value.cameraFacing) {
                com.esalinify.data.CameraFacing.BACK -> com.esalinify.data.CameraFacing.FRONT
                com.esalinify.data.CameraFacing.FRONT -> com.esalinify.data.CameraFacing.BACK
            }
            Log.i(TAG, "Switching camera to: $newFacing")
            _uiState.update { it.copy(cameraFacing = newFacing) }
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

                // Use different detector based on mode
                when (_uiState.value.recognitionMode) {
                    RecognitionMode.LETTER -> {
                        // Letter mode: Use HandDetector (21 landmarks)
                        val handResult = handDetector?.detectHandWithLandmarks(bitmap, timestampMs)

                        if (handResult != null) {
                            lastHandDetectedTime = currentTime

                            // Classify using letter classifier
                            val prediction = landmarkClassifier?.classify(handResult.normalizedLandmarks)

                            if (prediction != null) {
                                _uiState.update {
                                    it.copy(
                                        currentPrediction = prediction.copy(boundingBox = handResult.boundingBox)
                                    )
                                }
                                applyLetterStabilityFilter(prediction, currentTime)
                            }
                        } else {
                            // No hand detected
                            _uiState.update { it.copy(currentPrediction = null) }
                            if (currentStableLetter.isNotEmpty()) {
                                val lostAfter = currentTime - stableLetterStartTime
                                Log.w(TAG, "⚠ Lost stable tracking for '$currentStableLetter' after ${lostAfter}ms (no hand detected)")
                                currentStableLetter = ""
                                stableLetterStartTime = 0L
                            }
                            recentPredictions.clear()
                        }
                    }

                    RecognitionMode.PHRASE -> {
                        // Phrase mode: Use HolisticDetector (543 landmarks)
                        val holisticResult = holisticDetector?.detectHolistic(bitmap, timestampMs)

                        if (holisticResult != null) {
                            lastHandDetectedTime = currentTime
                            Log.d(TAG, "Holistic detection successful - landmarks: ${holisticResult.allLandmarks.size}")

                            // Classify using phrase classifier
                            val prediction = phraseClassifier?.classify(holisticResult.allLandmarks)

                            if (prediction != null) {
                                Log.d(TAG, "Phrase prediction: ${prediction.predictedChar} (${(prediction.confidence * 100).toInt()}%)")
                                _uiState.update {
                                    it.copy(
                                        currentPrediction = prediction.copy(boundingBox = holisticResult.boundingBox)
                                    )
                                }
                                applyPhraseDetection(prediction, currentTime)
                            } else {
                                Log.d(TAG, "No prediction from phrase classifier")
                            }
                        } else {
                            // No landmarks detected
                            if (frameCount % 30 == 0) {
                                Log.d(TAG, "No holistic landmarks detected - ensure face, body, and hands are visible")
                            }
                            _uiState.update { it.copy(currentPrediction = null) }
                            recentPredictions.clear()
                        }
                    }
                }

                // Check if we should add a space (word separator) - only in letter mode
                if (_uiState.value.recognitionMode == RecognitionMode.LETTER) {
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

    private fun applyLetterStabilityFilter(prediction: PredictionResult, currentTime: Long) {
        // Apply time-based stability filter for letters
        if (prediction.confidence > CONFIDENCE_THRESHOLD) {
            val predictedLetter = prediction.predictedChar

            // Check if this is a new letter or same as current stable letter
            if (predictedLetter != currentStableLetter) {
                // New letter detected, start tracking it
                currentStableLetter = predictedLetter
                stableLetterStartTime = currentTime
                Log.i(TAG, "▶ Started tracking letter: '$predictedLetter' (confidence: ${(prediction.confidence * 100).toInt()}%)")
            } else {
                // Same letter, check if it's been stable for required time
                val stableTime = currentTime - stableLetterStartTime

                if (stableTime >= LETTER_STABILITY_TIME_MS) {
                    // Letter has been stable for required time
                    val timeSinceLastLetter = currentTime - lastLetterTime
                    val isDifferent = currentStableLetter != lastConfirmedLetter
                    val cooldownPassed = timeSinceLastLetter > SAME_LETTER_COOLDOWN_MS

                    if (isDifferent || cooldownPassed) {
                        addLetterToText(currentStableLetter)
                        lastConfirmedLetter = currentStableLetter
                        lastLetterTime = currentTime
                        Log.i(TAG, "✓✓✓ LETTER CONFIRMED: '$currentStableLetter' (stable for ${stableTime}ms)")

                        // Reset stable tracking to avoid duplicate
                        currentStableLetter = ""
                        stableLetterStartTime = 0L
                    }
                } else {
                    // Still tracking, log progress periodically
                    if (stableTime < LETTER_STABILITY_TIME_MS) {
                        // Log every ~250ms for better visibility
                        val currentQuarter = (stableTime / 250).toInt()
                        val previousQuarter = ((stableTime - 50) / 250).toInt()

                        if (currentQuarter > previousQuarter) {
                            Log.i(TAG, "⏱ Letter '$predictedLetter' stable for ${stableTime}ms / ${LETTER_STABILITY_TIME_MS}ms")
                        }
                    }
                }
            }
        } else {
            // Low confidence, reset tracking
            if (currentStableLetter.isNotEmpty()) {
                val lostAfter = currentTime - stableLetterStartTime
                Log.w(TAG, "⚠ Lost stable tracking for '$currentStableLetter' after ${lostAfter}ms (confidence too low: ${(prediction.confidence * 100).toInt()}%)")
            }
            currentStableLetter = ""
            stableLetterStartTime = 0L
        }
    }

    private fun applyPhraseDetection(prediction: PredictionResult, currentTime: Long) {
        // Apply stability filter for phrases (using lower threshold)
        if (prediction.confidence > PHRASE_CONFIDENCE_THRESHOLD) {
            recentPredictions.add(prediction.predictedChar)

            if (recentPredictions.size > STABILITY_FRAMES) {
                recentPredictions.removeAt(0)
            }

            // Check for stable prediction
            if (recentPredictions.size == STABILITY_FRAMES &&
                recentPredictions.toSet().size == 1
            ) {
                val confirmedPhrase = recentPredictions[0]
                val timeSinceLastPhrase = currentTime - lastLetterTime
                val isDifferent = confirmedPhrase != lastConfirmedLetter
                val cooldownPassed = timeSinceLastPhrase > SAME_LETTER_COOLDOWN_MS

                if (isDifferent || cooldownPassed) {
                    addPhraseToText(confirmedPhrase)
                    lastConfirmedLetter = confirmedPhrase
                    lastLetterTime = currentTime
                    Log.i(TAG, "✓✓✓ PHRASE CONFIRMED: '$confirmedPhrase' (confidence: ${(prediction.confidence * 100).toInt()}%)")
                }
            }
        } else {
            if (recentPredictions.isNotEmpty()) {
                Log.d(TAG, "Phrase confidence too low: ${(prediction.confidence * 100).toInt()}%")
            }
            recentPredictions.clear()
        }
    }

    private fun addLetterToText(letter: String) {
        _uiState.update {
            val newText = it.translatedText + letter
            Log.i(TAG, "✅ Added letter '$letter' to text: '$newText'")
            it.copy(translatedText = newText)
        }
    }

    private fun addPhraseToText(phrase: String) {
        _uiState.update {
            val newText = if (it.translatedText.isEmpty()) {
                phrase
            } else {
                "${it.translatedText} $phrase"  // Add space before new phrase
            }
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
            // Preserve the current recognition mode when clearing
            _uiState.update {
                it.copy(
                    translatedText = "",
                    currentPrediction = null
                )
            }
            lastConfirmedLetter = ""
            lastLetterTime = 0L
            lastHandDetectedTime = 0L
            currentStableLetter = ""
            stableLetterStartTime = 0L
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
            currentStableLetter = ""
            stableLetterStartTime = 0L
            recentPredictions.clear()
        }
    }

    override fun onCleared() {
        super.onCleared()
        landmarkClassifier?.close()
        phraseClassifier?.close()
        handDetector?.close()
        holisticDetector?.close()
        Log.d(TAG, "ViewModel cleared")
    }
}
