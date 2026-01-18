package com.esalinify.data

import android.graphics.Rect

data class PredictionResult(
    val predictedChar: String,
    val confidence: Float,
    val boundingBox: Rect? = null
)

data class CameraUiState(
    val translatedText: String = "",
    val currentWord: String = "",
    val currentPrediction: PredictionResult? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)
