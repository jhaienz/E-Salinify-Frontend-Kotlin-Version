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

    // 24 ASL letters (no J and Z as they require motion)
    private val labels = listOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y'
    )

    companion object {
        private const val TAG = "SignLanguageClassifier"
        private const val MODEL_PATH = "model.tflite" // 28x28 grayscale model
        private const val INPUT_SIZE = 28
        private const val NUM_CHANNELS = 1 // Grayscale
        private const val NUM_CLASSES = 24
    }

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
            Log.d(TAG, "  Labels: 24 ASL letters (A-Y, excluding J and Z)")
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
     * Classifies a preprocessed bitmap (28x28 grayscale)
     * Matches Python: pixeldata = roi.reshape(1, 28, 28, 1) / 255.0
     */
    fun classify(bitmap: Bitmap): PredictionResult? = synchronized(lock) {
        if (interpreter == null) {
            Log.e(TAG, "❌ Interpreter not initialized - cannot classify")
            return null
        }

        try {
            // Verify bitmap size
            if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE) {
                Log.e(TAG, "❌ Invalid bitmap size: ${bitmap.width}x${bitmap.height}, expected ${INPUT_SIZE}x${INPUT_SIZE}")
                return null
            }

            // Convert bitmap to normalized grayscale float array (matching Python)
            val inputArray = ImageProcessor.bitmapToNormalizedFloatArray(bitmap)
            Log.v(TAG, "Input array size: ${inputArray.size}")

            // Create input buffer with shape [1, 28, 28, 1]
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (value in inputArray) {
                inputBuffer.putFloat(value)
            }

            inputBuffer.rewind()

            // Prepare output array [1, 24]
            val outputArray = Array(1) { FloatArray(NUM_CLASSES) }

            // Run inference
            interpreter?.run(inputBuffer, outputArray)

            // Get prediction
            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
            val confidence = predictions[maxIndex]
            val predictedChar = labels[maxIndex]

            Log.d(TAG, "✓ Prediction: $predictedChar (${(confidence * 100).toInt()}%) - Top 3: ${
                predictions.indices.sortedByDescending { predictions[it] }
                    .take(3)
                    .joinToString { "${labels[it]}:${(predictions[it] * 100).toInt()}%" }
            }")

            return PredictionResult(
                predictedChar = predictedChar.toString(),
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
