package com.esalinify.data

import android.graphics.Rect

enum class RecognitionMode {
    LETTER,  // A-Z individual letters
    PHRASE   // Full sign phrases
}

enum class CameraFacing {
    BACK,
    FRONT
}

data class PredictionResult(
    val predictedChar: String,
    val confidence: Float,
    val boundingBox: Rect? = null
)

data class CameraUiState(
    val translatedText: String = "",
    val currentWord: String = "",
    val currentPrediction: PredictionResult? = null,
    val recognitionMode: RecognitionMode = RecognitionMode.LETTER,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)
