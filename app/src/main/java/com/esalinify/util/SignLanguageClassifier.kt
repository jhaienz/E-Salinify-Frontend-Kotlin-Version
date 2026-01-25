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

class SignLanguageClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val lock = Any()

    // Kaggle model's output classes (250 ASL phrases)
    // Labels are ordered by index (0-249) as per the model's training
    private val labels = listOf(
        "tv", "after", "airplane", "all", "alligator", "animal", "another", "any", "apple", "arm",
        "aunt", "awake", "backyard", "bad", "balloon", "bath", "because", "bed", "bedroom", "bee",
        "before", "beside", "better", "bird", "black", "blow", "blue", "boat", "book", "boy",
        "brother", "brown", "bug", "bye", "callonphone", "can", "car", "carrot", "cat", "cereal",
        "chair", "cheek", "child", "chin", "chocolate", "clean", "close", "closet", "cloud", "clown",
        "cow", "cowboy", "cry", "cut", "cute", "dad", "dance", "dirty", "dog", "doll",
        "donkey", "down", "drawer", "drink", "drop", "dry", "dryer", "duck", "ear", "elephant",
        "empty", "every", "eye", "face", "fall", "farm", "fast", "feet", "find", "fine",
        "finger", "finish", "fireman", "first", "fish", "flag", "flower", "food", "for", "frenchfries",
        "frog", "garbage", "gift", "giraffe", "girl", "give", "glasswindow", "go", "goose", "grandma",
        "grandpa", "grass", "green", "gum", "hair", "happy", "hat", "hate", "have", "haveto",
        "head", "hear", "helicopter", "hello", "hen", "hesheit", "hide", "high", "home", "horse",
        "hot", "hungry", "icecream", "if", "into", "jacket", "jeans", "jump", "kiss", "kitty",
        "lamp", "later", "like", "lion", "lips", "listen", "look", "loud", "mad", "make",
        "man", "many", "milk", "minemy", "mitten", "mom", "moon", "morning", "mouse", "mouth",
        "nap", "napkin", "night", "no", "noisy", "nose", "not", "now", "nuts", "old",
        "on", "open", "orange", "outside", "owie", "owl", "pajamas", "pen", "pencil", "penny",
        "person", "pig", "pizza", "please", "police", "pool", "potty", "pretend", "pretty", "puppy",
        "puzzle", "quiet", "radio", "rain", "read", "red", "refrigerator", "ride", "room", "sad",
        "same", "say", "scissors", "see", "shhh", "shirt", "shoe", "shower", "sick", "sleep",
        "sleepy", "smile", "snack", "snow", "stairs", "stay", "sticky", "store", "story", "stuck",
        "sun", "table", "talk", "taste", "thankyou", "that", "there", "think", "thirsty", "tiger",
        "time", "tomorrow", "tongue", "tooth", "toothbrush", "touch", "toy", "tree", "uncle", "underwear",
        "up", "vacuum", "wait", "wake", "water", "wet", "weus", "where", "white", "who",
        "why", "will", "wolf", "yellow", "yes", "yesterday", "yourself", "yucky", "zebra", "zipper"
    )

    companion object {
        private const val TAG = "SignLanguageClassifier"
        private const val MODEL_PATH = "phrase_model.tflite" // Kaggle phrase model

        // Kaggle model expects MediaPipe Holistic: 543 landmarks × 3 coordinates
        private const val NUM_LANDMARKS = 543 // 468 face + 33 pose + 21 left hand + 21 right hand
        private const val NUM_COORDINATES = 3 // x, y, z
        private const val INPUT_SIZE = NUM_LANDMARKS * NUM_COORDINATES // 1629 features
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
     * Classifies holistic landmarks (face + pose + hands) to recognize ASL phrase
     * @param landmarks List of 543 landmark coordinates [[x1,y1,z1], [x2,y2,z2], ...]
     * @return PredictionResult with phrase and confidence
     */
    fun classify(landmarks: List<FloatArray>): PredictionResult? = synchronized(lock) {
        if (interpreter == null) {
            Log.e(TAG, "❌ Interpreter not initialized - cannot classify")
            return null
        }

        if (landmarks.size != NUM_LANDMARKS) {
            Log.e(TAG, "Invalid landmarks size: ${landmarks.size}, expected $NUM_LANDMARKS")
            return null
        }

        try {
            // Get actual model output size
            val outputTensor = interpreter?.getOutputTensor(0)
            val modelOutputSize = outputTensor?.shape()?.get(1) ?: numClasses

            // Create input buffer - Kaggle model expects raw normalized landmarks [543, 3]
            // Format: [[x1,y1,z1], [x2,y2,z2], ..., [x543,y543,z543]]
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
            inputBuffer.order(ByteOrder.nativeOrder())

            // Flatten landmarks into input buffer
            for (landmark in landmarks) {
                if (landmark.size >= NUM_COORDINATES) {
                    inputBuffer.putFloat(landmark[0]) // x
                    inputBuffer.putFloat(landmark[1]) // y
                    inputBuffer.putFloat(landmark[2]) // z
                } else {
                    // Fallback for malformed landmarks
                    inputBuffer.putFloat(0f)
                    inputBuffer.putFloat(0f)
                    inputBuffer.putFloat(0f)
                }
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
