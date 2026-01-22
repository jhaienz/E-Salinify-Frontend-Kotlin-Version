package com.esalinify.util

import android.content.Context
import android.util.Log
import com.esalinify.data.PredictionResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Landmark-based ASL classifier
 * Uses 21 hand landmarks to recognize A-Z letters
 * Model from: https://github.com/AkramOM606/American-Sign-Language-Detection
 */
class LandmarkClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val lock = Any()

    // ASL Alphabet labels (A-Z)
    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
        "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
        "U", "V", "W", "X", "Y", "Z"
    )

    companion object {
        private const val TAG = "LandmarkClassifier"
        private const val MODEL_PATH = "keypoint_classifier.tflite"
        private const val NUM_LANDMARKS = 21
        private const val NUM_COORDINATES = 2 // x, y
        private const val INPUT_SIZE = NUM_LANDMARKS * NUM_COORDINATES // 42 features
    }

    init {
        try {
            Log.d(TAG, "Loading landmark classifier from assets/$MODEL_PATH...")
            val modelFile = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelFile, options)

            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            Log.i(TAG, "✓ Landmark classifier loaded successfully")
            Log.d(TAG, "  Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Output shape: ${outputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Labels: A-Z (26 classes)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading landmark classifier", e)
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Classifies hand landmarks to recognize ASL letter
     * @param landmarks List of normalized landmark coordinates [[x1,y1], [x2,y2], ...]
     * @return PredictionResult with letter and confidence
     */
    fun classify(landmarks: List<FloatArray>): PredictionResult? = synchronized(lock) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter not initialized")
            return null
        }

        if (landmarks.size != NUM_LANDMARKS) {
            Log.e(TAG, "Invalid landmarks size: ${landmarks.size}, expected $NUM_LANDMARKS")
            return null
        }

        try {
            // Preprocess landmarks (same as Python implementation)
            val processedLandmarks = preprocessLandmarks(landmarks)

            // Create input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (value in processedLandmarks) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            // Get output size from model
            val outputTensor = interpreter?.getOutputTensor(0)
            val outputSize = outputTensor?.shape()?.get(1) ?: labels.size

            // Prepare output array
            val outputArray = Array(1) { FloatArray(outputSize) }

            // Run inference
            interpreter?.run(inputBuffer, outputArray)

            // Get prediction
            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
            val confidence = predictions[maxIndex]

            val predictedLetter = if (maxIndex < labels.size) {
                labels[maxIndex]
            } else {
                "?"
            }

            // Log prediction
            val top3 = predictions.indices
                .sortedByDescending { predictions[it] }
                .take(3)
                .joinToString { idx ->
                    val label = if (idx < labels.size) labels[idx] else "[$idx]"
                    "$label:${(predictions[idx] * 100).toInt()}%"
                }
            Log.d(TAG, "Prediction: $predictedLetter (${(confidence * 100).toInt()}%) | Top3: $top3")

            return PredictionResult(
                predictedChar = predictedLetter,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification", e)
            return null
        }
    }

    /**
     * Preprocesses landmarks matching the Python implementation:
     * 1. Get base point (wrist - landmark 0)
     * 2. Convert to relative coordinates
     * 3. Flatten to 1D array
     * 4. Normalize by max absolute value
     */
    private fun preprocessLandmarks(landmarks: List<FloatArray>): FloatArray {
        // Step 1: Get base point (wrist)
        val baseX = landmarks[0][0]
        val baseY = landmarks[0][1]

        // Step 2: Convert to relative coordinates
        val relativeCoords = mutableListOf<Float>()
        for (landmark in landmarks) {
            relativeCoords.add(landmark[0] - baseX)
            relativeCoords.add(landmark[1] - baseY)
        }

        // Step 3: Find max absolute value for normalization
        val maxValue = relativeCoords.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        val normalizer = if (maxValue > 0.0001f) maxValue else 1f

        // Step 4: Normalize
        val result = FloatArray(INPUT_SIZE)
        for (i in relativeCoords.indices) {
            result[i] = relativeCoords[i] / normalizer
        }

        return result
    }

    fun getLabels(): List<String> = labels

    fun close() = synchronized(lock) {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "Landmark classifier closed")
    }
}
