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

    // 24 letters - excluding J and Z (motion-based signs)
    private val labels = listOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y'
    )

    companion object {
        private const val TAG = "SignLanguageClassifier"
        private const val MODEL_PATH = "model.tflite"
        private const val INPUT_SIZE = 28
        private const val NUM_CLASSES = 24
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
            Log.d(TAG, "  Labels: 24 letters (A-Y, excluding J and Z)")
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
     * Classifies a preprocessed bitmap (28x28 grayscale)
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

            // Convert bitmap to normalized float array
            val inputArray = ImageProcessor.bitmapToNormalizedFloatArray(bitmap)
            Log.v(TAG, "Input array size: ${inputArray.size}, first 5 values: ${inputArray.take(5)}")

            // Reshape to [1, 28, 28, 1] for model input
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 1)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (value in inputArray) {
                inputBuffer.putFloat(value)
            }

            // CRITICAL: Rewind buffer to beginning before inference
            inputBuffer.rewind()

            // Prepare output array [1, 24]
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
