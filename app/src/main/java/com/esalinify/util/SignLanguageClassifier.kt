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
    private val lock = Any() // Synchronization lock for thread safety

    // 36 classes - 10 digits (0-9) + 26 letters (A-Z)
    private val labels = listOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    companion object {
        private const val TAG = "SignLanguageClassifier"
        private const val MODEL_PATH = "hand_sign_model.tflite"
        private const val INPUT_SIZE = 64
        private const val NUM_CHANNELS = 3
        private const val NUM_CLASSES = 36
    }

    init {
        try {
            Log.d(TAG, "Loading TensorFlow Lite model from assets/$MODEL_PATH...")
            val modelFile = loadModelFile()
            Log.d(TAG, "Model file size: ${modelFile.capacity()} bytes")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // Enable GPU acceleration if available
                // addDelegate(GpuDelegate())
            }
            interpreter = Interpreter(modelFile, options)

            // Log input/output tensor info
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            Log.i(TAG, "✓ TensorFlow Lite model loaded successfully")
            Log.d(TAG, "  Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Output shape: ${outputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "  Labels: 36 classes (0-9 + A-Z)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Error loading TensorFlow Lite model", e)
            e.printStackTrace()
        }
    }

    /**
     * Loads the TensorFlow Lite model from assets
     */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Classifies a preprocessed bitmap (64x64 RGB)
     * Returns prediction result with character and confidence
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

            // Convert bitmap to normalized RGB float array
            val inputArray = ImageProcessor.bitmapToNormalizedRgbFloatArray(bitmap)
            Log.v(TAG, "Input array size: ${inputArray.size}, first 5 values: ${inputArray.take(5)}")

            // Reshape to [1, 64, 64, 3] for model input
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (value in inputArray) {
                inputBuffer.putFloat(value)
            }

            // CRITICAL: Rewind buffer to beginning before inference
            inputBuffer.rewind()

            // Prepare output array [1, 36]
            val outputArray = Array(1) { FloatArray(NUM_CLASSES) }

            // Run inference (synchronized to prevent concurrent access)
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
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ TFLite state error - interpreter may be busy or closed", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "❌ TFLite input error - invalid buffer or tensor shape", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error during classification: ${e.javaClass.simpleName}", e)
            e.printStackTrace()
            return null
        }
    }

    /**
     * Releases resources
     */
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
