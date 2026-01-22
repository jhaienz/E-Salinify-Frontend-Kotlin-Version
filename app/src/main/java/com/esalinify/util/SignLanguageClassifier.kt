package com.esalinify.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.esalinify.data.PredictionResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SignLanguageClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val lock = Any()

    // Common greeting phrases for ASL recognition
    // Update these labels to match your trained model's output classes
    private val labels = listOf(
        "Hello",
        "Good Morning",
        "Good Afternoon",
        "Good Evening",
        "Goodbye",
        "Thank You",
        "Sorry",
        "Please",
        "Yes",
        "No"
    )

    companion object {
        private const val TAG = "SignLanguageClassifier"
        private const val MODEL_PATH = "model.tflite" // Replace with your phrase model
        private const val INPUT_SIZE = 28 // Adjust based on your model
        private const val NUM_CHANNELS = 1 // Grayscale (change to 3 for RGB)
    }

    // Dynamically set based on labels
    private val numClasses = labels.size

    init {
        try {
            Log.d(TAG, "Loading TensorFlow Lite model from assets/$MODEL_PATH...")
            val modelFile = loadModelFile()
            Log.d(TAG, "Model file size: ${modelFile.capacity()} bytes")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelFile, options)

            // Log input/output tensor info
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            Log.i(TAG, "✓ TensorFlow Lite model loaded successfully")
            Log.d(TAG, "  Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Output shape: ${outputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Labels ($numClasses phrases): ${labels.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Error loading TensorFlow Lite model", e)
            e.printStackTrace()
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
     * Classifies a preprocessed bitmap
     * Returns the recognized phrase and confidence
     */
    fun classify(bitmap: Bitmap): PredictionResult? = synchronized(lock) {
        if (interpreter == null) {
            Log.e(TAG, "❌ Interpreter not initialized - cannot classify")
            return null
        }

        try {
            // Get actual model output size
            val outputTensor = interpreter?.getOutputTensor(0)
            val modelOutputSize = outputTensor?.shape()?.get(1) ?: numClasses

            // Preprocess bitmap
            val processedBitmap = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE) {
                Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            } else {
                bitmap
            }

            // Convert bitmap to normalized float array
            val inputArray = ImageProcessor.bitmapToNormalizedFloatArray(processedBitmap)

            // Create input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (value in inputArray) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            // Prepare output array with actual model output size
            val outputArray = Array(1) { FloatArray(modelOutputSize) }

            // Run inference
            interpreter?.run(inputBuffer, outputArray)

            // Get prediction
            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
            val confidence = predictions[maxIndex]

            // Map to label (handle case where model has more outputs than labels)
            val predictedPhrase = if (maxIndex < labels.size) {
                labels[maxIndex]
            } else {
                "Unknown ($maxIndex)"
            }

            // Log top 3 predictions for debugging
            val top3 = predictions.indices
                .sortedByDescending { predictions[it] }
                .take(3)
                .joinToString { idx ->
                    val label = if (idx < labels.size) labels[idx] else "[$idx]"
                    "$label:${(predictions[idx] * 100).toInt()}%"
                }
            Log.d(TAG, "Prediction: $predictedPhrase (${(confidence * 100).toInt()}%) | Top3: $top3")

            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }

            return PredictionResult(
                predictedChar = predictedPhrase,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during classification: ${e.javaClass.simpleName}", e)
            e.printStackTrace()
            return null
        }
    }

    fun close() = synchronized(lock) {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "TensorFlow Lite interpreter closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter", e)
        }
    }
}
